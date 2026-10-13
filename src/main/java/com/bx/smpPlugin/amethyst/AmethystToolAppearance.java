package com.bx.smpPlugin.amethyst;

import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.NumberUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Shop identity for a granted amethyst tool: the Shards Shop tooltip, without the price line,
 * and with a live {@code {time}} placeholder on the Self Destruct line.
 */
public record AmethystToolAppearance(String name, List<String> loreTemplate, List<String> enchantments) {

    private static final Pattern PRICE_LINE = Pattern.compile(".*\\d.*shards.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern DURATION_TOKEN = Pattern.compile(
            "(?i)\\d+d(?:\\s+\\d+h)?(?:\\s+\\d+m)?(?:\\s+\\d+s)?"
                    + "|\\d+h(?:\\s+\\d+m)?(?:\\s+\\d+s)?"
                    + "|\\d+m(?:\\s+\\d+s)?"
    );

    public AmethystToolAppearance {
        loreTemplate = loreTemplate == null ? List.of() : List.copyOf(loreTemplate);
        enchantments = enchantments == null ? List.of() : List.copyOf(enchantments);
    }

    public static AmethystToolAppearance fromShop(String displayName, List<String> shopLore, List<String> enchantments) {
        return new AmethystToolAppearance(displayName, ownedLoreTemplate(shopLore), enchantments);
    }

    public boolean hasName() {
        return name != null && !name.isBlank();
    }

    public boolean hasLore() {
        return loreTemplate != null && !loreTemplate.isEmpty();
    }

    public boolean hasEnchantments() {
        return enchantments != null && !enchantments.isEmpty();
    }

    public static List<String> ownedLoreTemplate(List<String> shopLore) {
        List<String> owned = new ArrayList<>();
        for (String line : stripPriceLines(shopLore)) {
            owned.add(injectTimePlaceholder(line));
        }
        return List.copyOf(owned);
    }

    /**
     * Shop lore as it should appear on a purchased item: same text, without the price line.
     * Timed tools then replace Self Destruct durations with {@code {time}}.
     */
    public static List<String> stripPriceLines(List<String> shopLore) {
        if (shopLore == null || shopLore.isEmpty()) {
            return List.of();
        }
        List<String> owned = new ArrayList<>();
        for (String line : shopLore) {
            if (line == null || isPriceLine(line)) {
                continue;
            }
            owned.add(line);
        }
        return List.copyOf(owned);
    }

    public static List<String> resolveLore(List<String> template, long remainingSeconds) {
        if (template == null || template.isEmpty()) {
            return List.of();
        }
        String time = remainingSeconds > 0L ? NumberUtils.formatTimeLong(remainingSeconds) : "&cexpired";
        List<String> resolved = new ArrayList<>(template.size());
        for (String line : template) {
            resolved.add(line == null ? "" : line.replace("{time}", time));
        }
        return resolved;
    }

    public static String encodeTemplate(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        return String.join("\n", lines);
    }

    public static List<String> decodeTemplate(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return List.of();
        }
        return List.of(encoded.split("\n", -1));
    }

    static boolean isPriceLine(String line) {
        String stripped = ColorUtils.strip(line).trim();
        if (stripped.isEmpty()) {
            return false;
        }
        String lower = stripped.toLowerCase(Locale.US);
        if (lower.contains("self destruct")) {
            return false;
        }
        return PRICE_LINE.matcher(lower).matches();
    }

    static String injectTimePlaceholder(String line) {
        if (line.contains("{time}")) {
            return line;
        }
        String stripped = ColorUtils.strip(line);
        if (!stripped.toLowerCase(Locale.US).contains("self destruct")) {
            return line;
        }
        return DURATION_TOKEN.matcher(line).replaceFirst("{time}");
    }
}
