package com.bx.smpPlugin.engagement;

/**
 * A player's daily-reward streak, stored as {@code lastDay;streak;total} where {@code lastDay} is
 * the epoch day of the last claim. Pure arithmetic so it can be tested.
 */
public record DailyStreak(long lastDay, int streak, int total) {

    public static final DailyStreak NEW = new DailyStreak(Long.MIN_VALUE, 0, 0);

    public static DailyStreak parse(String stored) {
        if (stored == null || stored.isBlank()) {
            return NEW;
        }
        String[] parts = stored.split(";");
        try {
            return new DailyStreak(Long.parseLong(parts[0]), Integer.parseInt(parts[1]),
                    parts.length > 2 ? Integer.parseInt(parts[2]) : 0);
        } catch (RuntimeException ignored) {
            return NEW;
        }
    }

    public String serialize() {
        return lastDay + ";" + streak + ";" + total;
    }

    public boolean canClaim(long today) {
        return lastDay == Long.MIN_VALUE || today > lastDay;
    }

    /** The streak the player would have after claiming today. */
    public int nextStreak(long today, int graceDays) {
        if (lastDay == Long.MIN_VALUE) {
            return 1;
        }
        long gap = today - lastDay;
        return gap >= 1 && gap <= 1L + Math.max(0, graceDays) ? streak + 1 : 1;
    }

    /** The streak as it stands today: 0 once it has been broken. */
    public int currentStreak(long today, int graceDays) {
        if (lastDay == Long.MIN_VALUE) {
            return 0;
        }
        return today - lastDay <= 1L + Math.max(0, graceDays) ? streak : 0;
    }

    public DailyStreak claim(long today, int graceDays) {
        return new DailyStreak(today, nextStreak(today, graceDays), total + 1);
    }

    /** Which of the 7 reward days a streak lands on (1..7). */
    public static int rewardDay(int streak) {
        return ((Math.max(1, streak) - 1) % 7) + 1;
    }

    /** Full weeks completed before this streak day (0 in the first week). */
    public static int weeksDone(int streak) {
        return (Math.max(1, streak) - 1) / 7;
    }
}
