package com.bx.smpPlugin.events;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.PlayerStore;
import com.bx.smpPlugin.core.Reward;
import com.bx.smpPlugin.core.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * An event that rewards being active: every EVERY-MINUTES minutes online (not AFK, not in
 * spectator, alive) a player earns REWARD, up to DAILY-LIMIT times a day. Optionally, catching a
 * fish has a chance of an extra reward. The Summer event uses it, and so does every custom event
 * in events.yml.
 *
 * <p>One shared task counts minutes for everyone online, instead of a task per player. The minutes
 * towards the next reward are kept in memory; the number of rewards given today is kept in the
 * player store, so the daily limit holds across restarts and the event being switched off and on.
 */
final class PlaytimeEvent extends SeasonalEvent implements Listener {

    private final String fallbackName;
    private final String fallbackDescription;
    private final Material fallbackIcon;
    private final Map<UUID, Integer> minutes = new HashMap<>();

    PlaytimeEvent(SmpPlugin plugin, EventManager manager, String id, String configPath, String name,
                  String description, Material icon) {
        super(plugin, manager, id, configPath);
        this.fallbackName = name;
        this.fallbackDescription = description;
        this.fallbackIcon = icon;
    }

    @Override
    protected String defaultName() {
        return fallbackName;
    }

    @Override
    protected String defaultDescription() {
        return fallbackDescription;
    }

    @Override
    protected Material defaultIcon() {
        return fallbackIcon;
    }

    @Override
    protected void onStart() {
        listen(this);
        if (everyMinutes() > 0 && !reward("PLAYTIME-REWARD.REWARD").isEmpty()) {
            task(plugin.getSpigotScheduler().runGlobalTimer(this::countMinute, 1200L, 1200L));
        }
    }

    @Override
    protected void onStop() {
        minutes.clear();
    }

    private int everyMinutes() {
        return Math.max(0, config().getInt("PLAYTIME-REWARD.EVERY-MINUTES", 30));
    }

    private int dailyLimit() {
        return Math.max(0, config().getInt("PLAYTIME-REWARD.DAILY-LIMIT", 4));
    }

    @Override
    public List<String> progress(Player player) {
        int every = everyMinutes();
        if (every <= 0) {
            return List.of();
        }
        PlayerStore store = store();
        int given = store == null ? 0 : parse(store.get(player.getUniqueId(), storeKey("rewards." + today())));
        int counted = minutes.getOrDefault(player.getUniqueId(), 0);
        return List.of("&7Rewards today: &e" + given + "&7/&e" + dailyLimit(),
                "&7Next reward in: &e" + Math.max(0, every - counted) + " min");
    }

    private void countMinute() {
        PlayerStore store = store();
        if (store == null) {
            return;
        }
        int every = everyMinutes();
        int limit = dailyLimit();
        Reward reward = reward("PLAYTIME-REWARD.REWARD");
        String key = storeKey("rewards." + today());
        minutes.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isEligible(player) || !store.isLoaded(player.getUniqueId())) {
                continue;
            }
            int given = parse(store.get(player.getUniqueId(), key));
            if (given >= limit) {
                continue;
            }
            int counted = minutes.merge(player.getUniqueId(), 1, Integer::sum);
            if (counted < every) {
                continue;
            }
            minutes.put(player.getUniqueId(), 0);
            // Counted before the reward is given, so the daily limit can never be passed.
            store.set(player.getUniqueId(), key, Integer.toString(given + 1));
            reward.give(plugin, player);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7F, 1.0F);
            Text.send(player, "EVENTS.PLAYTIME-REWARD", "&e{event}: &7thanks for playing! &f{reward} &8({count}/{limit} today)",
                    "{event}", displayName(), "{reward}", String.join("&7, ", reward.describe(plugin)),
                    "{count}", Integer.toString(given + 1), "{limit}", Integer.toString(limit));
        }
    }

    /** Alive, not spectating, and not AFK: only active players earn playtime rewards. */
    private boolean isEligible(Player player) {
        if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        return plugin.getAfkManager() == null || !plugin.getAfkManager().isAfk(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        double chance = config().getDouble("FISHING.CHANCE-PERCENT", 0D);
        if (chance <= 0D || ThreadLocalRandom.current().nextDouble(100D) >= chance) {
            return;
        }
        WeightedRewards table = WeightedRewards.read(config().getConfigurationSection("FISHING.REWARDS"));
        WeightedRewards.Entry entry = table.roll(ThreadLocalRandom.current());
        if (entry == null) {
            return;
        }
        Player player = event.getPlayer();
        entry.reward().give(plugin, player);
        Text.send(player, "EVENTS.FISHING-BONUS", "&e{event} catch! &f{reward}",
                "{event}", displayName(), "{reward}", String.join("&7, ", entry.reward().describe(plugin)));
    }

    private static int parse(String raw) {
        try {
            return raw == null ? 0 : Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}
