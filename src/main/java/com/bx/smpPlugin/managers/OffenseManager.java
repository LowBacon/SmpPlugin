package com.bx.smpPlugin.managers;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.models.PunishmentFilterState;
import com.bx.smpPlugin.models.PunishmentQuery;
import com.bx.smpPlugin.models.PunishmentRecord;
import com.bx.smpPlugin.models.PunishmentSortOrder;
import com.bx.smpPlugin.models.PunishmentType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Preset offenses for {@code /offend}, read from offenses.yml. Each offense has a punishment type
 * and a ladder of durations; a player's earlier punishments for the same offense pick the rung.
 */
public class OffenseManager {

    /** "mo" is listed before the single letters so "1mo" is a month, not one minute. */
    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)\\s*(mo|[smhdwy])", Pattern.CASE_INSENSITIVE);

    /** How far back the escalation count looks. Older records past this still count as one tier. */
    private static final int HISTORY_SCAN_LIMIT = 500;

    public record OffenseRule(
            String key,
            String name,
            PunishmentType type,
            List<String> durations
    ) {
        public OffenseRule {
            durations = durations == null ? List.of() : List.copyOf(durations);
        }

        /** The duration for a player with {@code tierIndex} earlier offenses; the last rung repeats. */
        public String getDurationForTier(int tierIndex) {
            if (durations.isEmpty()) {
                return "perm";
            }
            int index = Math.max(0, Math.min(tierIndex, durations.size() - 1));
            return durations.get(index);
        }

        /** The text stored as the punishment reason, which also marks the record as this offense. */
        public String reasonText() {
            return name + " (" + key + ")";
        }
    }

    private final SmpPlugin plugin;
    private volatile Map<String, OffenseRule> offenses = Map.of();

    public OffenseManager(SmpPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "offenses.yml");
        if (!file.exists()) {
            plugin.saveResource("offenses.yml", false);
        }
        offenses = parse(YamlConfiguration.loadConfiguration(file));
    }

    static Map<String, OffenseRule> parse(YamlConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("offenses");
        if (section == null) {
            return Map.of();
        }

        Map<String, OffenseRule> parsed = new TreeMap<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection offenseSection = section.getConfigurationSection(key);
            if (offenseSection != null) {
                OffenseRule rule = parseRule(key, offenseSection);
                parsed.put(rule.key(), rule);
            }
        }
        return Collections.unmodifiableMap(parsed);
    }

    static OffenseRule parseRule(String key, ConfigurationSection section) {
        String normalizedKey = key.toLowerCase(Locale.ROOT);
        String name = section.getString("name", key);
        PunishmentType type = PunishmentType.fromString(section.getString("type", "BAN"), PunishmentType.BAN);

        List<String> durations = section.getStringList("durations");
        if (durations.isEmpty() && section.isString("durations")) {
            durations = List.of(section.getString("durations", "perm"));
        }
        return new OffenseRule(normalizedKey, name, type, durations);
    }

    public Optional<OffenseRule> getOffenseRule(String key) {
        if (key == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(offenses.get(key.toLowerCase(Locale.ROOT)));
    }

    public Set<String> getOffenseKeys() {
        return offenses.keySet();
    }

    public Map<String, OffenseRule> getOffenses() {
        return offenses;
    }

    /**
     * Counts the player's earlier punishments for this offense, so each offense climbs its own
     * ladder. Punishments staff removed (a won appeal, a mistake) do not count.
     */
    public int countPreviousOffenses(UUID targetUuid, OffenseRule rule) {
        if (targetUuid == null || rule == null) {
            return 0;
        }
        PunishmentQuery query = new PunishmentQuery(null, PunishmentFilterState.ALL, PunishmentSortOrder.NEWEST);
        List<PunishmentRecord> history = plugin.getPunishmentManager()
                .getHistory(targetUuid, HISTORY_SCAN_LIMIT, 0, query);
        return countMatching(history, rule);
    }

    static int countMatching(List<PunishmentRecord> history, OffenseRule rule) {
        String marker = "(" + rule.key() + ")";
        int count = 0;
        for (PunishmentRecord record : history) {
            if (record.isRemoved()) {
                continue;
            }
            String reason = record.getReason();
            if (reason != null && reason.toLowerCase(Locale.ROOT).endsWith(marker)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Parses a duration such as "30s", "15m", "2h", "5d", "1w", "1mo", "1y" or a combination like
     * "1d12h". Returns {@code null} for permanent ("perm", "permanent" or anything unreadable) and
     * {@code 0} for an instant warning ("0" or "0s"). A bare number is read as days.
     */
    public static Long parseDurationToMillis(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        String cleaned = input.trim().toLowerCase(Locale.ROOT);
        if (cleaned.equals("perm") || cleaned.equals("permanent")) {
            return null;
        }
        if (cleaned.equals("0") || cleaned.equals("0s")) {
            return 0L;
        }

        Matcher matcher = DURATION_PATTERN.matcher(cleaned);
        long totalMillis = 0L;
        boolean matchedAny = false;
        while (matcher.find()) {
            matchedAny = true;
            long amount = Long.parseLong(matcher.group(1));
            switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
                case "s" -> totalMillis += amount * 1_000L;
                case "m" -> totalMillis += amount * 60_000L;
                case "h" -> totalMillis += amount * 3_600_000L;
                case "d" -> totalMillis += amount * 86_400_000L;
                case "w" -> totalMillis += amount * 604_800_000L;
                case "mo" -> totalMillis += amount * 30L * 86_400_000L;
                case "y" -> totalMillis += amount * 365L * 86_400_000L;
                default -> {
                }
            }
        }

        if (!matchedAny) {
            try {
                return Long.parseLong(cleaned) * 86_400_000L;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return totalMillis;
    }
}
