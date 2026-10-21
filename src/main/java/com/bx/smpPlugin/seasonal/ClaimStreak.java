package com.bx.smpPlugin.seasonal;

/**
 * A player's calendar streak for one season: days in a row with at least one door opened, the best run, which
 * streak bonuses were already given and whether the completion prize was. Stored as
 * {@code lastDay;streak;best;milestones;completed}. Pure arithmetic so it can be tested.
 */
public record ClaimStreak(long lastDay, int streak, int best, long milestones, boolean completed) {

    public static final ClaimStreak NEW = new ClaimStreak(Long.MIN_VALUE, 0, 0, 0L, false);

    public static ClaimStreak parse(String stored) {
        if (stored == null || stored.isBlank()) {
            return NEW;
        }
        String[] parts = stored.split(";");
        try {
            return new ClaimStreak(Long.parseLong(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                    Long.parseLong(parts[3]), "1".equals(parts[4]));
        } catch (RuntimeException ignored) {
            return NEW;
        }
    }

    public String serialize() {
        return lastDay + ";" + streak + ";" + best + ";" + milestones + ";" + (completed ? "1" : "0");
    }

    /**
     * The streak after opening a door on {@code day}. A second door the same day keeps it; the next day adds one;
     * a gap longer than {@code graceDays} starts again at one.
     */
    public ClaimStreak claimOn(long day, int graceDays) {
        int next;
        if (lastDay == Long.MIN_VALUE) {
            next = 1;
        } else if (day <= lastDay) {
            // Same day (or a test date before the last claim): the streak stands.
            return this;
        } else if (day - lastDay <= 1L + Math.max(0, graceDays)) {
            next = streak + 1;
        } else {
            next = 1;
        }
        return new ClaimStreak(day, next, Math.max(best, next), milestones, completed);
    }

    /** The streak as it stands today: 0 once a gap longer than the grace days has passed. */
    public int current(long today, int graceDays) {
        if (lastDay == Long.MIN_VALUE) {
            return 0;
        }
        return today - lastDay <= 1L + Math.max(0, graceDays) ? streak : 0;
    }

    public boolean hasMilestone(int length) {
        return length >= 1 && length <= 62 && (milestones & (1L << length)) != 0L;
    }

    public ClaimStreak withMilestone(int length) {
        if (length < 1 || length > 62) {
            return this;
        }
        return new ClaimStreak(lastDay, streak, best, milestones | (1L << length), completed);
    }

    public ClaimStreak withCompleted() {
        return new ClaimStreak(lastDay, streak, best, milestones, true);
    }
}
