package com.bx.smpPlugin.events;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.PlayerStore;
import com.bx.smpPlugin.core.Reward;
import com.bx.smpPlugin.core.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * The Easter egg hunt. Staff hide eggs (any block) with /easter addegg; while the event runs,
 * players right-click an egg to collect it once per season, earn the egg reward, and bonuses at
 * the milestones in events.yml. Eggs a player has not found yet sparkle when they are near.
 *
 * <p>Egg places are kept in {@code data/easter-eggs.yml}; which eggs each player found is kept in
 * the player store as {@code event.easter.<year>}, so progress survives restarts and the event
 * being switched off and on.
 */
final class EasterEvent extends SeasonalEvent implements Listener {

    /** One hidden egg. */
    record Egg(String id, String world, int x, int y, int z) {

        boolean isAt(Block block) {
            return block.getX() == x && block.getY() == y && block.getZ() == z
                    && block.getWorld().getName().equals(world);
        }

        Location centre(World loaded) {
            return new Location(loaded, x + 0.5D, y + 0.5D, z + 0.5D);
        }
    }

    private static final String EGG_FILE = "data/easter-eggs.yml";

    private final Map<String, Egg> eggs = new LinkedHashMap<>();

    EasterEvent(SmpPlugin plugin, EventManager manager) {
        super(plugin, manager, "easter");
        loadEggs();
    }

    @Override
    protected String defaultName() {
        return "&dEaster Egg Hunt";
    }

    @Override
    protected String defaultDescription() {
        return "Find the eggs hidden around the server. Right-click an egg to collect it.";
    }

    @Override
    protected Material defaultIcon() {
        return Material.EGG;
    }

    @Override
    protected void onStart() {
        listen(this);
        if (config().getBoolean("EGGS.SPARKLE", true)) {
            task(plugin.getSpigotScheduler().runGlobalTimer(this::sparkle, 40L, 40L));
        }
    }

    @Override
    protected void onReload() {
        loadEggs();
    }

    Collection<Egg> eggs() {
        return eggs.values();
    }

    private String foundKey() {
        return storeKey(Integer.toString(seasonYear()));
    }

    /** Ids of the eggs a loaded player has found this season. */
    Set<String> found(UUID player) {
        PlayerStore store = store();
        String raw = store == null ? null : store.get(player, foundKey());
        Set<String> ids = new LinkedHashSet<>();
        if (raw != null && !raw.isBlank()) {
            ids.addAll(Arrays.asList(raw.split(",")));
        }
        return ids;
    }

    @Override
    public void open(Player player) {
        plugin.getSpigotScheduler().dispatchPlayerCommand(player, "easter");
    }

    @Override
    public List<String> progress(Player player) {
        return List.of("&7Eggs found: &d" + found(player.getUniqueId()).size() + "&7/&d" + eggs.size());
    }

    // ------------------------------------------------------------------ collecting

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null) {
            return;
        }
        Block block = event.getClickedBlock();
        Egg egg = null;
        for (Egg candidate : eggs.values()) {
            if (candidate.isAt(block)) {
                egg = candidate;
                break;
            }
        }
        if (egg == null) {
            return;
        }
        // An egg hidden in a chest or behind a door is collected, not opened.
        event.setCancelled(true);
        Player player = event.getPlayer();
        PlayerStore store = store();
        if (store == null || !store.isLoaded(player.getUniqueId())) {
            return;
        }
        Set<String> found = found(player.getUniqueId());
        if (found.contains(egg.id())) {
            Text.actionBar(player, config().getString("EGGS.ALREADY-FOUND", "&7You already found this egg."));
            return;
        }
        found.add(egg.id());
        // Recorded before the reward is handed out, so clicking twice cannot pay twice.
        store.set(player.getUniqueId(), foundKey(), String.join(",", found));
        reward("EGG-REWARD").give(plugin, player);
        player.playSound(player.getLocation(), Sound.ENTITY_CHICKEN_EGG, 1.0F, 1.2F);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, egg.centre(block.getWorld()), 20, 0.4, 0.4, 0.4, 0.0);
        Text.send(player, "EVENTS.EGG-FOUND", "&dYou found an egg! &7({count}/{total})",
                "{count}", Integer.toString(found.size()), "{total}", Integer.toString(eggs.size()));
        milestone(player, found.size());
    }

    private void milestone(Player player, int count) {
        ConfigurationSection milestones = config().getConfigurationSection("MILESTONES");
        if (milestones == null) {
            return;
        }
        for (String key : milestones.getKeys(false)) {
            boolean all = key.equalsIgnoreCase("ALL");
            int needed;
            try {
                needed = all ? eggs.size() : Integer.parseInt(key);
            } catch (NumberFormatException exception) {
                continue;
            }
            if (needed <= 0 || count != needed) {
                continue;
            }
            Reward bonus = Reward.read(milestones.getConfigurationSection(key));
            bonus.give(plugin, player);
            Text.send(player, "EVENTS.EGG-MILESTONE", "&d&lMilestone! &7{count} eggs: &f{reward}",
                    "{count}", Integer.toString(count), "{reward}", String.join("&7, ", bonus.describe(plugin)));
            if (all) {
                Text.broadcast(Text.prefix() + Text.get("EVENTS.EGG-ALL", "&d{player} &7found every Easter egg!",
                        "{player}", player.getName()));
            }
        }
    }

    /** Eggs a nearby player has not found yet give off a few particles only that player sees. */
    private void sparkle() {
        if (eggs.isEmpty()) {
            return;
        }
        double radius = Math.max(4D, config().getDouble("EGGS.SPARKLE-RADIUS", 24D));
        double radiusSquared = radius * radius;
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location at = player.getLocation();
            Set<String> found = null;
            for (Egg egg : eggs.values()) {
                if (!egg.world().equals(at.getWorld().getName())) {
                    continue;
                }
                Location centre = egg.centre(at.getWorld());
                if (centre.distanceSquared(at) > radiusSquared) {
                    continue;
                }
                if (found == null) {
                    found = found(player.getUniqueId());
                }
                if (!found.contains(egg.id())) {
                    player.spawnParticle(Particle.HAPPY_VILLAGER, centre, 4, 0.3, 0.3, 0.3, 0.0);
                }
            }
        }
    }

    // ------------------------------------------------------------------ staff tools

    /** Adds an egg at a block; returns its id, or null when an egg is already there. */
    String addEgg(Block block) {
        for (Egg egg : eggs.values()) {
            if (egg.isAt(block)) {
                return null;
            }
        }
        int next = 1;
        while (eggs.containsKey("egg" + next)) {
            next++;
        }
        String id = "egg" + next;
        eggs.put(id, new Egg(id, block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));
        saveEggs();
        return id;
    }

    /** Removes the egg at a block, or by id; returns the removed egg's id or null. */
    String removeEgg(Block block, String id) {
        Egg removed = null;
        if (id != null) {
            removed = eggs.remove(id.toLowerCase(Locale.ROOT));
        } else if (block != null) {
            for (Egg egg : List.copyOf(eggs.values())) {
                if (egg.isAt(block)) {
                    removed = eggs.remove(egg.id());
                    break;
                }
            }
        }
        if (removed != null) {
            saveEggs();
        }
        return removed == null ? null : removed.id();
    }

    /** Clears one player's found eggs for this season (online or not). */
    void resetPlayer(UUID player) {
        PlayerStore store = store();
        if (store != null) {
            store.set(player, foundKey(), null);
        }
    }

    /** The players who found the most eggs this season, read on the database thread. */
    CompletableFuture<List<Map.Entry<String, Integer>>> top(int limit) {
        PlayerStore store = store();
        String key = foundKey();
        if (store == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        return plugin.getDatabaseManager().supplyAsync(() -> {
            List<Map.Entry<String, Integer>> rows = new ArrayList<>();
            for (Map.Entry<UUID, String> row : store.readSectionBlocking(key).entrySet()) {
                int count = row.getValue() == null || row.getValue().isBlank() ? 0 : row.getValue().split(",").length;
                if (count > 0) {
                    String name = plugin.getDatabaseManager().getLastKnownUsername(row.getKey());
                    rows.add(Map.entry(name == null ? row.getKey().toString().substring(0, 8) : name, count));
                }
            }
            rows.sort(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                    .thenComparing(Map.Entry.comparingByKey()));
            return rows.size() > limit ? List.copyOf(rows.subList(0, limit)) : rows;
        });
    }

    // ------------------------------------------------------------------ storage

    private File eggFile() {
        return new File(plugin.getDataFolder(), EGG_FILE);
    }

    private void loadEggs() {
        eggs.clear();
        File file = eggFile();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not read " + EGG_FILE + "; no Easter eggs are placed until it is fixed.", exception);
            return;
        }
        ConfigurationSection section = yaml.getConfigurationSection("EGGS");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection egg = section.getConfigurationSection(id);
            if (egg != null && egg.getString("WORLD") != null) {
                String key = id.toLowerCase(Locale.ROOT);
                eggs.put(key, new Egg(key, egg.getString("WORLD"), egg.getInt("X"), egg.getInt("Y"), egg.getInt("Z")));
            }
        }
    }

    private void saveEggs() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Egg egg : eggs.values()) {
            String path = "EGGS." + egg.id();
            yaml.set(path + ".WORLD", egg.world());
            yaml.set(path + ".X", egg.x());
            yaml.set(path + ".Y", egg.y());
            yaml.set(path + ".Z", egg.z());
        }
        String text = "# Easter eggs placed with /easter addegg.\n" + yaml.saveToString();
        File file = eggFile();
        plugin.getSpigotScheduler().runAsync(() -> {
            try {
                Files.createDirectories(file.getParentFile().toPath());
                Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
            } catch (IOException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not save " + EGG_FILE, exception);
            }
        });
    }
}
