package com.bx.smpPlugin.anticheat;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Immutable snapshot of anticheat.yml. A reload builds a new snapshot and swaps it in whole. */
public final class AntiCheatConfig {

    /** What happens when a check reaches its BAN-AT level. */
    public enum BanMode {
        /** Never ban; BAN-AT is ignored. */
        OFF,
        /** Kick and put the player on the ban queue; staff approve with /anticheat bans. */
        QUEUE,
        /** Kick and ban automatically in the next ban wave (staff can still remove them before it runs). */
        AUTO;

        static BanMode parse(String raw) {
            if (raw == null) {
                return QUEUE;
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                return QUEUE;
            }
        }
    }

    /** Built-in defaults; anticheat.yml overrides any of them. */
    static final Map<CheckType, CheckSettings> DEFAULTS = defaults();

    static final List<String> DEFAULT_BLOCKED_BRANDS = List.of("wurst", "meteor", "aristois", "liquidbounce",
            "rusherhack", "thunderhack", "inertia", "boze", "konas", "salhack", "phobos", "future client");
    static final List<String> DEFAULT_BLOCKED_CHANNELS = List.of("wurst", "meteor-client", "aristois",
            "liquidbounce", "rusherhack", "thunderhack", "boze");

    private final boolean enabled;
    private final long alertCooldownMillis;
    private final boolean consoleAlerts;
    private final String alertFormat;
    private final String kickMessage;
    private final boolean kicksEnabled;
    private final BanMode banMode;
    private final String banCommand;
    private final long banWaveMillis;
    private final boolean kickOnQueue;
    private final boolean multiCheckEnabled;
    private final int multiCheckDistinct;
    private final long multiCheckWindowMillis;
    private final long serverLagGraceMillis;
    private final boolean clientScanEnabled;
    private final long clientScanDelayTicks;
    private final List<String> blockedBrands;
    private final List<String> blockedChannels;
    private final boolean evidenceFileLogging;
    private final int evidenceRetentionDays;
    private final int evidenceMemoryPerPlayer;
    private final Set<CheckType> bedrockSkipped;
    private final Map<String, Long> exemptionMillis;
    private final Map<CheckType, CheckSettings> checks;
    private final Map<String, Set<String>> xrayOreFamilies;
    private final Set<String> xrayFiller;

    private AntiCheatConfig(ConfigurationSection root) {
        this.enabled = root == null || root.getBoolean("ENABLED", true);
        this.alertCooldownMillis = (long) (Math.max(0D, getDouble(root, "ALERTS.COOLDOWN-SECONDS", 3D)) * 1000D);
        this.consoleAlerts = root == null || root.getBoolean("ALERTS.CONSOLE", true);
        this.alertFormat = getString(root, "ALERTS.FORMAT",
                "&8[&cAC&8] &f{player} &7failed &c{check} &8(&7{detail}&8) &7vl &f{vl} &7ping &f{ping}ms &8#{id}");
        this.kickMessage = getString(root, "ACTIONS.KICK-MESSAGE",
                "&cYou were removed by the server anti-cheat.\n&7Evidence ID: &f{id}\n&7Contact staff if this was a mistake.");

        this.kicksEnabled = root == null || root.getBoolean("ENFORCEMENT.KICKS", true);
        this.banMode = BanMode.parse(getString(root, "ENFORCEMENT.BANS", "QUEUE"));
        this.banCommand = getString(root, "ENFORCEMENT.BAN-COMMAND",
                "tempban {player} 30d Unfair advantage (anti-cheat #{id})");
        this.banWaveMillis = (long) (Math.max(1D, getDouble(root, "ENFORCEMENT.BAN-WAVE-MINUTES", 20D)) * 60_000D);
        this.kickOnQueue = root == null || root.getBoolean("ENFORCEMENT.KICK-ON-QUEUE", true);
        this.multiCheckEnabled = root == null || root.getBoolean("ENFORCEMENT.MULTI-CHECK.ENABLED", true);
        this.multiCheckDistinct = (int) Math.max(2D, getDouble(root, "ENFORCEMENT.MULTI-CHECK.DISTINCT-CHECKS", 4D));
        this.multiCheckWindowMillis = (long) (Math.max(1D, getDouble(root, "ENFORCEMENT.MULTI-CHECK.WINDOW-MINUTES", 10D)) * 60_000D);
        this.serverLagGraceMillis = (long) Math.max(0D, getDouble(root, "ENFORCEMENT.SERVER-LAG-GRACE-MS", 2_000D));

        this.clientScanEnabled = root == null || root.getBoolean("CLIENT-SCAN.ENABLED", true);
        this.clientScanDelayTicks = (long) Math.max(1D, getDouble(root, "CLIENT-SCAN.DELAY-TICKS", 60D));
        this.blockedBrands = lowerList(root == null || !root.isList("CLIENT-SCAN.BLOCKED-BRANDS")
                ? DEFAULT_BLOCKED_BRANDS : root.getStringList("CLIENT-SCAN.BLOCKED-BRANDS"));
        this.blockedChannels = lowerList(root == null || !root.isList("CLIENT-SCAN.BLOCKED-CHANNELS")
                ? DEFAULT_BLOCKED_CHANNELS : root.getStringList("CLIENT-SCAN.BLOCKED-CHANNELS"));

        this.evidenceFileLogging = root == null || root.getBoolean("EVIDENCE.FILE-LOGGING", true);
        this.evidenceRetentionDays = (int) Math.max(1D, getDouble(root, "EVIDENCE.RETENTION-DAYS", 14D));
        this.evidenceMemoryPerPlayer = (int) Math.max(5D, getDouble(root, "EVIDENCE.MEMORY-PER-PLAYER", 50D));

        Set<CheckType> skipped = EnumSet.noneOf(CheckType.class);
        List<String> configuredSkips = root == null || !root.isList("BEDROCK.SKIP-CHECKS")
                ? List.of("FLY", "SPEED", "KILLAURA", "AUTOCLICKER", "WALL-HIT", "TIMER", "NOFALL", "STEP",
                "JESUS", "VELOCITY", "CRITICALS", "SCAFFOLD", "INVENTORY", "AUTO-TOTEM")
                : root.getStringList("BEDROCK.SKIP-CHECKS");
        for (String raw : configuredSkips) {
            CheckType.fromInput(raw).ifPresent(skipped::add);
        }
        this.bedrockSkipped = Collections.unmodifiableSet(skipped);

        Map<String, Long> exemptions = new HashMap<>();
        exemptions.put("TELEPORT", 2_000L);
        exemptions.put("JOIN", 3_000L);
        exemptions.put("RESPAWN", 2_000L);
        exemptions.put("DAMAGE", 700L);
        exemptions.put("VELOCITY-BASE", 1_000L);
        exemptions.put("VELOCITY-PER-BLOCK", 700L);
        exemptions.put("FLIGHT-CHANGE", 2_500L);
        exemptions.put("VEHICLE-EXIT", 1_500L);
        exemptions.put("GLIDE-END", 2_500L);
        exemptions.put("RIPTIDE", 3_000L);
        exemptions.put("SPEAR", 1_500L);
        exemptions.put("GAMEMODE-CHANGE", 2_000L);
        ConfigurationSection exemptionSection = root == null ? null : root.getConfigurationSection("EXEMPTIONS");
        if (exemptionSection != null) {
            for (String key : exemptionSection.getKeys(false)) {
                String normalized = key.toUpperCase(Locale.ROOT).replace("-MS", "");
                exemptions.put(normalized, (long) Math.max(0D, exemptionSection.getDouble(key)));
            }
        }
        this.exemptionMillis = Collections.unmodifiableMap(exemptions);

        Map<CheckType, CheckSettings> settings = new EnumMap<>(CheckType.class);
        ConfigurationSection checksSection = root == null ? null : root.getConfigurationSection("CHECKS");
        for (CheckType type : CheckType.values()) {
            CheckSettings fallback = DEFAULTS.get(type);
            ConfigurationSection section = checksSection == null ? null : checksSection.getConfigurationSection(type.configKey());
            settings.put(type, readSettings(section, fallback));
        }
        this.checks = Collections.unmodifiableMap(settings);

        Map<String, Set<String>> families = new LinkedHashMap<>();
        ConfigurationSection familySection = root == null ? null : root.getConfigurationSection("CHECKS.XRAY.ORE-FAMILIES");
        if (familySection != null) {
            for (String family : familySection.getKeys(false)) {
                families.put(family.toUpperCase(Locale.ROOT), upperSet(familySection.getStringList(family)));
            }
        }
        if (families.isEmpty()) {
            families.put("DIAMOND", Set.of("DIAMOND_ORE", "DEEPSLATE_DIAMOND_ORE"));
            families.put("ANCIENT_DEBRIS", Set.of("ANCIENT_DEBRIS"));
        }
        this.xrayOreFamilies = Collections.unmodifiableMap(families);
        List<String> filler = root == null || !root.isList("CHECKS.XRAY.FILLER-BLOCKS")
                ? List.of("STONE", "DEEPSLATE", "TUFF", "GRANITE", "DIORITE", "ANDESITE", "CALCITE",
                "NETHERRACK", "BASALT", "BLACKSTONE", "SMOOTH_BASALT")
                : root.getStringList("CHECKS.XRAY.FILLER-BLOCKS");
        this.xrayFiller = upperSet(filler);
    }

    public static AntiCheatConfig from(ConfigurationSection root) {
        return new AntiCheatConfig(root);
    }

    private static final Set<String> RESERVED_KEYS =
            Set.of("ENABLED", "ALERT-AT", "ACTION-AT", "KICK-AT", "BAN-AT", "DECAY-PER-SECOND");

    private static CheckSettings readSettings(ConfigurationSection section, CheckSettings fallback) {
        if (section == null) {
            return fallback;
        }
        Map<String, Double> params = new HashMap<>(fallback.params());
        for (String key : section.getKeys(false)) {
            String upper = key.toUpperCase(Locale.ROOT);
            if (RESERVED_KEYS.contains(upper)) {
                continue;
            }
            if (section.isDouble(key) || section.isInt(key) || section.isLong(key)) {
                params.put(upper, section.getDouble(key));
            }
        }
        return new CheckSettings(
                section.getBoolean("ENABLED", fallback.enabled()),
                Math.max(0D, section.getDouble("ALERT-AT", fallback.alertAt())),
                Math.max(0D, section.getDouble("ACTION-AT", fallback.actionAt())),
                Math.max(0D, section.getDouble("KICK-AT", fallback.kickAt())),
                Math.max(0D, section.getDouble("BAN-AT", fallback.banAt())),
                Math.max(0D, section.getDouble("DECAY-PER-SECOND", fallback.decayPerSecond())),
                params);
    }

    private static CheckSettings check(double alert, double act, double kick, double ban, double decay,
                                       Map<String, Double> params) {
        return new CheckSettings(true, alert, act, kick, ban, decay, params);
    }

    private static Map<CheckType, CheckSettings> defaults() {
        Map<CheckType, CheckSettings> defaults = new EnumMap<>(CheckType.class);
        defaults.put(CheckType.FLY, check(4, 6, 25, 50, 1.0, Map.of(
                "HEIGHT-TOLERANCE", 0.35, "MAX-HOVER-TICKS", 4.0, "MAX-SLOW-AIR-TICKS", 30.0,
                "AIR-JUMP-TOLERANCE", 0.03)));
        defaults.put(CheckType.SPEED, check(5, 8, 30, 60, 1.5, Map.of(
                "TOLERANCE", 0.06, "BALANCE-LIMIT", 0.6)));
        defaults.put(CheckType.REACH, check(3, 5, 15, 30, 0.5, Map.of(
                "TOLERANCE", 0.35, "MAX-PING-MS", 400.0, "SPEAR-EXTRA-RANGE", 2.0)));
        defaults.put(CheckType.WALL_HIT, check(3, 5, 15, 0, 0.5, Map.of(
                "MIN-DISTANCE", 1.0)));
        defaults.put(CheckType.KILLAURA, check(4, 6, 20, 40, 0.5, Map.of(
                "MAX-ANGLE", 75.0, "MIN-DISTANCE", 1.5, "MAX-TARGETS-PER-TICK", 3.0, "MAX-SWITCH-ANGLE", 60.0)));
        defaults.put(CheckType.AUTOCLICKER, check(6, 0, 0, 0, 1.0, Map.of(
                "MAX-CPS", 22.0)));
        defaults.put(CheckType.FAST_BREAK, check(3, 4, 15, 30, 0.3, Map.of(
                "PROGRESS-FLOOR", 0.7, "LATENCY-TICKS", 2.0)));
        defaults.put(CheckType.NUKER, check(3, 4, 10, 20, 0.5, Map.of(
                "MAX-PER-TICK", 4.0, "MAX-PER-SECOND", 22.0)));
        defaults.put(CheckType.FAST_PLACE, check(3, 5, 15, 0, 0.5, Map.of(
                "MAX-PER-TICK", 4.0, "MAX-PER-SECOND", 15.0)));
        defaults.put(CheckType.BLOCK_REACH, check(3, 4, 15, 30, 0.5, Map.of(
                "TOLERANCE", 0.5)));
        defaults.put(CheckType.XRAY, check(1, 0, 0, 0, 0.0005, Map.of(
                "WINDOW-MINUTES", 60.0, "MIN-HIDDEN-VEINS", 5.0, "MAX-VEINS-PER-THOUSAND", 12.0,
                "MIN-FILLER", 200.0, "VEIN-RADIUS", 3.0, "OVERWORLD-MAX-Y", 16.0, "NETHER-MAX-Y", 119.0)));
        defaults.put(CheckType.TIMER, check(5, 8, 25, 50, 1.0, Map.of(
                "MAX-AHEAD-MS", 250.0, "LAG-ALLOWANCE-MS", 1500.0)));
        defaults.put(CheckType.NOFALL, check(3, 3, 20, 40, 0.5, Map.of(
                "MIN-FALL-SPEED", 0.08)));
        defaults.put(CheckType.STEP, check(3, 3, 15, 30, 0.5, Map.of(
                "TOLERANCE", 0.05)));
        defaults.put(CheckType.JESUS, check(4, 5, 20, 0, 0.5, Map.of(
                "MIN-TICKS", 6.0)));
        defaults.put(CheckType.VEHICLE_FLY, check(3, 4, 15, 0, 0.5, Map.of(
                "MIN-RISE-TICKS", 6.0)));
        defaults.put(CheckType.VELOCITY, check(4, 0, 20, 40, 0.3, Map.of(
                "MIN-RATIO", 0.2, "MIN-VELOCITY", 0.3, "SAMPLE-UPDATES", 5.0)));
        defaults.put(CheckType.CRITICALS, check(4, 4, 20, 0, 0.5, Map.of(
                "MAX-RISE", 0.12)));
        defaults.put(CheckType.SCAFFOLD, check(3, 3, 15, 30, 0.5, Map.of(
                "FACE-TOLERANCE", 0.05, "LOOK-TOLERANCE", 0.6, "MAX-TOWER-PER-SECOND", 3.5)));
        defaults.put(CheckType.GHOST_HAND, check(3, 3, 15, 0, 0.5, Map.of(
                "MIN-DISTANCE", 1.5)));
        defaults.put(CheckType.INVENTORY, check(4, 4, 20, 0, 0.5, Map.of(
                "MAX-CLICKS-PER-TICK", 6.0, "MAX-CLICKS-PER-SECOND", 30.0, "MIN-MOVE-SPEED", 0.2,
                "MOVE-SPEED-RATIO", 0.9)));
        defaults.put(CheckType.AUTO_TOTEM, check(3, 0, 0, 0, 0.2, Map.of(
                "MIN-REACTION-MS", 100.0)));
        defaults.put(CheckType.BAD_CLIENT, check(1, 0, 1, 0, 0.01, Map.of()));
        return Collections.unmodifiableMap(defaults);
    }

    private static double getDouble(ConfigurationSection root, String path, double fallback) {
        return root == null ? fallback : root.getDouble(path, fallback);
    }

    private static String getString(ConfigurationSection root, String path, String fallback) {
        return root == null ? fallback : root.getString(path, fallback);
    }

    private static Set<String> upperSet(List<String> values) {
        Set<String> result = new java.util.HashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value.trim().toUpperCase(Locale.ROOT));
            }
        }
        return Collections.unmodifiableSet(result);
    }

    private static List<String> lowerList(List<String> values) {
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .toList();
    }

    public boolean enabled() {
        return enabled;
    }

    public long alertCooldownMillis() {
        return alertCooldownMillis;
    }

    public boolean consoleAlerts() {
        return consoleAlerts;
    }

    public String alertFormat() {
        return alertFormat;
    }

    public String kickMessage() {
        return kickMessage;
    }

    public boolean kicksEnabled() {
        return kicksEnabled;
    }

    public BanMode banMode() {
        return banMode;
    }

    public String banCommand() {
        return banCommand;
    }

    public long banWaveMillis() {
        return banWaveMillis;
    }

    public boolean kickOnQueue() {
        return kickOnQueue;
    }

    public boolean multiCheckEnabled() {
        return multiCheckEnabled;
    }

    public int multiCheckDistinct() {
        return multiCheckDistinct;
    }

    public long multiCheckWindowMillis() {
        return multiCheckWindowMillis;
    }

    public long serverLagGraceMillis() {
        return serverLagGraceMillis;
    }

    public boolean clientScanEnabled() {
        return clientScanEnabled;
    }

    public long clientScanDelayTicks() {
        return clientScanDelayTicks;
    }

    /** Lower-case substrings; a client brand containing any of them is flagged. */
    public List<String> blockedBrands() {
        return blockedBrands;
    }

    /** Lower-case substrings; a registered plugin channel containing any of them is flagged. */
    public List<String> blockedChannels() {
        return blockedChannels;
    }

    public boolean evidenceFileLogging() {
        return evidenceFileLogging;
    }

    public int evidenceRetentionDays() {
        return evidenceRetentionDays;
    }

    public int evidenceMemoryPerPlayer() {
        return evidenceMemoryPerPlayer;
    }

    public boolean skipForBedrock(CheckType check) {
        return bedrockSkipped.contains(check);
    }

    public long exemptionMillis(String key) {
        return exemptionMillis.getOrDefault(key.toUpperCase(Locale.ROOT), 1_000L);
    }

    public CheckSettings settings(CheckType check) {
        return checks.get(check);
    }

    public Map<String, Set<String>> xrayOreFamilies() {
        return xrayOreFamilies;
    }

    public boolean isXrayFiller(String material) {
        return xrayFiller.contains(material);
    }
}
