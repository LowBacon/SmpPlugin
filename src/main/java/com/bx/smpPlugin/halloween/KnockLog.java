package com.bx.smpPlugin.halloween;

/**
 * A player's Trick or Treat knocks: how many today (by epoch day) and in the whole season. Stored as
 * {@code day;today;total}. Pure, so it can be tested.
 */
public record KnockLog(long day, int today, int total) {

    public static final KnockLog NEW = new KnockLog(Long.MIN_VALUE, 0, 0);

    public static KnockLog parse(String stored) {
        if (stored == null || stored.isBlank()) {
            return NEW;
        }
        String[] parts = stored.split(";");
        try {
            return new KnockLog(Long.parseLong(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (RuntimeException ignored) {
            return NEW;
        }
    }

    public String serialize() {
        return day + ";" + today + ";" + total;
    }

    /** Knocks used on {@code epochDay}. */
    public int usedOn(long epochDay) {
        return epochDay == day ? today : 0;
    }

    /** True when another knock fits in the daily limit ({@code limit <= 0}: no limit). */
    public boolean canKnock(long epochDay, int limit) {
        return limit <= 0 || usedOn(epochDay) < limit;
    }

    public KnockLog knock(long epochDay) {
        return new KnockLog(epochDay, usedOn(epochDay) + 1, total + 1);
    }
}
