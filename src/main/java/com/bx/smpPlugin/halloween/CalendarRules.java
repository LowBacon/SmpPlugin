package com.bx.smpPlugin.halloween;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * The date rules of the Spooky Calendar, kept free of any server call so they can be tested.
 *
 * <p>Doors are numbered by their day of the month. A door is {@link DoorState#OPEN} when it may be
 * claimed now, {@link DoorState#LOCKED} before its day, {@link DoorState#MISSED} once it can no
 * longer be claimed, and {@link DoorState#CLAIMED} once opened.
 */
public record CalendarRules(int month, int firstDay, int lastDay, Mode mode, boolean catchUp, int graceDays) {

    public enum Mode {
        /** Door N opens on day N. */
        DAILY,
        /** Every door opens on the last day. */
        ALL_ON_LAST_DAY;

        static Mode parse(String raw) {
            if (raw == null) {
                return DAILY;
            }
            String key = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            return "ALL_ON_LAST_DAY".equals(key) || "ALL_ON_HALLOWEEN".equals(key) ? ALL_ON_LAST_DAY : DAILY;
        }
    }

    public enum DoorState {
        OPEN, LOCKED, CLAIMED, MISSED
    }

    public CalendarRules {
        month = Math.max(1, Math.min(12, month));
        firstDay = Math.max(1, Math.min(31, firstDay));
        lastDay = Math.max(firstDay, Math.min(31, lastDay));
        mode = mode == null ? Mode.DAILY : mode;
        graceDays = Math.max(0, graceDays);
    }

    public static CalendarRules of(int month, int firstDay, int lastDay, String mode, boolean catchUp, int graceDays) {
        return new CalendarRules(month, firstDay, lastDay, Mode.parse(mode), catchUp, graceDays);
    }

    /** The last day of the season in that year, clamped to the length of the month. */
    public int lastDayIn(int year) {
        return Math.min(lastDay, YearMonth.of(year, month).lengthOfMonth());
    }

    public int doorCount(int year) {
        return lastDayIn(year) - firstDay + 1;
    }

    public LocalDate openDate(int door, int year) {
        int day = mode == Mode.ALL_ON_LAST_DAY ? lastDayIn(year) : door;
        return LocalDate.of(year, month, Math.min(day, YearMonth.of(year, month).lengthOfMonth()));
    }

    public LocalDate seasonStart(int year) {
        return LocalDate.of(year, month, firstDay);
    }

    /** The last date a door can be claimed (the last day plus the grace days). */
    public LocalDate claimDeadline(int year) {
        return LocalDate.of(year, month, lastDayIn(year)).plusDays(graceDays);
    }

    /** True from the first door's day to the claim deadline. */
    public boolean inSeason(LocalDate today) {
        int year = seasonYear(today);
        return !today.isBefore(seasonStart(year)) && !today.isAfter(claimDeadline(year));
    }

    /**
     * The year whose calendar applies today. Grace days can run into the next month (or, for a
     * December season, the next year); dates inside them still belong to that season.
     */
    public int seasonYear(LocalDate today) {
        int year = today.getYear();
        LocalDate lastYearsDeadline = claimDeadline(year - 1);
        if (lastYearsDeadline.getYear() == year && !today.isAfter(lastYearsDeadline)) {
            return year - 1;
        }
        return year;
    }

    public boolean isDoor(int door, int year) {
        return door >= firstDay && door <= lastDayIn(year);
    }

    public DoorState state(int door, LocalDate today, boolean claimed) {
        if (claimed) {
            return DoorState.CLAIMED;
        }
        int year = seasonYear(today);
        if (!isDoor(door, year)) {
            return DoorState.MISSED;
        }
        LocalDate opens = openDate(door, year);
        if (today.isBefore(opens)) {
            return DoorState.LOCKED;
        }
        if (today.isAfter(claimDeadline(year))) {
            return DoorState.MISSED;
        }
        if (today.isEqual(opens)) {
            return DoorState.OPEN;
        }
        // Past its own day: open only while catching up, or inside the grace days after the season.
        boolean inGrace = today.isAfter(LocalDate.of(year, month, lastDayIn(year)));
        return catchUp || (inGrace && opens.isEqual(LocalDate.of(year, month, lastDayIn(year))))
                ? DoorState.OPEN
                : DoorState.MISSED;
    }

    /** Seconds until a door opens, or 0 when it already has. */
    public long secondsUntilOpen(int door, ZonedDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalDate opens = openDate(door, seasonYear(today));
        if (!today.isBefore(opens)) {
            return 0L;
        }
        ZonedDateTime start = opens.atStartOfDay(now.getZone());
        return Math.max(0L, ChronoUnit.SECONDS.between(now, start));
    }

    /** Seconds until the next season starts (0 while it is running). */
    public long secondsUntilSeason(ZonedDateTime now) {
        LocalDate today = now.toLocalDate();
        if (inSeason(today)) {
            return 0L;
        }
        int year = today.getYear();
        LocalDate start = seasonStart(year);
        if (today.isAfter(claimDeadline(year))) {
            start = seasonStart(year + 1);
        }
        return Math.max(0L, ChronoUnit.SECONDS.between(now, start.atStartOfDay(now.getZone())));
    }

    /** Seconds until the last day (Halloween itself), 0 on or after it. */
    public long secondsUntilLastDay(ZonedDateTime now) {
        LocalDate today = now.toLocalDate();
        int year = seasonYear(today);
        LocalDate last = LocalDate.of(year, month, lastDayIn(year));
        if (!today.isBefore(last)) {
            return 0L;
        }
        return Math.max(0L, ChronoUnit.SECONDS.between(now, last.atStartOfDay(now.getZone())));
    }

    public static ZoneId zone(String configured) {
        if (configured == null || configured.isBlank()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(configured.trim());
        } catch (RuntimeException ignored) {
            return ZoneId.systemDefault();
        }
    }

    // ---------------------------------------------------------------- claimed doors as a bit mask

    public static boolean isClaimed(long mask, int door) {
        return door >= 1 && door <= 62 && (mask & (1L << door)) != 0L;
    }

    public static long withClaimed(long mask, int door) {
        return door >= 1 && door <= 62 ? mask | (1L << door) : mask;
    }

    public static long withoutClaimed(long mask, int door) {
        return door >= 1 && door <= 62 ? mask & ~(1L << door) : mask;
    }

    public static long parseMask(String stored) {
        if (stored == null || stored.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(stored.trim());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
