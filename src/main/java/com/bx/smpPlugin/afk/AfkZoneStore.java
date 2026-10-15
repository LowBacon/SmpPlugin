package com.bx.smpPlugin.afk;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.CuboidManager;
import com.bx.smpPlugin.utils.LocationUtils;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reads and writes the {@code ZONES} section of afk.yml, and on first start brings over the AFK
 * setups of older versions (the AFK Lounge, /setafk points, the AFK location and AFK-bound cuboids)
 * so nothing an admin built is lost.
 */
final class AfkZoneStore {

    private static final Pattern VALID_ID = Pattern.compile("^[a-z0-9_-]{1,32}$");

    private final SmpPlugin plugin;
    private volatile Map<String, AfkZone> zones = Map.of();

    AfkZoneStore(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    static boolean isValidId(String id) {
        return id != null && VALID_ID.matcher(id).matches();
    }

    static String normalizeId(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    Collection<AfkZone> all() {
        return zones.values();
    }

    AfkZone get(String id) {
        return zones.get(normalizeId(id));
    }

    void load() {
        FileConfiguration config = plugin.getConfigManager().getAfk();
        if (!config.getBoolean("MIGRATED", false)) {
            migrate(config);
        }
        Map<String, AfkZone> loaded = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("ZONES");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection zone = section.getConfigurationSection(key);
                if (zone != null) {
                    String id = normalizeId(key);
                    loaded.put(id, read(id, zone));
                }
            }
        }
        zones = Map.copyOf(loaded);
    }

    private static AfkZone read(String id, ConfigurationSection section) {
        AfkZone.Region region = null;
        ConfigurationSection box = section.getConfigurationSection("REGION");
        if (box != null && box.getString("WORLD", "").length() > 0) {
            region = new AfkZone.Region(box.getString("WORLD"),
                    box.getDouble("MIN-X"), box.getDouble("MIN-Y"), box.getDouble("MIN-Z"),
                    box.getDouble("MAX-X"), box.getDouble("MAX-Y"), box.getDouble("MAX-Z"));
        }
        return new AfkZone(id,
                section.getString("DISPLAY-NAME", "&d" + id),
                section.getString("SPAWN", ""),
                region,
                section.getLong("REWARD.AMOUNT", 1L),
                section.getInt("REWARD.EVERY-SECONDS", 60));
    }

    /** Saves or replaces one zone. */
    boolean put(AfkZone zone) {
        FileConfiguration config = plugin.getConfigManager().getAfk();
        write(config, zone);
        return saveAndReload();
    }

    boolean remove(String id) {
        FileConfiguration config = plugin.getConfigManager().getAfk();
        config.set("ZONES." + normalizeId(id), null);
        return saveAndReload();
    }

    private static void write(FileConfiguration config, AfkZone zone) {
        String path = "ZONES." + zone.id();
        config.set(path, null);
        config.set(path + ".DISPLAY-NAME", zone.displayName());
        config.set(path + ".SPAWN", zone.spawn());
        AfkZone.Region region = zone.region();
        if (region != null) {
            config.set(path + ".REGION.WORLD", region.world());
            config.set(path + ".REGION.MIN-X", region.minX());
            config.set(path + ".REGION.MIN-Y", region.minY());
            config.set(path + ".REGION.MIN-Z", region.minZ());
            config.set(path + ".REGION.MAX-X", region.maxX());
            config.set(path + ".REGION.MAX-Y", region.maxY());
            config.set(path + ".REGION.MAX-Z", region.maxZ());
        }
        config.set(path + ".REWARD.AMOUNT", zone.rewardAmount());
        config.set(path + ".REWARD.EVERY-SECONDS", zone.rewardSeconds());
    }

    private boolean saveAndReload() {
        boolean saved = plugin.getConfigManager().saveAfk();
        load();
        return saved;
    }

    // ── Migration from the old AFK systems ─────────────────────────────────────

    private void migrate(FileConfiguration afk) {
        List<AfkZone> imported = new ArrayList<>();
        importLounge(imported);
        importBoundCuboids(imported);
        importPoints(imported);

        for (AfkZone zone : imported) {
            if (!afk.isConfigurationSection("ZONES." + zone.id())) {
                write(afk, zone);
            }
        }
        afk.set("MIGRATED", true);
        plugin.getConfigManager().saveAfk();
        if (!imported.isEmpty()) {
            plugin.getLogger().info("[AFK] Moved " + imported.size() + " AFK zone(s) from the old AFK setup into afk.yml.");
        }
    }

    private void importLounge(List<AfkZone> imported) {
        File file = new File(plugin.getDataFolder(), "afk-lounge.yml");
        if (!file.exists()) {
            return;
        }
        YamlConfiguration lounge = YamlConfiguration.loadConfiguration(file);
        String world = lounge.getString("BORDER.WORLD", "");
        double minX = lounge.getDouble("BORDER.MIN-X");
        double maxX = lounge.getDouble("BORDER.MAX-X");
        double minZ = lounge.getDouble("BORDER.MIN-Z");
        double maxZ = lounge.getDouble("BORDER.MAX-Z");
        if (!lounge.getBoolean("BORDER.ENABLED", true) || world.isBlank() || maxX <= minX || maxZ <= minZ) {
            return;
        }
        AfkZone.Region region = new AfkZone.Region(world, minX, lounge.getDouble("BORDER.MIN-Y", -64D), minZ,
                maxX, lounge.getDouble("BORDER.MAX-Y", 320D), maxZ);
        String spawn = lounge.getString("LOCATIONS.AFK-LOUNGE-LOCATION", "");
        if (spawn.isBlank()) {
            spawn = world + "," + ((minX + maxX) / 2D) + ",100.0," + ((minZ + maxZ) / 2D) + ",0.0,0.0";
        }
        imported.add(new AfkZone("lounge", "&dAFK Lounge", spawn, region,
                lounge.getLong("SETTINGS.SHARD-REWARD-AMOUNT", 1L),
                lounge.getInt("SETTINGS.SHARD-REWARD-INTERVAL-SECONDS", 60)));
    }

    private void importBoundCuboids(List<AfkZone> imported) {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        List<String> names = new ArrayList<>(config.getStringList("CUBOID-BINDS.AFK"));
        String legacy = config.getString("AFK-SYSTEM.AFK-CUBOID-NAME", "");
        if (legacy != null && !legacy.isBlank() && !names.contains(legacy)) {
            names.add(legacy);
        }
        CuboidManager cuboids = plugin.getCuboidManager();
        for (String name : names) {
            CuboidManager.Cuboid cuboid = cuboids == null ? null : cuboids.getCuboid(name);
            String id = normalizeId(name);
            if (cuboid == null || !isValidId(id) || contains(imported, id)) {
                continue;
            }
            AfkZone.Region region = new AfkZone.Region(cuboid.world(),
                    Math.min(cuboid.x1(), cuboid.x2()), Math.min(cuboid.y1(), cuboid.y2()), Math.min(cuboid.z1(), cuboid.z2()),
                    Math.max(cuboid.x1(), cuboid.x2()) + 1D, Math.max(cuboid.y1(), cuboid.y2()) + 1D,
                    Math.max(cuboid.z1(), cuboid.z2()) + 1D);
            Location spawn = cuboids.getCuboidTeleportLocation(name);
            imported.add(new AfkZone(id, "&d" + name, LocationUtils.serialize(spawn), region, 1L, 60));
        }
    }

    /** /setafk points and the single AFK location become teleport-only zones. */
    private void importPoints(List<AfkZone> imported) {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        Map<String, String> points = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("AFK-SYSTEM.POINTS");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                points.put(key, section.getString(key + ".LOCATION", ""));
            }
        }
        String afkLocation = config.getString("LOCATIONS.AFK-LOCATION", "");
        if (afkLocation != null && !afkLocation.isBlank()) {
            points.put("afk", afkLocation);
        }

        for (Map.Entry<String, String> point : points.entrySet()) {
            String id = normalizeId(point.getKey());
            Location location = LocationUtils.parse(point.getValue());
            if (!isValidId(id) || point.getValue().isBlank() || contains(imported, id)) {
                continue;
            }
            // A point that already sits inside an imported zone would just be a second way in.
            if (location != null && imported.stream().anyMatch(zone -> zone.contains(location))) {
                continue;
            }
            String name = section == null ? null : section.getString(point.getKey() + ".DISPLAY-NAME");
            imported.add(new AfkZone(id, name == null ? "&d" + point.getKey() : name, point.getValue(), null, 0L, 60));
        }
    }

    private static boolean contains(List<AfkZone> zones, String id) {
        return zones.stream().anyMatch(zone -> zone.id().equals(id));
    }
}
