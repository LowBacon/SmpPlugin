package com.bx.smpPlugin.managers;

import java.util.Locale;

/** The pure parts of the tab list's look: the shining title, colours and sort order. */
final class TablistEffects {

    private TablistEffects() {
    }

    /**
     * Colours each letter of {@code text} in {@code base}, with a band of {@code shine} that sweeps
     * across it from left to right as {@code frame} counts up, pausing a little between sweeps.
     */
    static String shimmer(String text, int base, int shine, long frame, boolean bold) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        int width = 3;
        int cycle = text.length() + width * 2 + 8;
        double center = Math.floorMod(frame, cycle) - width;
        StringBuilder out = new StringBuilder(text.length() * 10);
        for (int i = 0; i < text.length(); i++) {
            double amount = Math.max(0D, 1D - Math.abs(i - center) / width);
            out.append("&#").append(String.format("%06X", mix(base, shine, amount)));
            if (bold) {
                out.append("&l");
            }
            out.append(text.charAt(i));
        }
        return out.toString();
    }

    /**
     * {@code length} copies of {@code character}, fading from {@code from} to {@code to} along the
     * bar, with a {@code shine} band sweeping across it as {@code frame} counts up.
     */
    static String gradientLine(String character, int length, int from, int to, int shine, long frame) {
        if (character == null || character.isEmpty() || length <= 0) {
            return "";
        }
        int width = 4;
        int cycle = length + width * 2 + 10;
        double center = Math.floorMod(frame, cycle) - width;
        StringBuilder out = new StringBuilder(length * (character.length() + 8));
        for (int i = 0; i < length; i++) {
            int base = mix(from, to, length == 1 ? 0D : i / (double) (length - 1));
            double amount = Math.max(0D, 1D - Math.abs(i - center) / width) * 0.85D;
            out.append("&#").append(String.format("%06X", mix(base, shine, amount))).append(character);
        }
        return out.toString();
    }

    static int mix(int from, int to, double amount) {
        int r = (int) Math.round(((from >> 16) & 0xFF) + ((((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * amount));
        int g = (int) Math.round(((from >> 8) & 0xFF) + ((((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * amount));
        int b = (int) Math.round((from & 0xFF) + (((to & 0xFF) - (from & 0xFF)) * amount));
        return (r << 16) | (g << 8) | b;
    }

    static int parseRgb(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        String hex = value.trim().replace("&#", "").replace("#", "");
        try {
            return hex.length() == 6 ? Integer.parseInt(hex, 16) : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    /** Higher comes first. Active players always sit above AFK ones, then by rank weight. */
    static int listOrder(int rankWeight, boolean afk) {
        int weight = Math.max(0, Math.min(99_999, rankWeight));
        return afk ? weight : 100_000 + weight;
    }

    static String colorPing(int ping) {
        String color = ping < 80 ? "&a" : ping < 160 ? "&e" : ping < 300 ? "&6" : "&c";
        return color + ping + "ms";
    }

    static String colorTps(double tps) {
        double shown = Math.min(20D, Math.max(0D, tps));
        String color = shown >= 19.5D ? "&a" : shown >= 17D ? "&e" : "&c";
        return color + String.format(Locale.ROOT, "%.1f", shown);
    }
}
