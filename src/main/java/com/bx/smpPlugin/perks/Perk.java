// SmpPlugin by LowBacon
package com.bx.smpPlugin.perks;

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

    public String key() {
        return key;
    }

    public String description() {
        return description;
    }

    public String unit() {
        return unit;
    }

    public int hardCap() {
        return hardCap;
    }
}
