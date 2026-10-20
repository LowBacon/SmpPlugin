package com.bx.smpPlugin.perks;

/**
 * What a rank can add. Every perk is a whole number with a hard limit in code; perks.yml CAPS can only lower it.
 * None of them touches gambling odds, prices, anti-cheat, moderation or anything another player loses out on.
 */
public enum Perk {
    CALENDAR_BONUS_PERCENT("CALENDAR-BONUS-PERCENT", "more money and shards behind Advent and Halloween doors", "%", 100),
    CALENDAR_STREAK_GRACE_DAYS("CALENDAR-STREAK-GRACE-DAYS", "days a calendar streak survives a missed day", " day(s)", 3),
    DAILY_BONUS_PERCENT("DAILY-BONUS-PERCENT", "more money and shards from /daily", "%", 100),
    TRICK_OR_TREAT_COOLDOWN_CUT_PERCENT("TRICK-OR-TREAT-COOLDOWN-CUT-PERCENT", "shorter wait between Trick or Treat knocks",
            "%", 50),
    TRICK_OR_TREAT_EXTRA_KNOCKS("TRICK-OR-TREAT-EXTRA-KNOCKS", "extra Trick or Treat knocks a day", "", 10),
    CANDY_BONUS_PERCENT("CANDY-BONUS-PERCENT", "more Halloween candy", "%", 100);

    private final String key;
    private final String description;
    private final String unit;
    private final int hardCap;

    Perk(String key, String description, String unit, int hardCap) {
        this.key = key;
        this.description = description;
        this.unit = unit;
        this.hardCap = hardCap;
    }

    /** The key in perks.yml. */
    public String key() {
        return key;
    }

    public String description() {
        return description;
    }

    public String unit() {
        return unit;
    }

    /** The most this perk can ever be, whatever perks.yml says. */
    public int hardCap() {
        return hardCap;
    }
}
