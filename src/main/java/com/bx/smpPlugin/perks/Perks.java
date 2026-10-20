package com.bx.smpPlugin.perks;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * The ranks of perks.yml and what each adds. Nothing about ranks is built in: there can be any number of them,
 * with any names, and the order comes only from their PRIORITY.
 *
 * <pre>
 * ENABLED: true
 * CAPS:                              # optional lower limits, per perk
 *   CALENDAR-BONUS-PERCENT: 50
 * RANKS:
 *   vip:
 *     PERMISSION: smpplugin.rank.vip
 *     PRIORITY: 10
 *     DISPLAY: "&amp;a&amp;lVIP"
 *     PERKS:
 *       CALENDAR-BONUS-PERCENT: 10
 * </pre>
 *
 * The file is read again on every {@link #reload()}; problems (an unknown perk, a value over its cap, two ranks with
 * one permission) are logged once per reload and the bad value is left out or capped, never guessed.
 */
public final class Perks {

    private final Supplier<FileConfiguration> config;
    private final BooleanSupplier featureEnabled;
    private final Logger logger;
    private volatile List<Rank> ranks = List.of();
    private volatile List<String> problems = List.of();

    public Perks(Supplier<FileConfiguration> config, BooleanSupplier featureEnabled, Logger logger) {
        this.config = config;
        this.featureEnabled = featureEnabled;
        this.logger = logger;
        reload();
    }

    /** Reads perks.yml again. */
    public void reload() {
        FileConfiguration file = config.get();
        List<String> found = new ArrayList<>();
        ranks = parse(file == null ? new YamlConfiguration() : file, found);
        problems = List.copyOf(found);
        for (String problem : found) {
            logger.warning("[Perks] " + problem);
        }
    }

    /** True when perks.yml ENABLED is true and the PERKS feature is on. */
    public boolean isEnabled() {
        FileConfiguration file = config.get();
        return (file == null || file.getBoolean("ENABLED", true)) && featureEnabled.getAsBoolean();
    }

    /** Every rank, highest priority first. */
    public List<Rank> ranks() {
        return ranks;
    }

    public Optional<Rank> rank(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String wanted = id.trim().toLowerCase(Locale.ROOT);
        return ranks.stream().filter(rank -> rank.id().equals(wanted)).findFirst();
    }

    /** What the last reload complained about. */
    public List<String> problems() {
        return problems;
    }

    /** The highest rank the player has (by permission), or empty. */
    public Optional<Rank> rankOf(Predicate<String> hasPermission) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        for (Rank rank : ranks) {
            if (hasPermission.test(rank.permission())) {
                return Optional.of(rank);
            }
        }
        return Optional.empty();
    }

    /** The perk's value for a player: their highest rank's value, or 0. */
    public int value(Predicate<String> hasPermission, Perk perk) {
        return rankOf(hasPermission).map(rank -> rank.value(perk)).orElse(0);
    }

    /** {@code amount} plus the perk's percent, rounded down so a bonus never mints fractions of a cent. */
    public static double withPercent(double amount, int percent) {
        if (amount <= 0D || percent <= 0) {
            return amount;
        }
        long cents = Math.round(amount * 100D);
        return (cents + cents * percent / 100L) / 100D;
    }

    /** As {@link #withPercent(double, int)} for whole numbers (shards, candy). */
    public static long withPercent(long amount, int percent) {
        if (amount <= 0L || percent <= 0) {
            return amount;
        }
        return amount + amount * percent / 100L;
    }

    // ------------------------------------------------------------------ parsing

    static List<Rank> parse(FileConfiguration file, List<String> problems) {
        Map<Perk, Integer> caps = new EnumMap<>(Perk.class);
        for (Perk perk : Perk.values()) {
            int cap = perk.hardCap();
            if (file.contains("CAPS." + perk.key())) {
                int configured = file.getInt("CAPS." + perk.key(), cap);
                if (configured > cap) {
                    problems.add("CAPS." + perk.key() + " is " + configured + " but can be at most " + cap + "; using " + cap + ".");
                }
                cap = Math.max(0, Math.min(cap, configured));
            }
            caps.put(perk, cap);
        }
        ConfigurationSection section = file.getConfigurationSection("RANKS");
        List<Rank> parsed = new ArrayList<>();
        if (section == null) {
            return parsed;
        }
        Set<String> permissions = new HashSet<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection rank = section.getConfigurationSection(id);
            if (rank == null) {
                continue;
            }
            String permission = rank.getString("PERMISSION", "");
            if (permission == null || permission.isBlank()) {
                problems.add("Rank '" + id + "' has no PERMISSION and is ignored.");
                continue;
            }
            permission = permission.trim();
            if (!permissions.add(permission.toLowerCase(Locale.ROOT))) {
                problems.add("Rank '" + id + "' uses the permission " + permission + " of another rank and is ignored.");
                continue;
            }
            Map<Perk, Integer> values = new EnumMap<>(Perk.class);
            ConfigurationSection perks = rank.getConfigurationSection("PERKS");
            if (perks != null) {
                for (String key : perks.getKeys(false)) {
                    Perk perk = byKey(key);
                    if (perk == null) {
                        problems.add("Rank '" + id + "' has an unknown perk " + key + "; known perks: " + keys() + ".");
                        continue;
                    }
                    int value = perks.getInt(key, 0);
                    int cap = caps.get(perk);
                    if (value < 0) {
                        problems.add("Rank '" + id + "' " + key + " is negative; using 0.");
                        value = 0;
                    } else if (value > cap) {
                        problems.add("Rank '" + id + "' " + key + " is " + value + " but the cap is " + cap + "; using " + cap + ".");
                        value = cap;
                    }
                    if (value > 0) {
                        values.put(perk, value);
                    }
                }
            }
            parsed.add(new Rank(id.toLowerCase(Locale.ROOT), permission, rank.getInt("PRIORITY", 0),
                    rank.getString("DISPLAY", id), values));
        }
        // Highest priority first; equal priorities keep the order of the file.
        parsed.sort(Comparator.comparingInt(Rank::priority).reversed());
        return parsed;
    }

    static Perk byKey(String key) {
        String wanted = key == null ? "" : key.trim().toUpperCase(Locale.ROOT).replace('_', '-');
        for (Perk perk : Perk.values()) {
            if (perk.key().equals(wanted)) {
                return perk;
            }
        }
        return null;
    }

    private static String keys() {
        List<String> keys = new ArrayList<>();
        for (Perk perk : Perk.values()) {
            keys.add(perk.key());
        }
        return String.join(", ", keys);
    }
}
