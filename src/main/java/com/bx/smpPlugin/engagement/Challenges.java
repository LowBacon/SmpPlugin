package com.bx.smpPlugin.engagement;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.MenuFx;
import com.bx.smpPlugin.core.PlayerStore;
import com.bx.smpPlugin.core.Reward;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.halloween.CalendarRules;
import com.bx.smpPlugin.managers.FeatureManager;
import com.bx.smpPlugin.menus.BaseMenu;
import com.bx.smpPlugin.models.PlayerData;
import com.bx.smpPlugin.utils.ItemUtils;
import com.bx.smpPlugin.utils.NumberUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * /challenges: a few goals a day, the same for everyone (picked from the pool with the date as
 * the seed). Progress is the difference between a player's stats now and when they first played
 * that day, so nothing extra is counted per block or per kill. A short timer pays out finished
 * goals while players play.
 */
public final class Challenges implements CommandExecutor {

    public static final String PERMISSION = "smpplugin.challenges";
    private static final String SECTION = "challenges";

    public enum Type {
        BREAK_BLOCKS("Break blocks"), PLACE_BLOCKS("Place blocks"), KILL_MOBS("Kill mobs"),
        KILL_PLAYERS("Kill players"), PLAY_MINUTES("Play minutes"), EARN_MONEY("Earn money by selling");

        private final String label;

        Type(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        static Type parse(String raw) {
            try {
                return raw == null ? null : valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    public record Challenge(String key, String name, Material icon, Type type, long goal, Reward reward) {
    }

    /** One player's day: the stat values when the day started and which goals are paid out. */
    record DayState(long day, Map<Type, Long> baseline, Set<String> done, boolean bonusPaid) {

        static DayState parse(String stored) {
            if (stored == null || stored.isBlank()) {
                return null;
            }
            try {
                String[] parts = stored.split("\\|", -1);
                long day = Long.parseLong(parts[0]);
                Map<Type, Long> baseline = new EnumMap<>(Type.class);
                if (parts.length > 1 && !parts[1].isBlank()) {
                    for (String pair : parts[1].split(",")) {
                        String[] kv = pair.split("=");
                        Type type = Type.parse(kv[0]);
                        if (type != null && kv.length == 2) {
                            baseline.put(type, Long.parseLong(kv[1]));
                        }
                    }
                }
                Set<String> done = new LinkedHashSet<>();
                if (parts.length > 2 && !parts[2].isBlank()) {
                    Collections.addAll(done, parts[2].split(","));
                }
                boolean bonus = parts.length > 3 && "1".equals(parts[3]);
                return new DayState(day, baseline, done, bonus);
            } catch (RuntimeException ignored) {
                return null;
            }
        }

        String serialize() {
            StringBuilder base = new StringBuilder();
            for (Map.Entry<Type, Long> entry : baseline.entrySet()) {
                if (!base.isEmpty()) {
                    base.append(',');
                }
                base.append(entry.getKey().name()).append('=').append(entry.getValue());
            }
            return day + "|" + base + "|" + String.join(",", done) + "|" + (bonusPaid ? "1" : "0");
        }
    }

    private final SmpPlugin plugin;
    private final PlayerStore store;

    public Challenges(SmpPlugin plugin, PlayerStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    private FileConfiguration config() {
        FileConfiguration extras = plugin.getConfigManager().getExtras();
        return extras == null ? new YamlConfiguration() : extras;
    }

    private ZoneId zone() {
        return CalendarRules.zone(config().getString("CHALLENGES.TIME-ZONE", ""));
    }

    long today() {
        return LocalDate.now(zone()).toEpochDay();
    }

    long secondsUntilReset() {
        ZonedDateTime now = ZonedDateTime.now(zone());
        return ChronoUnit.SECONDS.between(now, now.toLocalDate().plusDays(1).atStartOfDay(zone()));
    }

    /** Every challenge in the pool, in a stable order. */
    List<Challenge> pool() {
        List<Challenge> out = new ArrayList<>();
        ConfigurationSection section = config().getConfigurationSection("CHALLENGES.POOL");
        if (section == null) {
            return out;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            Type type = entry == null ? null : Type.parse(entry.getString("TYPE"));
            if (type == null) {
                continue;
            }
            Material icon = Material.matchMaterial(entry.getString("ICON", "PAPER").toUpperCase(Locale.ROOT));
            out.add(new Challenge(key, entry.getString("NAME", key), icon == null || !icon.isItem() ? Material.PAPER : icon,
                    type, Math.max(1L, entry.getLong("GOAL", 1L)), Reward.read(entry.getConfigurationSection("REWARD"))));
        }
        out.sort((a, b) -> a.key().compareTo(b.key()));
        return out;
    }

    /** Today's challenges: the same for everyone, reshuffled every day. */
    public List<Challenge> todays() {
        return pick(pool(), today(), Math.max(1, config().getInt("CHALLENGES.PER-DAY", 3)));
    }

    static <T> List<T> pick(List<T> pool, long day, int count) {
        List<T> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, new Random(day * 31L + 7L));
        return shuffled.subList(0, Math.min(count, shuffled.size()));
    }

    static long metric(PlayerData data, Type type) {
        return switch (type) {
            case BREAK_BLOCKS -> data.getBlocksBroken();
            case PLACE_BLOCKS -> data.getBlocksPlaced();
            case KILL_MOBS -> data.getMobsKilled();
            case KILL_PLAYERS -> data.getKills();
            case PLAY_MINUTES -> data.getTotalPlaytimeSeconds() / 60L;
            case EARN_MONEY -> (long) data.getMoneyMade();
        };
    }

    /** Today's state for an online player, starting the day if needed. Null when data is not loaded. */
    DayState stateOf(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null || !store.isLoaded(uuid)) {
            return null;
        }
        long today = today();
        DayState state = DayState.parse(store.get(uuid, SECTION));
        if (state == null || state.day() != today) {
            Map<Type, Long> baseline = new EnumMap<>(Type.class);
            for (Type type : Type.values()) {
                baseline.put(type, metric(data, type));
            }
            state = new DayState(today, baseline, new LinkedHashSet<>(), false);
            store.set(uuid, SECTION, state.serialize());
        }
        return state;
    }

    /** How many of today's challenges an online player has finished, for placeholders. */
    public int doneToday(Player player) {
        DayState state = stateOf(player);
        return state == null ? 0 : (int) todays().stream().filter(c -> state.done().contains(c.key())).count();
    }

    public int perDay() {
        return todays().size();
    }

    long progress(Player player, DayState state, Challenge challenge) {
        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null) {
            return 0L;
        }
        long start = state.baseline().getOrDefault(challenge.type(), metric(data, challenge.type()));
        return Math.max(0L, metric(data, challenge.type()) - start);
    }

    /** Pays out finished challenges. Called by a timer and when the menu opens. */
    public void check(Player player) {
        if (!plugin.getFeatureManager().isEnabled(FeatureManager.Feature.CHALLENGES) || !PermissionUtils.has(player, PERMISSION)) {
            return;
        }
        DayState state = stateOf(player);
        if (state == null) {
            return;
        }
        List<Challenge> today = todays();
        boolean changed = false;
        for (Challenge challenge : today) {
            if (state.done().contains(challenge.key()) || progress(player, state, challenge) < challenge.goal()) {
                continue;
            }
            state.done().add(challenge.key());
            changed = true;
            challenge.reward().give(plugin, player);
            Text.title(player, Text.get("CHALLENGES.DONE-TITLE", "&b&lChallenge complete!"), "&f" + challenge.name(), 5, 40, 10);
            Text.send(player, "CHALLENGES.DONE", "&bChallenge complete: &f{name}&b! &7{prize}",
                    "{name}", challenge.name(), "{prize}", String.join("&7, ", challenge.reward().describe(plugin)));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1.2F);
        }
        boolean bonus = state.bonusPaid();
        if (!bonus && !today.isEmpty() && today.stream().allMatch(c -> state.done().contains(c.key()))) {
            bonus = true;
            changed = true;
            Reward allDone = Reward.read(config().getConfigurationSection("CHALLENGES.ALL-DONE-BONUS"));
            allDone.give(plugin, player);
            Text.send(player, "CHALLENGES.ALL-DONE", "&b&lAll challenges done for today! &7Bonus: {prize}",
                    "{prize}", String.join("&7, ", allDone.describe(plugin)));
        }
        if (changed) {
            store.set(player.getUniqueId(), SECTION,
                    new DayState(state.day(), state.baseline(), state.done(), bonus).serialize());
        }
    }

    /** Runs the payout check for everyone online, each on their own thread. */
    public void checkAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.getSpigotScheduler().runEntity(player, () -> {
                if (player.isOnline()) {
                    check(player);
                }
            });
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "GENERAL.PLAYERS-ONLY", "&cOnly players can do that.");
            return true;
        }
        if (!PermissionUtils.has(player, PERMISSION)) {
            Text.send(player, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return true;
        }
        if (stateOf(player) == null) {
            Text.send(player, "GENERAL.LOADING", "&7Your data is still loading. Try again in a moment.");
            return true;
        }
        check(player);
        new ChallengesMenu().open(player);
        return true;
    }

    final class ChallengesMenu extends BaseMenu {

        private static final int[] SLOTS = {11, 13, 15, 12, 14, 10, 16};

        ChallengesMenu() {
            super(Challenges.this.plugin, Text.get("CHALLENGES.TITLE", "&8✦ &b&lDaily Challenges"), 27);
        }

        @Override
        public void open(Player player) {
            build(player);
            player.openInventory(inventory);
            MenuFx.sound(player, Sound.ITEM_BOOK_PAGE_TURN, 0.7F, 1.0F);
        }

        @Override
        public void build(Player player) {
            clear();
            fill(Material.BLACK_STAINED_GLASS_PANE);
            DayState state = stateOf(player);
            List<Challenge> today = todays();
            int finished = 0;
            for (int i = 0; i < today.size() && i < SLOTS.length; i++) {
                Challenge challenge = today.get(i);
                boolean done = state != null && state.done().contains(challenge.key());
                long progress = state == null ? 0L : Math.min(challenge.goal(), progress(player, state, challenge));
                if (done) {
                    finished++;
                }
                List<String> lore = new ArrayList<>();
                lore.add("&7" + challenge.type().label() + ": &f" + NumberUtils.format(challenge.goal()));
                lore.add("");
                lore.add(Text.bar(progress / (double) challenge.goal(), 20, "&b", "&8")
                        + " &f" + NumberUtils.format(progress) + "&7/&f" + NumberUtils.format(challenge.goal()));
                lore.add("");
                lore.add("&7Reward:");
                for (String line : challenge.reward().describe(plugin)) {
                    lore.add("&8• " + line);
                }
                if (done) {
                    lore.add("");
                    lore.add("&a✔ Complete");
                }
                ItemStack item = ItemUtils.createItem(challenge.icon(), (done ? "&a&l✔ " : "&b&l") + challenge.name(), lore);
                set(SLOTS[i], ItemUtils.hideMenuNoise(done ? ItemUtils.setGlint(item, true) : item, false));
            }
            List<String> info = new ArrayList<>();
            info.add("&7Finished today: &b" + finished + "&7/&b" + today.size());
            info.add("&7New challenges in &f" + Text.duration(secondsUntilReset()));
            info.add("");
            info.add("&7Finish all of them for a bonus:");
            for (String line : Reward.read(config().getConfigurationSection("CHALLENGES.ALL-DONE-BONUS")).describe(plugin)) {
                info.add("&8• " + line);
            }
            set(4, ItemUtils.createItem(Material.TARGET, "&b&lDaily Challenges", info));
            set(22, ItemUtils.createItem(Material.BARRIER, "&cClose"));
        }

        @Override
        public void handleClick(int slot, Player player, ClickType clickType) {
            if (slot == 22) {
                player.closeInventory();
            }
        }
    }
}
