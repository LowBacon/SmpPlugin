package com.bx.smpPlugin.events;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * When an event runs: from START up to (not including) END.
 *
 * <p>Dates are written {@code MM-dd} (every year, e.g. {@code 12-01}) or {@code yyyy-MM-dd} (one
 * year only), each optionally followed by a time {@code HH:mm}. A yearly window may wrap into the
 * next year ({@code 12-20} to {@code 01-06}). An END without a time means the start of that day.
 * Free of any server call so it can be tested.
 */
public record EventSchedule(Point start, Point end) {

    /** One end of the window. {@code year} is null for a date that repeats every year. */
    public record Point(Integer year, MonthDay day, LocalTime time) {

        LocalDateTime in(int calendarYear) {
            int y = year == null ? calendarYear : year;
            // 02-29 in a year without it falls back to 02-28.
            LocalDate date = day.isValidYear(y) ? day.atYear(y) : LocalDate.of(y, day.getMonth(), 28);
            return date.atTime(time);
        }

        @Override
        public String toString() {
            String date = (year == null ? "" : year + "-") + String.format(Locale.ROOT, "%02d-%02d",
                    day.getMonthValue(), day.getDayOfMonth());
            return time.equals(LocalTime.MIDNIGHT) ? date : date + " " + time;
        }
    }

    private static final Pattern FORMAT = Pattern.compile(
            "^(?:(\\d{4})-)?(\\d{1,2})-(\\d{1,2})(?:[ T](\\d{1,2}):(\\d{2}))?$");

    /** Reads one date; empty when it is not in one of the accepted forms. */
    public static Optional<Point> parsePoint(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        Matcher matcher = FORMAT.matcher(raw.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            Integer year = matcher.group(1) == null ? null : Integer.parseInt(matcher.group(1));
            MonthDay day = MonthDay.of(Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
            LocalTime time = matcher.group(4) == null ? LocalTime.MIDNIGHT
                    : LocalTime.of(Integer.parseInt(matcher.group(4)), Integer.parseInt(matcher.group(5)));
            if (year != null && !day.isValidYear(year)) {
                return Optional.empty();
            }
            return Optional.of(new Point(year, day, time));
        } catch (DateTimeException | NumberFormatException exception) {
            return Optional.empty();
        }
    }

    /** Both dates, or empty when either is missing or malformed. */
    public static Optional<EventSchedule> parse(String start, String end) {
        Optional<Point> from = parsePoint(start);
        Optional<Point> to = parsePoint(end);
        if (from.isEmpty() || to.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new EventSchedule(from.get(), to.get()));
    }

    private boolean yearly() {
        return start.year() == null && end.year() == null;
    }

    /** The window that contains {@code now}, or the next one to start. */
    private LocalDateTime[] window(LocalDateTime now) {
        if (!yearly()) {
            int startYear = start.year() != null ? start.year() : (end.year() != null ? end.year() : now.getYear());
            LocalDateTime from = start.in(startYear);
            LocalDateTime to = end.in(end.year() != null ? end.year() : startYear);
            if (!to.isAfter(from)) {
                to = end.in(startYear + 1);
            }
            return new LocalDateTime[]{from, to};
        }
        // A yearly window: the one that started last year (if it wraps) can still be running.
        for (int year = now.getYear() - 1; year <= now.getYear() + 1; year++) {
            LocalDateTime from = start.in(year);
            LocalDateTime to = end.in(year);
            if (!to.isAfter(from)) {
                to = end.in(year + 1);
            }
            if (now.isBefore(to)) {
                return new LocalDateTime[]{from, to};
            }
        }
        return new LocalDateTime[]{start.in(now.getYear() + 1), end.in(now.getYear() + 1)};
    }

    public boolean contains(LocalDateTime now) {
        LocalDateTime[] window = window(now);
        return !now.isBefore(window[0]) && now.isBefore(window[1]);
    }

    /** Seconds until the window starts (0 while it runs), or -1 when a one-off window is over. */
    public long secondsUntilStart(LocalDateTime now) {
        LocalDateTime[] window = window(now);
        if (contains(now)) {
            return 0L;
        }
        if (!now.isBefore(window[1])) {
            return -1L;
        }
        return Math.max(0L, ChronoUnit.SECONDS.between(now, window[0]));
    }

    /** Seconds until the running window ends, or -1 when it is not running. */
    public long secondsUntilEnd(LocalDateTime now) {
        if (!contains(now)) {
            return -1L;
        }
        return Math.max(0L, ChronoUnit.SECONDS.between(now, window(now)[1]));
    }

    /** The year the current (or next) window started in, for keeping progress per season. */
    public int seasonYear(LocalDateTime now) {
        return window(now)[0].getYear();
    }

    @Override
    public String toString() {
        return start + " to " + end;
    }
}
