// SmpPlugin by LowBacon
package com.bx.smpPlugin.utils;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class NumberUtils {

    public interface DurationFormatter {
        String formatTime(long totalSeconds);
        String formatTimeLong(long totalSeconds);
        String formatCountdown(long totalSeconds);
    }

    private static final DecimalFormat COMMA_FMT;
    private static final DecimalFormat SHORT_FMT;
    private static final String[] SHORT_SUFFIXES = {"", "K", "M", "B", "T", "Q"};
    private static volatile DurationFormatter durationFormatter;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        COMMA_FMT = new DecimalFormat("#,##0.##", symbols);
        SHORT_FMT = new DecimalFormat("#,##0.##", symbols);
    }

    public static String format(double number) {
        return COMMA_FMT.format(number);
    }

    public static String formatNice(double number) {
        if (!Double.isFinite(number)) {
            return "0";
        }

        double absolute = Math.abs(number);
        int suffixIndex = 0;

        while (absolute >= 1_000D && suffixIndex < SHORT_SUFFIXES.length - 1) {
            absolute /= 1_000D;
            suffixIndex++;
        }

        if (absolute >= 999.995D && suffixIndex < SHORT_SUFFIXES.length - 1) {
            absolute /= 1_000D;
            suffixIndex++;
        }

        String sign = number < 0D ? "-" : "";
        if (suffixIndex == 0) {
            return sign + COMMA_FMT.format(absolute);
        }

        return sign + SHORT_FMT.format(absolute) + SHORT_SUFFIXES[suffixIndex];
    }

    public static double parse(String input) {
        if (input == null || input.isBlank()) throw new NumberFormatException("Empty input");
        String clean = input.trim().replace(",", "").replace("_", "").toUpperCase(Locale.US);
        double multiplier = 1;
        if (clean.endsWith("Q")) { multiplier = 1_000_000_000_000_000D; clean = clean.substring(0, clean.length() - 1); }
        else if (clean.endsWith("T")) { multiplier = 1_000_000_000_000D; clean = clean.substring(0, clean.length() - 1); }
        else if (clean.endsWith("B")) { multiplier = 1_000_000_000; clean = clean.substring(0, clean.length() - 1); }
        else if (clean.endsWith("M")) { multiplier = 1_000_000; clean = clean.substring(0, clean.length() - 1); }
        else if (clean.endsWith("K")) { multiplier = 1_000;    clean = clean.substring(0, clean.length() - 1); }
        double value = Double.parseDouble(clean) * multiplier;
        // "NaN", "Infinity" and overflowing input parse fine in Java but compare false against
        // every bound ("amount < 1" lets NaN through), so they are refused here for every caller.
        if (!Double.isFinite(value)) {
            throw new NumberFormatException("Not a finite number: " + input);
        }
        return value;
    }

    public static boolean isValidPositiveAmount(String input) {
        try {
            return parse(input) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static void setDurationFormatter(DurationFormatter formatter) {
        durationFormatter = formatter;
    }

    public static String formatTime(long totalSeconds) {
        DurationFormatter formatter = durationFormatter;
        if (formatter != null) {
            return formatter.formatTime(totalSeconds);
        }
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

    public static String formatTimeLong(long totalSeconds) {
        DurationFormatter formatter = durationFormatter;
        if (formatter != null) {
            return formatter.formatTimeLong(totalSeconds);
        }
        long d = totalSeconds / 86400;
        long h = (totalSeconds % 86400) / 3600;
        long m = (totalSeconds % 3600) / 60;
        if (d > 0) return d + "d " + h + "h " + m + "m";
        return formatTime(totalSeconds);
    }

    public static String formatCountdown(long totalSeconds) {
        DurationFormatter formatter = durationFormatter;
        if (formatter != null) {
            return formatter.formatCountdown(totalSeconds);
        }
        long m = totalSeconds / 60;
        long s = totalSeconds % 60;
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

    public static long parseLong(String input) {
        return (long) parse(input);
    }
}
