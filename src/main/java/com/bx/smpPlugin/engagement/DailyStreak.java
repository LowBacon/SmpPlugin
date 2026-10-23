// SmpPlugin by LowBacon
package com.bx.smpPlugin.engagement;

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

    public int nextStreak(long today, int graceDays) {
        if (lastDay == Long.MIN_VALUE) {
            return 1;
        }
        long gap = today - lastDay;
        return gap >= 1 && gap <= 1L + Math.max(0, graceDays) ? streak + 1 : 1;
    }

    public int currentStreak(long today, int graceDays) {
        if (lastDay == Long.MIN_VALUE) {
            return 0;
        }
        return today - lastDay <= 1L + Math.max(0, graceDays) ? streak : 0;
    }

    public DailyStreak claim(long today, int graceDays) {
        return new DailyStreak(today, nextStreak(today, graceDays), total + 1);
    }

    public static int rewardDay(int streak) {
        return ((Math.max(1, streak) - 1) % 7) + 1;
    }

    public static int weeksDone(int streak) {
        return (Math.max(1, streak) - 1) / 7;
    }
}
