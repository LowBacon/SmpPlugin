package com.bx.smpPlugin.managers;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.utils.ItemUtils;
import com.bx.smpPlugin.utils.LocationUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * Stores the named AFK points created with {@code /setafk}.
 *
 * <p>Points live in config.yml under {@code AFK-SYSTEM.POINTS.<id>} so operators can hand-edit them.
 * Locations are kept as serialized strings and parsed on demand, which keeps points in worlds that
 * are not loaded yet from being dropped during startup.</p>
 */
public class AfkPointManager {

    public static final String POINTS_PATH = "AFK-SYSTEM.POINTS";
    public static final double DEFAULT_COUNT_RADIUS = 16.0D;

    private static final Pattern VALID_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,32}$");
    private static final int MAX_AUTO_ID = 1000;

    private final SmpPlugin plugin;
    private Map<String, AfkPoint> points = new LinkedHashMap<>();

    public AfkPointManager(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    public record AfkPoint(
            String id,
            String displayName,
            Material icon,
            int capacity,
            String rawLocation
    ) {
        /** Resolves the stored location, or {@code null} when the world is missing or the value is broken. */
        public Location location() {
            return LocationUtils.parse(rawLocation);
        }
    }

    public record PointResult(boolean success, String id, boolean overwrote, String message) {
        public static PointResult success(String id, boolean overwrote) {
            return new PointResult(true, id, overwrote, "");
        }

        public static PointResult failure(String message) {
            return new PointResult(false, "", false, message);
        }
    }

    public void load() {
        Map<String, AfkPoint> loaded = new LinkedHashMap<>();
        ConfigurationSection section = plugin.getConfigManager().getConfig()
                .getConfigurationSection(POINTS_PATH);

        if (section != null) {
            List<String> keys = new ArrayList<>(section.getKeys(false));
            keys.sort(AfkPointManager::compareIds);

            for (String key : keys) {
                String id = normalizeName(key);
                if (id == null) {
                    continue;
                }

                ConfigurationSection pointSection = section.getConfigurationSection(key);
                String rawLocation = pointSection == null
                        ? section.getString(key, "")
                        : pointSection.getString("LOCATION", "");

                if (rawLocation == null || rawLocation.isBlank()) {
                    plugin.getLogger().warning("[AfkPointManager] " + POINTS_PATH + "." + key
                            + " has no LOCATION and was skipped.");
                    continue;
                }

                loaded.put(id, new AfkPoint(
                        id,
                        pointSection == null
                                ? defaultDisplayName(id)
                                : pointSection.getString("DISPLAY-NAME", defaultDisplayName(id)),
                        pointSection == null
                                ? Material.AMETHYST_CLUSTER
                                : ItemUtils.parseMaterial(pointSection.getString("ICON", "AMETHYST_CLUSTER")),
                        pointSection == null
                                ? 200
                                : Math.max(1, pointSection.getInt("CAPACITY", 200)),
                        rawLocation.trim()
                ));
            }
        }

        points = loaded;
    }

    /** Every stored point, including any whose world is not currently loaded. */
    public List<AfkPoint> getPoints() {
        return List.copyOf(points.values());
    }

    /** Only the points that currently resolve to a real location. */
    public List<AfkPoint> getResolvablePoints() {
        List<AfkPoint> resolvable = new ArrayList<>();
        for (AfkPoint point : points.values()) {
            if (point.location() != null) {
                resolvable.add(point);
            }
        }
        return List.copyOf(resolvable);
    }

    public AfkPoint getPoint(String name) {
        String id = normalizeName(name);
        return id == null ? null : points.get(id);
    }

    public boolean exists(String name) {
        return getPoint(name) != null;
    }

    public int count() {
        return points.size();
    }

    public List<String> getSortedIds() {
        List<String> ids = new ArrayList<>(points.keySet());
        ids.sort(AfkPointManager::compareIds);
        return Collections.unmodifiableList(ids);
    }

    public PointResult setPoint(String name, Location location) {
        if (location == null || location.getWorld() == null) {
            return PointResult.failure("that location is not in a loaded world.");
        }

        String id = normalizeName(name);
        if (id == null || !isValidName(name)) {
            return PointResult.failure("afk point names may only use letters, numbers, '-' and '_' (max 32).");
        }

        FileConfiguration config = plugin.getConfigManager().getConfig();
        String path = POINTS_PATH + "." + id;
        boolean overwrote = points.containsKey(id);

        config.set(path + ".LOCATION", LocationUtils.serialize(location));
        if (config.getString(path + ".DISPLAY-NAME") == null) {
            config.set(path + ".DISPLAY-NAME", defaultDisplayName(id));
        }
        if (config.getString(path + ".ICON") == null) {
            config.set(path + ".ICON", "AMETHYST_CLUSTER");
        }
        if (!config.isInt(path + ".CAPACITY")) {
            config.set(path + ".CAPACITY", 200);
        }

        if (!saveConfig()) {
            return PointResult.failure("failed to save config.yml.");
        }

        load();
        return PointResult.success(id, overwrote);
    }

    public PointResult deletePoint(String name) {
        String id = normalizeName(name);
        if (id == null || !points.containsKey(id)) {
            return PointResult.failure("there is no afk point named '" + name + "'.");
        }

        plugin.getConfigManager().getConfig().set(POINTS_PATH + "." + id, null);
        if (!saveConfig()) {
            return PointResult.failure("failed to save config.yml.");
        }

        load();
        return PointResult.success(id, false);
    }

    /** The first unused {@code afk<N>} id, or {@code null} when the sequence is exhausted. */
    public String nextAutoId() {
        for (int candidate = 1; candidate <= MAX_AUTO_ID; candidate++) {
            String id = "afk" + candidate;
            if (!points.containsKey(id)) {
                return id;
            }
        }
        return null;
    }

    public AfkPoint randomPoint() {
        List<AfkPoint> resolvable = getResolvablePoints();
        if (resolvable.isEmpty()) {
            return null;
        }
        return resolvable.get(ThreadLocalRandom.current().nextInt(resolvable.size()));
    }

    public Location randomLocation() {
        AfkPoint point = randomPoint();
        return point == null ? null : point.location();
    }

    /** The nearest point in the player's world, or {@code null} when none share their world. */
    public AfkPoint closestPoint(Player player) {
        if (player == null || player.getWorld() == null) {
            return null;
        }

        AfkPoint closest = null;
        double closestDistanceSquared = Double.MAX_VALUE;
        for (AfkPoint point : getResolvablePoints()) {
            Location location = point.location();
            if (location.getWorld() == null || !location.getWorld().equals(player.getWorld())) {
                continue;
            }

            double distanceSquared = location.distanceSquared(player.getLocation());
            if (distanceSquared < closestDistanceSquared) {
                closest = point;
                closestDistanceSquared = distanceSquared;
            }
        }
        return closest;
    }

    public int countPlayersNear(Location location) {
        return countPlayersNear(location, DEFAULT_COUNT_RADIUS);
    }

    public int countPlayersNear(Location location, double radius) {
        if (location == null || location.getWorld() == null) {
            return 0;
        }

        double radiusSquared = radius * radius;
        int count = 0;
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            Location playerLocation = online.getLocation();
            if (playerLocation.getWorld() != null
                    && playerLocation.getWorld().equals(location.getWorld())
                    && playerLocation.distanceSquared(location) <= radiusSquared) {
                count++;
            }
        }
        return count;
    }

    public boolean isValidName(String name) {
        return name != null && VALID_NAME_PATTERN.matcher(name.trim()).matches();
    }

    public String normalizeName(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private boolean saveConfig() {
        try {
            return plugin.getConfigManager().saveConfig();
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("[AfkPointManager] failed to save config.yml: " + exception.getMessage());
            return false;
        }
    }

    private static String defaultDisplayName(String id) {
        return "&#A303F9" + id;
    }

    /** Sorts {@code afk2} before {@code afk10} instead of lexicographically. */
    private static int compareIds(String first, String second) {
        Integer firstNumber = trailingNumber(first);
        Integer secondNumber = trailingNumber(second);
        if (firstNumber != null && secondNumber != null) {
            String firstPrefix = first.substring(0, first.length() - firstNumber.toString().length());
            String secondPrefix = second.substring(0, second.length() - secondNumber.toString().length());
            if (firstPrefix.equalsIgnoreCase(secondPrefix)) {
                return Integer.compare(firstNumber, secondNumber);
            }
        }
        return first.compareToIgnoreCase(second);
    }

    private static Integer trailingNumber(String value) {
        int index = value.length();
        while (index > 0 && Character.isDigit(value.charAt(index - 1))) {
            index--;
        }
        if (index == value.length() || index == 0) {
            return null;
        }
        try {
            return Integer.parseInt(value.substring(index));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
