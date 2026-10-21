package com.bx.smpPlugin.core;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

/**
 * The server's display name, set once in config.yml under BRANDING and used by the scoreboard,
 * the tab list, menus and messages.
 *
 * <p>This is only what players see. The plugin itself is always SmpPlugin: its jar, its plugin.yml
 * name, its commands and its permissions do not change with the brand.
 *
 * <p>Text can use {@code {server_name}} (or {@code %server_name%}), {@code {server_title}} for the
 * name in a bold gradient, and {@code {server_address}}.
 */
public final class Branding {

    public static final String DEFAULT_NAME = "CustomSMP";
    /** Deliberately not a real address; servers set their own. */
    public static final String DEFAULT_ADDRESS = "play.customsmp.example";
    public static final String DEFAULT_GRADIENT_FROM = "#0069D6";
    public static final String DEFAULT_GRADIENT_TO = "#1AC3F9";

    private static volatile Branding current = new Branding(DEFAULT_NAME, DEFAULT_ADDRESS,
            DEFAULT_GRADIENT_FROM, DEFAULT_GRADIENT_TO);

    private final String name;
    private final String address;
    private final String title;

    Branding(String name, String address, String gradientFrom, String gradientTo) {
        this.name = clean(name, DEFAULT_NAME);
        this.address = clean(address, DEFAULT_ADDRESS);
        this.title = gradient(this.name, parseRgb(gradientFrom, 0x0069D6), parseRgb(gradientTo, 0x1AC3F9));
    }

    /** Reads BRANDING from config.yml; a missing or blank value falls back to CustomSMP. */
    public static void load(ConfigurationSection config) {
        if (config == null) {
            current = new Branding(DEFAULT_NAME, DEFAULT_ADDRESS, DEFAULT_GRADIENT_FROM, DEFAULT_GRADIENT_TO);
            preserveSpelling(current);
            return;
        }
        current = new Branding(
                config.getString("BRANDING.SERVER-NAME", DEFAULT_NAME),
                config.getString("BRANDING.SERVER-ADDRESS", DEFAULT_ADDRESS),
                config.getString("BRANDING.GRADIENT-FROM", DEFAULT_GRADIENT_FROM),
                config.getString("BRANDING.GRADIENT-TO", DEFAULT_GRADIENT_TO));
        preserveSpelling(current);
    }

    /** Tells the title-casing used by the scoreboard to keep the brand's spelling. */
    private static void preserveSpelling(Branding brand) {
        java.util.List<String> words = new java.util.ArrayList<>();
        for (String word : brand.name.split("[^\\p{L}\\p{N}'`]+")) {
            if (!word.isEmpty()) {
                words.add(word);
            }
        }
        com.bx.smpPlugin.utils.ColorUtils.setPreservedWords(words);
    }

    public static String name() {
        return current.name;
    }

    public static String address() {
        return current.address;
    }

    /** The name in a bold left-to-right gradient, in {@code &#RRGGBB} colour codes. */
    public static String title() {
        return current.title;
    }

    /** Replaces the branding tokens in a line of text. Cheap when the text has none. */
    public static String apply(String text) {
        if (text == null || (text.indexOf('{') < 0 && text.indexOf('%') < 0)) {
            return text;
        }
        Branding brand = current;
        if (text.contains("server_title}")) {
            text = text.replace("{server_title}", brand.title);
        }
        if (text.contains("server_name")) {
            text = text.replace("{server_name}", brand.name)
                    .replace("%server_name%", brand.name)
                    .replace("%smp_server_name%", brand.name);
        }
        if (text.contains("server_address")) {
            text = text.replace("{server_address}", brand.address)
                    .replace("%server_address%", brand.address)
                    .replace("%smp_server_address%", brand.address);
        }
        return text;
    }

    static String gradient(String text, int from, int to) {
        int letters = text.codePointCount(0, text.length());
        StringBuilder builder = new StringBuilder(text.length() * 16);
        int index = 0;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint)) {
                builder.appendCodePoint(codePoint);
                index++;
                continue;
            }
            double amount = letters <= 1 ? 0D : (double) index / (letters - 1);
            builder.append(String.format(Locale.ROOT, "&#%06X&l", mix(from, to, amount)));
            builder.appendCodePoint(codePoint);
            index++;
        }
        return builder.toString();
    }

    private static int mix(int from, int to, double amount) {
        int red = (int) Math.round(((from >> 16) & 0xFF) + ((((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * amount));
        int green = (int) Math.round(((from >> 8) & 0xFF) + ((((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * amount));
        int blue = (int) Math.round((from & 0xFF) + (((to & 0xFF) - (from & 0xFF)) * amount));
        return (red << 16) | (green << 8) | blue;
    }

    static int parseRgb(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        String hex = value.trim();
        if (hex.startsWith("&#")) {
            hex = hex.substring(2);
        } else if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (!hex.matches("[0-9A-Fa-f]{6}")) {
            return fallback;
        }
        return Integer.parseInt(hex, 16);
    }

    private static String clean(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        // Colour codes belong in the gradient settings, not the name itself.
        String cleaned = value.replaceAll("(?i)&#[0-9a-f]{6}|[&§][0-9a-fk-or]", "").trim();
        return cleaned.isEmpty() ? fallback : cleaned;
    }
}
