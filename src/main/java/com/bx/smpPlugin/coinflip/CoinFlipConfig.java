// SmpPlugin by LowBacon
package com.bx.smpPlugin.coinflip;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CoinFlipConfig {

    public static final List<String> MAIN_BUTTONS = List.of("BALANCE", "CREATE", "BROWSE", "CHALLENGE", "MY-MATCHES",
            "HISTORY", "STATS", "LEADERBOARDS", "ACHIEVEMENTS", "PRACTICE", "SETTINGS", "HELP", "CLOSE");
    private static final Map<String, Integer> DEFAULT_SLOTS = defaultSlots();

    private final List<String> problems = new ArrayList<>();
    private boolean wageringBlocked;

    private final boolean enabled;
    private final boolean maintenance;
    private final long minStakeCents;
    private final long maxStakeCents;
    private final boolean allowDecimals;
    private final int feeBasisPoints;
    private final boolean allowFullBalance;
    private final long highStakeConfirmCents;
    private final List<Long> incrementsCents;
    private final long expiryMillis;
    private final long challengeExpiryMillis;
    private final int maxActivePerPlayer;
    private final int maxActiveTotal;
    private final int maxListed;
    private final boolean allowPrivate;
    private final boolean allowRematch;
    private final long cooldownMillis;
    private final long dailyWagerCapCents;
    private final long dailyNetLossCapCents;
    private final int dailyMatchCap;
    private final long minPlaytimeMinutes;
    private final long sessionReminderMillis;
    private final List<Integer> selfExclusionHours;
    private final long clickCooldownMillis;
    private final long commandCooldownMillis;
    private final long browserRefreshCooldownMillis;
    private final int maxPendingStorageOperations;
    private final AnimationPreset defaultPreset;
    private final boolean particlesAllowed;
    private final boolean soundsAllowed;
    private final int maxConcurrentAnimations;
    private final Map<String, String> sounds;
    private final boolean broadcastEnabled;
    private final long broadcastMinPotCents;
    private final long broadcastCooldownMillis;
    private final long leaderboardRefreshMillis;
    private final int leaderboardSize;
    private final int winRateMinMatches;
    private final boolean achievementsEnabled;
    private final boolean achievementNotify;
    private final boolean achievementRewardsEnabled;
    private final Map<String, List<String>> achievementRewards;
    private final int historyRetentionDays;
    private final int queryTimeoutSeconds;
    private final boolean webhookEnabled;
    private final String webhookUrl;
    private final long webhookMinPotCents;
    private final int webhookTimeoutSeconds;
    private final long webhookMinIntervalMillis;
    private final String titlePrefix;
    private final boolean readableDefault;
    private final String headsMaterial;
    private final String tailsMaterial;
    private final String borderMaterial;
    private final String accentMaterial;
    private final Map<String, Integer> mainSlots;
    private final boolean debug;

    private CoinFlipConfig(ConfigurationSection root) {
        enabled = bool(root, "ENABLED", true);
        maintenance = bool(root, "MAINTENANCE", false);
        debug = bool(root, "DEBUG", false);

        allowDecimals = bool(root, "ECONOMY.ALLOW-DECIMALS", false);
        long min = money(root, "ECONOMY.MIN-STAKE", 100D, true);
        long max = money(root, "ECONOMY.MAX-STAKE", 1_000_000D, true);
        if (max > StakeMath.HARD_MAX_STAKE_CENTS) {
            problem("ECONOMY.MAX-STAKE", "at most " + StakeMath.format(StakeMath.HARD_MAX_STAKE_CENTS),
                    StakeMath.format(max), "wagering disabled until fixed");
            wageringBlocked = true;
        }
        if (min > max) {
            problem("ECONOMY.MIN-STAKE", "not above MAX-STAKE", StakeMath.format(min), "wagering disabled until fixed");
            wageringBlocked = true;
        }
        if (!allowDecimals && (min % 100L != 0L || max % 100L != 0L)) {
            problem("ECONOMY.MIN-STAKE / MAX-STAKE", "whole numbers while ALLOW-DECIMALS is false",
                    StakeMath.format(min) + " / " + StakeMath.format(max), "wagering disabled until fixed");
            wageringBlocked = true;
        }
        minStakeCents = min;
        maxStakeCents = max;

        double feePercent = number(root, "ECONOMY.FEE-PERCENT", 0D);
        int bps = (int) Math.round(feePercent * 100D);
        if (!Double.isFinite(feePercent) || feePercent < 0D || bps > StakeMath.MAX_FEE_BASIS_POINTS
                || Math.abs(feePercent * 100D - bps) > 1e-9) {
            problem("ECONOMY.FEE-PERCENT", "0 to 50 with at most two decimals", String.valueOf(feePercent),
                    "wagering disabled until fixed (a fee is never guessed)");
            wageringBlocked = true;
            bps = 0;
        }
        feeBasisPoints = bps;
        allowFullBalance = bool(root, "ECONOMY.ALLOW-FULL-BALANCE", false);
        highStakeConfirmCents = money(root, "ECONOMY.HIGH-STAKE-CONFIRM", 100_000D, false);
        List<Long> increments = new ArrayList<>();
        List<?> rawIncrements = root == null ? null : root.getList("ECONOMY.INCREMENTS");
        if (rawIncrements == null || rawIncrements.isEmpty()) {
            for (long value : new long[]{1, 10, 100, 1_000, 5_000, 10_000, 50_000, 100_000}) {
                increments.add(value * 100L);
            }
        } else {
            for (Object raw : rawIncrements) {
                StakeMath.ParseResult parsed = StakeMath.parse(String.valueOf(raw));
                if (parsed.ok()) {
                    increments.add(parsed.cents());
                } else {
                    problem("ECONOMY.INCREMENTS", "positive amounts", String.valueOf(raw), "entry skipped");
                }
            }
        }
        incrementsCents = List.copyOf(increments.stream().distinct().sorted().limit(8).toList());

        expiryMillis = seconds(root, "MATCHES.EXPIRY-SECONDS", 300, 30, 86_400);
        challengeExpiryMillis = seconds(root, "MATCHES.CHALLENGE-EXPIRY-SECONDS", 120, 15, 3_600);
        maxActivePerPlayer = integer(root, "MATCHES.MAX-ACTIVE-PER-PLAYER", 1, 1, 10);
        maxActiveTotal = integer(root, "MATCHES.MAX-ACTIVE-TOTAL", 200, 1, 5_000);
        maxListed = integer(root, "MATCHES.MAX-PUBLIC-LISTED", 200, 1, 5_000);
        allowPrivate = bool(root, "MATCHES.ALLOW-PRIVATE", true);
        allowRematch = bool(root, "MATCHES.ALLOW-REMATCH", true);

        cooldownMillis = seconds(root, "LIMITS.COOLDOWN-SECONDS", 3, 0, 3_600);
        dailyWagerCapCents = money(root, "LIMITS.DAILY-WAGER-CAP", 0D, false);
        dailyNetLossCapCents = money(root, "LIMITS.DAILY-NET-LOSS-CAP", 0D, false);
        dailyMatchCap = integer(root, "LIMITS.DAILY-MATCH-CAP", 0, 0, 100_000);
        minPlaytimeMinutes = integer(root, "LIMITS.MIN-PLAYTIME-MINUTES", 0, 0, 1_000_000);
        sessionReminderMillis = integer(root, "LIMITS.SESSION-REMINDER-MINUTES", 0, 0, 1_440) * 60_000L;
        List<Integer> exclusion = new ArrayList<>();
        List<Integer> rawExclusion = root == null ? List.of() : root.getIntegerList("LIMITS.SELF-EXCLUSION-HOURS");
        for (int hours : rawExclusion.isEmpty() ? List.of(1, 24, 168) : rawExclusion) {
            if (hours > 0 && hours <= 24 * 365) {
                exclusion.add(hours);
            }
        }
        selfExclusionHours = List.copyOf(exclusion.stream().distinct().sorted().limit(4).toList());

        clickCooldownMillis = integer(root, "SECURITY.CLICK-COOLDOWN-MS", 150, 0, 5_000);
        commandCooldownMillis = integer(root, "SECURITY.COMMAND-COOLDOWN-MS", 500, 0, 10_000);
        browserRefreshCooldownMillis = integer(root, "SECURITY.BROWSER-REFRESH-COOLDOWN-MS", 1_000, 0, 30_000);
        maxPendingStorageOperations = integer(root, "SECURITY.MAX-PENDING-STORAGE-OPERATIONS", 500, 10, 100_000);

        AnimationPreset preset = AnimationPreset.parse(string(root, "ANIMATION.DEFAULT-PRESET", "CLASSIC")).orElse(null);
        if (preset == null) {
            problem("ANIMATION.DEFAULT-PRESET", "one of FAST, SUBTLE, CLASSIC, CINEMATIC, REDUCED, OFF",
                    string(root, "ANIMATION.DEFAULT-PRESET", ""), "using CLASSIC");
            preset = AnimationPreset.CLASSIC;
        }
        defaultPreset = preset;
        particlesAllowed = bool(root, "ANIMATION.PARTICLES", true);
        soundsAllowed = bool(root, "ANIMATION.SOUNDS", true);
        maxConcurrentAnimations = integer(root, "ANIMATION.MAX-CONCURRENT", 100, 1, 2_000);
        Map<String, String> soundMap = new LinkedHashMap<>();
        soundMap.put("CLICK", "minecraft:ui.button.click|0.6|1.2");
        soundMap.put("TICK", "minecraft:block.note_block.hat|0.6|1.6");
        soundMap.put("REVEAL", "minecraft:block.note_block.bell|0.8|1.0");
        soundMap.put("WIN", "minecraft:entity.player.levelup|0.8|1.2");
        soundMap.put("RESULT", "minecraft:block.note_block.chime|0.7|0.9");
        soundMap.put("ERROR", "minecraft:block.note_block.bass|0.7|0.6");
        soundMap.put("INVITE", "minecraft:block.note_block.pling|0.8|1.4");
        ConfigurationSection soundSection = root == null ? null : root.getConfigurationSection("ANIMATION.SOUND-EFFECTS");
        if (soundSection != null) {
            for (String key : soundSection.getKeys(false)) {
                soundMap.put(key.toUpperCase(Locale.ROOT), soundSection.getString(key, ""));
            }
        }
        sounds = Collections.unmodifiableMap(soundMap);

        broadcastEnabled = bool(root, "BROADCAST.ENABLED", true);
        broadcastMinPotCents = money(root, "BROADCAST.MIN-POT", 100_000D, false);
        broadcastCooldownMillis = seconds(root, "BROADCAST.COOLDOWN-SECONDS", 30, 0, 3_600);

        leaderboardRefreshMillis = integer(root, "LEADERBOARDS.REFRESH-MINUTES", 5, 1, 1_440) * 60_000L;
        leaderboardSize = integer(root, "LEADERBOARDS.SIZE", 45, 5, 45);
        winRateMinMatches = integer(root, "LEADERBOARDS.MIN-MATCHES-FOR-WIN-RATE", 20, 1, 100_000);

        achievementsEnabled = bool(root, "ACHIEVEMENTS.ENABLED", true);
        achievementNotify = bool(root, "ACHIEVEMENTS.NOTIFY", true);
        achievementRewardsEnabled = bool(root, "ACHIEVEMENTS.REWARDS-ENABLED", false);
        Map<String, List<String>> rewards = new HashMap<>();
        ConfigurationSection rewardSection = root == null ? null : root.getConfigurationSection("ACHIEVEMENTS.REWARDS");
        if (rewardSection != null) {
            for (String key : rewardSection.getKeys(false)) {
                String upper = key.toUpperCase(Locale.ROOT).replace('-', '_');
                try {
                    Achievement.valueOf(upper);
                    rewards.put(upper, List.copyOf(rewardSection.getStringList(key)));
                } catch (IllegalArgumentException exception) {
                    problem("ACHIEVEMENTS.REWARDS." + key, "an achievement key", key, "ignored");
                }
            }
        }
        achievementRewards = Collections.unmodifiableMap(rewards);

        historyRetentionDays = integer(root, "STORAGE.HISTORY-RETENTION-DAYS", 0, 0, 36_500);
        queryTimeoutSeconds = integer(root, "STORAGE.QUERY-TIMEOUT-SECONDS", 10, 1, 120);

        webhookEnabled = bool(root, "WEBHOOK.ENABLED", false);
        String url = string(root, "WEBHOOK.URL", "");
        if (webhookEnabled && !(url.startsWith("https://discord.com/api/webhooks/")
                || url.startsWith("https://discordapp.com/api/webhooks/"))) {
            problem("WEBHOOK.URL", "a https://discord.com/api/webhooks/ URL", url.isBlank() ? "(empty)" : "(other URL)",
                    "webhook disabled");
        }
        webhookUrl = url;
        webhookMinPotCents = money(root, "WEBHOOK.MIN-POT", 100_000D, false);
        webhookTimeoutSeconds = integer(root, "WEBHOOK.TIMEOUT-SECONDS", 5, 1, 30);
        webhookMinIntervalMillis = seconds(root, "WEBHOOK.MIN-INTERVAL-SECONDS", 10, 1, 3_600);

        titlePrefix = string(root, "GUI.TITLE-PREFIX", "&5&lCoinFlip");
        readableDefault = bool(root, "GUI.READABLE-DEFAULT", false);
        headsMaterial = string(root, "GUI.MATERIALS.HEADS", "GOLD_BLOCK").toUpperCase(Locale.ROOT);
        tailsMaterial = string(root, "GUI.MATERIALS.TAILS", "REDSTONE_BLOCK").toUpperCase(Locale.ROOT);
        borderMaterial = string(root, "GUI.MATERIALS.BORDER", "BLACK_STAINED_GLASS_PANE").toUpperCase(Locale.ROOT);
        accentMaterial = string(root, "GUI.MATERIALS.ACCENT", "PURPLE_STAINED_GLASS_PANE").toUpperCase(Locale.ROOT);
        mainSlots = readSlots(root);
    }

    public static CoinFlipConfig from(ConfigurationSection root) {
        return new CoinFlipConfig(root);
    }

    private static Map<String, Integer> defaultSlots() {
        Map<String, Integer> slots = new LinkedHashMap<>();
        slots.put("BALANCE", 4);
        slots.put("CREATE", 20);
        slots.put("BROWSE", 22);
        slots.put("CHALLENGE", 24);
        slots.put("MY-MATCHES", 29);
        slots.put("HISTORY", 30);
        slots.put("STATS", 31);
        slots.put("LEADERBOARDS", 32);
        slots.put("ACHIEVEMENTS", 33);
        slots.put("PRACTICE", 38);
        slots.put("SETTINGS", 40);
        slots.put("HELP", 42);
        slots.put("CLOSE", 49);
        return Collections.unmodifiableMap(slots);
    }

    private Map<String, Integer> readSlots(ConfigurationSection root) {
        ConfigurationSection section = root == null ? null : root.getConfigurationSection("GUI.MAIN-SLOTS");
        if (section == null) {
            return DEFAULT_SLOTS;
        }
        Map<String, Integer> slots = new LinkedHashMap<>(DEFAULT_SLOTS);
        for (String key : section.getKeys(false)) {
            String upper = key.toUpperCase(Locale.ROOT);
            if (!DEFAULT_SLOTS.containsKey(upper)) {
                problem("GUI.MAIN-SLOTS." + key, "one of " + MAIN_BUTTONS, key, "ignored");
                continue;
            }
            int slot = section.getInt(key, -1);
            if (slot < 0 || slot > 53) {
                problem("GUI.MAIN-SLOTS." + key, "0 to 53", String.valueOf(slot), "default layout used");
                return DEFAULT_SLOTS;
            }
            slots.put(upper, slot);
        }
        Map<Integer, String> used = new HashMap<>();
        for (Map.Entry<String, Integer> entry : slots.entrySet()) {
            String previous = used.put(entry.getValue(), entry.getKey());
            if (previous != null) {
                problem("GUI.MAIN-SLOTS", "each button in its own slot",
                        previous + " and " + entry.getKey() + " both use " + entry.getValue(), "default layout used");
                return DEFAULT_SLOTS;
            }
        }
        return Collections.unmodifiableMap(slots);
    }

    // ── Reading helpers that report problems ─────────────────────────────────────

    private void problem(String key, String expected, String found, String action) {
        problems.add(key + ": expected " + expected + ", found " + found + " -> " + action);
    }

    private boolean bool(ConfigurationSection root, String key, boolean fallback) {
        if (root == null || !root.contains(key)) {
            return fallback;
        }
        if (!root.isBoolean(key)) {
            problem(key, "true or false", String.valueOf(root.get(key)), "using " + fallback);
            return fallback;
        }
        return root.getBoolean(key);
    }

    private String string(ConfigurationSection root, String key, String fallback) {
        if (root == null || !root.contains(key)) {
            return fallback;
        }
        return root.getString(key, fallback);
    }

    private double number(ConfigurationSection root, String key, double fallback) {
        if (root == null || !root.contains(key)) {
            return fallback;
        }
        Object raw = root.get(key);
        if (raw instanceof Number value) {
            return value.doubleValue();
        }
        problem(key, "a number", String.valueOf(raw), "treated as invalid");
        return Double.NaN;
    }

    private int integer(ConfigurationSection root, String key, int fallback, int min, int max) {
        double value = number(root, key, fallback);
        if (!Double.isFinite(value) || value != Math.rint(value) || value < min || value > max) {
            problem(key, "a whole number from " + min + " to " + max, String.valueOf(value), "using " + fallback);
            return fallback;
        }
        return (int) value;
    }

    private long seconds(ConfigurationSection root, String key, int fallback, int min, int max) {
        return integer(root, key, fallback, min, max) * 1_000L;
    }

    private long money(ConfigurationSection root, String key, double fallback, boolean required) {
        double value = number(root, key, fallback);
        if (!Double.isFinite(value) || value < 0D || (required && value <= 0D)) {
            problem(key, required ? "a positive amount" : "zero or a positive amount", String.valueOf(value),
                    required ? "wagering disabled until fixed" : "treated as 0 (off)");
            if (required) {
                wageringBlocked = true;
            }
            return required ? 100L : 0L;
        }
        StakeMath.ParseResult parsed = StakeMath.parse(java.math.BigDecimal.valueOf(value).toPlainString());
        if (value == 0D) {
            return 0L;
        }
        if (!parsed.ok()) {
            problem(key, "an amount with at most two decimals, below " + StakeMath.format(StakeMath.HARD_MAX_STAKE_CENTS),
                    String.valueOf(value), required ? "wagering disabled until fixed" : "treated as 0 (off)");
            if (required) {
                wageringBlocked = true;
            }
            return required ? 100L : 0L;
        }
        return parsed.cents();
    }

    // ── Accessors ─────────────────────────────────────────────────────────────────

    public List<String> problems() {
        return List.copyOf(problems);
    }

    public boolean wageringBlocked() {
        return wageringBlocked;
    }

    public boolean enabled() { return enabled; }
    public boolean maintenance() { return maintenance; }
    public boolean debug() { return debug; }
    public long minStakeCents() { return minStakeCents; }
    public long maxStakeCents() { return maxStakeCents; }
    public boolean allowDecimals() { return allowDecimals; }
    public int feeBasisPoints() { return feeBasisPoints; }
    public boolean allowFullBalance() { return allowFullBalance; }
    public long highStakeConfirmCents() { return highStakeConfirmCents; }
    public List<Long> incrementsCents() { return incrementsCents; }
    public long expiryMillis() { return expiryMillis; }
    public long challengeExpiryMillis() { return challengeExpiryMillis; }
    public int maxActivePerPlayer() { return maxActivePerPlayer; }
    public int maxActiveTotal() { return maxActiveTotal; }
    public int maxListed() { return maxListed; }
    public boolean allowPrivate() { return allowPrivate; }
    public boolean allowRematch() { return allowRematch; }
    public long cooldownMillis() { return cooldownMillis; }
    public long dailyWagerCapCents() { return dailyWagerCapCents; }
    public long dailyNetLossCapCents() { return dailyNetLossCapCents; }
    public int dailyMatchCap() { return dailyMatchCap; }
    public long minPlaytimeMinutes() { return minPlaytimeMinutes; }
    public long sessionReminderMillis() { return sessionReminderMillis; }
    public List<Integer> selfExclusionHours() { return selfExclusionHours; }
    public long clickCooldownMillis() { return clickCooldownMillis; }
    public long commandCooldownMillis() { return commandCooldownMillis; }
    public long browserRefreshCooldownMillis() { return browserRefreshCooldownMillis; }
    public int maxPendingStorageOperations() { return maxPendingStorageOperations; }
    public AnimationPreset defaultPreset() { return defaultPreset; }
    public boolean particlesAllowed() { return particlesAllowed; }
    public boolean soundsAllowed() { return soundsAllowed; }
    public int maxConcurrentAnimations() { return maxConcurrentAnimations; }
    public String sound(String key) { return sounds.getOrDefault(key, ""); }
    public boolean broadcastEnabled() { return broadcastEnabled; }
    public long broadcastMinPotCents() { return broadcastMinPotCents; }
    public long broadcastCooldownMillis() { return broadcastCooldownMillis; }
    public long leaderboardRefreshMillis() { return leaderboardRefreshMillis; }
    public int leaderboardSize() { return leaderboardSize; }
    public int winRateMinMatches() { return winRateMinMatches; }
    public boolean achievementsEnabled() { return achievementsEnabled; }
    public boolean achievementNotify() { return achievementNotify; }
    public boolean achievementRewardsEnabled() { return achievementRewardsEnabled; }
    public List<String> achievementRewards(Achievement achievement) {
        return achievementRewards.getOrDefault(achievement.name(), List.of());
    }
    public int historyRetentionDays() { return historyRetentionDays; }
    public int queryTimeoutSeconds() { return queryTimeoutSeconds; }
    public boolean webhookEnabled() {
        return webhookEnabled && (webhookUrl.startsWith("https://discord.com/api/webhooks/")
                || webhookUrl.startsWith("https://discordapp.com/api/webhooks/"));
    }
    public String webhookUrl() { return webhookUrl; }
    public long webhookMinPotCents() { return webhookMinPotCents; }
    public int webhookTimeoutSeconds() { return webhookTimeoutSeconds; }
    public long webhookMinIntervalMillis() { return webhookMinIntervalMillis; }
    public String titlePrefix() { return titlePrefix; }
    public boolean readableDefault() { return readableDefault; }
    public String headsMaterial() { return headsMaterial; }
    public String tailsMaterial() { return tailsMaterial; }
    public String borderMaterial() { return borderMaterial; }
    public String accentMaterial() { return accentMaterial; }
    public int mainSlot(String button) { return mainSlots.getOrDefault(button, -1); }
}
