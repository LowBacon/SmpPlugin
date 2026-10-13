package com.bx.smpPlugin.models;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Auction search words. A word has to appear in the item name, so {@code neth} matches
 * netherite and {@code boot} matches boots. An enchant abbreviation followed by a level,
 * as in {@code prot 4} or {@code prot4}, keeps items that have that enchant at that level
 * or higher.
 */
public final class AuctionIntelligentSearch {

    public record EnchantNeed(String enchantKey, int minimumLevel) {
    }

    private record Parsed(List<String> nameTokens, List<EnchantNeed> enchants) {
    }

    private static final Map<String, String> ENCHANT_ALIASES = Map.ofEntries(
            Map.entry("prot", "protection"),
            Map.entry("protection", "protection"),
            Map.entry("sharp", "sharpness"),
            Map.entry("sharpness", "sharpness"),
            Map.entry("unb", "unbreaking"),
            Map.entry("unbreaking", "unbreaking"),
            Map.entry("eff", "efficiency"),
            Map.entry("efficiency", "efficiency"),
            Map.entry("fort", "fortune"),
            Map.entry("fortune", "fortune"),
            Map.entry("silk", "silk_touch"),
            Map.entry("silktouch", "silk_touch"),
            Map.entry("mend", "mending"),
            Map.entry("mending", "mending"),
            Map.entry("fireaspect", "fire_aspect"),
            Map.entry("fa", "fire_aspect"),
            Map.entry("blast", "blast_protection"),
            Map.entry("blastprot", "blast_protection"),
            Map.entry("fireprot", "fire_protection"),
            Map.entry("firep", "fire_protection"),
            Map.entry("projprot", "projectile_protection"),
            Map.entry("projectileprot", "projectile_protection"),
            Map.entry("thorns", "thorns"),
            Map.entry("feather", "feather_falling"),
            Map.entry("ff", "feather_falling"),
            Map.entry("depth", "depth_strider"),
            Map.entry("depthstrider", "depth_strider"),
            Map.entry("resp", "respiration"),
            Map.entry("respiration", "respiration"),
            Map.entry("aqua", "aqua_affinity"),
            Map.entry("aquaaffinity", "aqua_affinity"),
            Map.entry("loot", "looting"),
            Map.entry("looting", "looting"),
            Map.entry("power", "power"),
            Map.entry("punch", "punch"),
            Map.entry("flame", "flame"),
            Map.entry("inf", "infinity"),
            Map.entry("infinity", "infinity"),
            Map.entry("knock", "knockback"),
            Map.entry("knockback", "knockback"),
            Map.entry("kb", "knockback"),
            Map.entry("sweep", "sweeping_edge"),
            Map.entry("sweeping", "sweeping_edge"),
            Map.entry("smite", "smite"),
            Map.entry("bane", "bane_of_arthropods"),
            Map.entry("impaling", "impaling"),
            Map.entry("loyalty", "loyalty"),
            Map.entry("riptide", "riptide"),
            Map.entry("channeling", "channeling"),
            Map.entry("multishot", "multishot"),
            Map.entry("piercing", "piercing"),
            Map.entry("quickcharge", "quick_charge"),
            Map.entry("qc", "quick_charge"),
            Map.entry("soulspeed", "soul_speed"),
            Map.entry("swiftsneak", "swift_sneak"),
            Map.entry("density", "density"),
            Map.entry("breach", "breach"),
            Map.entry("windburst", "wind_burst")
    );

    private static final Map<String, Integer> ROMAN_LEVELS = Map.ofEntries(
            Map.entry("i", 1),
            Map.entry("ii", 2),
            Map.entry("iii", 3),
            Map.entry("iv", 4),
            Map.entry("v", 5),
            Map.entry("vi", 6),
            Map.entry("vii", 7),
            Map.entry("viii", 8),
            Map.entry("ix", 9),
            Map.entry("x", 10)
    );

    private AuctionIntelligentSearch() {
    }

    public static boolean matches(String query, String materialName, String displayName, Map<String, Integer> enchantLevels) {
        Parsed parsed = parse(query);
        if (parsed.nameTokens().isEmpty() && parsed.enchants().isEmpty()) {
            return true;
        }
        String haystack = ((materialName == null ? "" : materialName) + " " + (displayName == null ? "" : displayName))
                .toLowerCase(Locale.ROOT)
                .replace('_', ' ');
        for (String token : parsed.nameTokens()) {
            if (!haystack.contains(token)) {
                return false;
            }
        }
        Map<String, Integer> levels = enchantLevels == null ? Map.of() : enchantLevels;
        for (EnchantNeed need : parsed.enchants()) {
            Integer level = levels.get(need.enchantKey());
            if (level == null || level < need.minimumLevel()) {
                return false;
            }
        }
        return true;
    }

    public static boolean needsEnchantLevels(String query) {
        return !parse(query).enchants().isEmpty();
    }

    static Parsed parse(String query) {
        if (query == null || query.isBlank()) {
            return new Parsed(List.of(), List.of());
        }
        String[] tokens = query.trim().toLowerCase(Locale.ROOT).split("\\s+");
        List<String> names = new ArrayList<>();
        List<EnchantNeed> enchants = new ArrayList<>();
        for (int index = 0; index < tokens.length; index++) {
            String token = tokens[index];
            GluedLevel glued = gluedLevel(token);
            if (glued != null) {
                enchants.add(new EnchantNeed(glued.enchantKey(), glued.level()));
                continue;
            }
            String enchantKey = ENCHANT_ALIASES.get(token);
            if (enchantKey != null && index + 1 < tokens.length) {
                Integer level = level(tokens[index + 1]);
                if (level != null) {
                    enchants.add(new EnchantNeed(enchantKey, level));
                    index++;
                    continue;
                }
            }
            names.add(token);
        }
        return new Parsed(List.copyOf(names), List.copyOf(enchants));
    }

    private static GluedLevel gluedLevel(String token) {
        int split = token.length();
        while (split > 0 && Character.isDigit(token.charAt(split - 1))) {
            split--;
        }
        if (split == 0 || split == token.length()) {
            return null;
        }
        String alias = token.substring(0, split);
        String enchantKey = ENCHANT_ALIASES.get(alias);
        Integer level = level(token.substring(split));
        if (enchantKey == null || level == null) {
            return null;
        }
        return new GluedLevel(enchantKey, level);
    }

    private static Integer level(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Integer roman = ROMAN_LEVELS.get(token);
        if (roman != null) {
            return roman;
        }
        try {
            int parsed = Integer.parseInt(token);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record GluedLevel(String enchantKey, int level) {
    }
}
