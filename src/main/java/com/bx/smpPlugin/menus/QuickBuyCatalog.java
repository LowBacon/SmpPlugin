// SmpPlugin by LowBacon
package com.bx.smpPlugin.menus;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.WorthManager;
import com.bx.smpPlugin.models.QuickBuyEntry;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class QuickBuyCatalog {

    private QuickBuyCatalog() {
    }

    public record EnchantmentSpec(
            String key,
            Enchantment enchantment,
            String displayName,
            boolean isCurse,
            int maxLevel
    ) {}

    public static List<Material> getSelectableMaterials(SmpPlugin plugin, String query) {
        return getSelectableMaterials(plugin, query, ChooseItemBlacklist.configuredEntries(plugin));
    }

    public static List<Material> getSelectableMaterials(
            SmpPlugin plugin,
            String query,
            Collection<String> shopBlacklist
    ) {
        Collection<String> extras = shopBlacklist == null ? List.of() : shopBlacklist;
        String mode = "VANILLA";
        int maxItems = 2000;
        if (plugin != null && plugin.getConfigManager() != null && plugin.getConfigManager().getShop() != null) {
            mode = plugin.getConfigManager().getShop()
                    .getString("QUICK-BUY.CHOOSE-ITEM.MODE", "VANILLA")
                    .trim().toUpperCase(Locale.ROOT);
            maxItems = plugin.getConfigManager().getShop()
                    .getInt("QUICK-BUY.CHOOSE-ITEM.MAX-ITEMS", 2000);
        }
        if (maxItems <= 0) {
            maxItems = Integer.MAX_VALUE;
        }

        List<Material> list = new ArrayList<>();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

        if ("CUSTOM".equals(mode) && plugin != null && plugin.getWorthManager() != null) {
            Set<Material> seen = new HashSet<>();
            for (WorthManager.WorthBrowserEntry entry : plugin.getWorthManager().getBrowserEntries()) {
                Material mat = entry.material();
                if (mat == null || mat.isAir() || mat.isLegacy() || mat.name().startsWith("LEGACY_")) {
                    continue;
                }
                if (ChooseItemBlacklist.isBlacklisted(mat, extras)) {
                    continue;
                }
                if (!seen.add(mat)) {
                    continue;
                }
                String name = plugin.getWorthManager().prettifyMaterial(mat).toLowerCase(Locale.ROOT);
                if (q.isBlank() || name.contains(q) || mat.name().toLowerCase(Locale.ROOT).contains(q)) {
                    list.add(mat);
                }
            }
        } else {
            // Default: VANILLA directly from vanilla Minecraft matching server version
            for (Material mat : Material.values()) {
                if (mat.isLegacy() || mat.name().startsWith("LEGACY_") || isAir(mat)) {
                    continue;
                }
                if (!isItem(mat) || ChooseItemBlacklist.isBlacklisted(mat, extras)) {
                    continue;
                }
                String name = plugin != null && plugin.getWorthManager() != null
                        ? plugin.getWorthManager().prettifyMaterial(mat).toLowerCase(Locale.ROOT)
                        : mat.name().toLowerCase(Locale.ROOT);
                if (q.isBlank() || name.contains(q) || mat.name().toLowerCase(Locale.ROOT).contains(q)) {
                    list.add(mat);
                }
            }
        }

        if (plugin != null && plugin.getWorthManager() != null) {
            list.sort(Comparator.comparing(m -> plugin.getWorthManager().prettifyMaterial(m)));
        } else {
            list.sort(Comparator.comparing(Enum::name));
        }

        if (list.size() > maxItems) {
            return new ArrayList<>(list.subList(0, maxItems));
        }
        return list;
    }

    public static boolean isBlacklisted(SmpPlugin plugin, Material material) {
        return ChooseItemBlacklist.isBlacklisted(plugin, material);
    }

    private static boolean hasRegistry() {
        try {
            return org.bukkit.Bukkit.getServer() != null && Material.STONE.isBlock();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isAir(Material mat) {
        if (mat == null || mat == Material.AIR) {
            return true;
        }
        return mat.name().endsWith("_AIR");
    }

    private static final Set<String> KNOWN_TEXTURES = new HashSet<>();

    static {
        try (var in = QuickBuyCatalog.class.getResourceAsStream("/minecraft_textures.txt")) {
            if (in != null) {
                try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty()) {
                            KNOWN_TEXTURES.add(line);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static boolean isBlock(Material mat) {
        if (mat == null) {
            return false;
        }
        if (hasRegistry()) {
            try {
                return mat.isBlock();
            } catch (Throwable ignored) {
            }
        }
        String name = mat.name().toLowerCase(Locale.ROOT);
        if (KNOWN_TEXTURES.contains("item/" + name)) {
            return false;
        }
        return true;
    }

    public static boolean isItem(Material mat) {
        if (mat == null || mat.isLegacy() || mat.name().startsWith("LEGACY_")) {
            return false;
        }
        if (isAir(mat)) {
            return false;
        }
        if (hasRegistry()) {
            try {
                return mat.isItem();
            } catch (Throwable ignored) {
            }
        }
        String name = mat.name().toLowerCase(Locale.ROOT);
        return !name.startsWith("wall_")
                && !name.startsWith("potted_")
                && !name.startsWith("attached_")
                && !name.endsWith("_wall_sign")
                && !name.endsWith("_wall_hanging_sign")
                && !name.endsWith("_wall_fan")
                && !name.endsWith("_wall_banner")
                && !name.endsWith("_wall_skull")
                && !name.endsWith("_wall_head")
                && !name.endsWith("_wall_torch")
                && !name.endsWith("_candle_cake")
                && !name.equals("candle_cake")
                && !name.endsWith("_crop")
                && !name.endsWith("_stem")
                && !name.equals("water")
                && !name.equals("lava")
                && !name.equals("water_cauldron")
                && !name.equals("lava_cauldron")
                && !name.equals("powder_snow_cauldron")
                && !name.equals("beetroots")
                && !name.equals("carrots")
                && !name.equals("potatoes")
                && !name.equals("cocoa")
                && !name.equals("bamboo_sapling")
                && !name.equals("sweet_berry_bush")
                && !name.equals("tall_seagrass")
                && !name.equals("fire")
                && !name.equals("soul_fire")
                && !name.equals("nether_portal")
                && !name.equals("end_portal")
                && !name.equals("end_gateway")
                && !name.equals("redstone_wire")
                && !name.equals("tripwire")
                && !name.equals("moving_piston")
                && !name.equals("piston_head")
                && !name.equals("powder_snow")
                && !name.equals("frosted_ice")
                && !name.equals("bubble_column")
                && !name.equals("test_block");
    }

    public static boolean isEnchantable(Material material) {
        return getEnchantmentCategory(material) != null;
    }

    public static String getEnchantmentCategory(Material material) {
        if (material == null) return null;
        String name = material.name();
        if (name.endsWith("_SWORD")) return "sword";
        if (name.endsWith("_HELMET") || name.equals("TURTLE_HELMET")) return "helmet";
        if (name.endsWith("_CHESTPLATE")) return "chestplate";
        if (name.endsWith("_LEGGINGS")) return "leggings";
        if (name.endsWith("_BOOTS")) return "boots";
        if (name.equals("ELYTRA")) return "elytra";
        if (name.equals("BOW")) return "bow";
        if (name.equals("CROSSBOW")) return "crossbow";
        if (name.equals("FISHING_ROD")) return "fishing_rod";
        if (name.endsWith("_SHOVEL")) return "shovel";
        if (name.endsWith("_PICKAXE")) return "pickaxe";
        if (name.endsWith("_AXE")) return "axe";
        if (name.endsWith("_HOE")) return "hoe";
        if (name.equals("SHIELD")) return "shield";
        if (name.equals("TRIDENT")) return "trident";
        if (name.equals("MACE")) return "mace";
        if (name.equals("SHEARS") || name.equals("FLINT_AND_STEEL") || name.equals("CARROT_ON_A_STICK")
                || name.equals("WARPED_FUNGUS_ON_A_STICK") || name.equals("BRUSH")) {
            return "utility";
        }
        if (name.equals("BOOK") || name.equals("ENCHANTED_BOOK")) return "book";
        return null;
    }

    public static List<EnchantmentSpec> getEnchantmentSpecs(SmpPlugin plugin, Material material) {
        if (material == null) return Collections.emptyList();
        String category = getEnchantmentCategory(material);
        if (category == null) return Collections.emptyList();

        List<EnchantmentSpec> specs = new ArrayList<>();
        switch (category) {
            case "sword" -> {
                addSpec(specs, "curse_of_vanishing", 1);
                addSpec(specs, "sharpness", 5);
                addSpec(specs, "smite", 5);
                addSpec(specs, "bane_of_arthropods", 5);
                addSpec(specs, "sweeping_edge", 3);
                addSpec(specs, "fire_aspect", 2);
                addSpec(specs, "knockback", 2);
                addSpec(specs, "looting", 3);
                addSpec(specs, "unbreaking", 3);
                addSpec(specs, "mending", 1);
            }
            case "helmet" -> {
                addSpec(specs, "curse_of_vanishing", 1);
                addSpec(specs, "curse_of_binding", 1);
                addSpec(specs, "protection", 4);
                addSpec(specs, "fire_protection", 4);
                addSpec(specs, "blast_protection", 4);
                addSpec(specs, "projectile_protection", 4);
                addSpec(specs, "respiration", 3);
                addSpec(specs, "aqua_affinity", 1);
                addSpec(specs, "thorns", 3);
                addSpec(specs, "unbreaking", 3);
                addSpec(specs, "mending", 1);
            }
            case "chestplate" -> {
                addSpec(specs, "curse_of_vanishing", 1);
                addSpec(specs, "curse_of_binding", 1);
                addSpec(specs, "protection", 4);
                addSpec(specs, "fire_protection", 4);
                addSpec(specs, "blast_protection", 4);
                addSpec(specs, "projectile_protection", 4);
                addSpec(specs, "thorns", 3);
                addSpec(specs, "unbreaking", 3);
                addSpec(specs, "mending", 1);
            }
            case "leggings" -> {
                addSpec(specs, "curse_of_vanishing", 1);
                addSpec(specs, "curse_of_binding", 1);
                addSpec(specs, "protection", 4);
                addSpec(specs, "fire_protection", 4);
                addSpec(specs, "blast_protection", 4);
                addSpec(specs, "projectile_protection", 4);
                addSpec(specs, "swift_sneak", 3);
                addSpec(specs, "thorns", 3);
                addSpec(specs, "unbreaking", 3);
                addSpec(specs, "mending", 1);
            }
            case "boots" -> {
                addSpec(specs, "curse_of_vanishing", 1);
                addSpec(specs, "curse_of_binding", 1);
                addSpec(specs, "protection", 4);
                addSpec(specs, "fire_protection", 4);
                addSpec(specs, "blast_protection", 4);
                addSpec(specs, "projectile_protection", 4);
                addSpec(specs, "feather_falling", 4);
                addSpec(specs, "depth_strider", 3);
                addSpec(specs, "frost_walker", 2);
                addSpec(specs, "soul_speed", 3);
                addSpec(specs, "thorns", 3);
                addSpec(specs, "unbreaking", 3);
                addSpec(specs, "mending", 1);
            }
            case "pickaxe", "shovel", "hoe" -> {
                addSpec(specs, "curse_of_vanishing", 1);
                addSpec(specs, "efficiency", 5);
                addSpec(specs, "fortune", 3);
                addSpec(specs, "silk_touch", 1);
                addSpec(specs, "unbreaking", 3);
                addSpec(specs, "mending", 1);
            }
            case "axe" -> {
                addSpec(specs, "curse_of_vanishing", 1);
                addSpec(specs, "sharpness", 5);
                addSpec(specs, "smite", 5);
                addSpec(specs, "bane_of_arthropods", 5);
                addSpec(specs, "efficiency", 5);
                addSpec(specs, "fortune", 3);
                addSpec(specs, "silk_touch", 1);
                addSpec(specs, "unbreaking", 3);
                addSpec(specs, "mending", 1);
            }
            case "bow" -> {
                addSpec(specs, "curse_of_vanishing", 1);
                addSpec(specs, "power", 5);
                addSpec(specs, "punch", 2);
                addSpec(specs, "flame", 1);
                addSpec(specs, "infinity", 1);
                addSpec(specs, "unbreaking", 3);
                addSpec(specs, "mending", 1);
            }
            case "crossbow" -> {
                addSpec(specs, "curse_of_vanishing", 1);
                addSpec(specs, "quick_charge", 3);
                addSpec(specs, "multishot", 1);
                addSpec(specs, "piercing", 4);
                addSpec(specs, "unbreaking", 3);
                addSpec(specs, "mending", 1);
            }
            case "fishing_rod" -> {
                addSpec(specs, "curse_of_vanishing", 1);
                addSpec(specs, "luck_of_the_sea", 3);
                addSpec(specs, "lure", 3);
                addSpec(specs, "unbreaking", 3);
                addSpec(specs, "mending", 1);
            }
            case "trident" -> {
                addSpec(specs, "curse_of_vanishing", 1);
                addSpec(specs, "impaling", 5);
                addSpec(specs, "loyalty", 3);
                addSpec(specs, "riptide", 3);
                addSpec(specs, "channeling", 1);
                addSpec(specs, "unbreaking", 3);
                addSpec(specs, "mending", 1);
            }
            case "mace" -> {
                addSpec(specs, "curse_of_vanishing", 1);
                addSpec(specs, "density", 5);
                addSpec(specs, "breach", 4);
                addSpec(specs, "wind_burst", 3);
                addSpec(specs, "smite", 5);
                addSpec(specs, "bane_of_arthropods", 5);
                addSpec(specs, "fire_aspect", 2);
                addSpec(specs, "unbreaking", 3);
                addSpec(specs, "mending", 1);
            }
            case "elytra" -> {
                addSpec(specs, "curse_of_vanishing", 1);
                addSpec(specs, "curse_of_binding", 1);
                addSpec(specs, "unbreaking", 3);
                addSpec(specs, "mending", 1);
            }
            case "shield", "utility" -> {
                addSpec(specs, "curse_of_vanishing", 1);
                addSpec(specs, "unbreaking", 3);
                addSpec(specs, "mending", 1);
            }
            case "book" -> {
                addSpec(specs, "curse_of_vanishing", 1);
                addSpec(specs, "curse_of_binding", 1);
                addSpec(specs, "sharpness", 5);
                addSpec(specs, "smite", 5);
                addSpec(specs, "protection", 4);
                addSpec(specs, "efficiency", 5);
                addSpec(specs, "fortune", 3);
                addSpec(specs, "silk_touch", 1);
                addSpec(specs, "looting", 3);
                addSpec(specs, "power", 5);
                addSpec(specs, "unbreaking", 3);
                addSpec(specs, "mending", 1);
            }
        }

        if (plugin != null && plugin.getConfigManager() != null) {
            try {
                var encCfg = plugin.getConfigManager().getEnchantments();
                if (encCfg != null && encCfg.isConfigurationSection(category)) {
                    var sec = encCfg.getConfigurationSection(category);
                    if (sec != null) {
                        for (String childKey : sec.getKeys(false)) {
                            String encVal = sec.getString(childKey + ".enchantment");
                            if (encVal != null && !encVal.isBlank()) {
                                String enchId = encVal.split(";")[0].trim().toLowerCase(Locale.ROOT);
                                if (enchId.startsWith("minecraft:")) enchId = enchId.substring(10);
                                if (enchId.equals("vanishing_curse")) enchId = "curse_of_vanishing";
                                if (enchId.equals("binding_curse")) enchId = "curse_of_binding";
                                final String finalId = enchId;
                                if (specs.stream().noneMatch(s -> s.key().equalsIgnoreCase(finalId))) {
                                    addSpec(specs, finalId, 1);
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        return specs;
    }

    private static void addSpec(List<EnchantmentSpec> list, String key, int fallbackMaxLevel) {
        Enchantment ench = null;
        try {
            if (org.bukkit.Bukkit.getServer() != null) {
                ench = QuickBuyEntry.findEnchantment(key);
            }
        } catch (Throwable ignored) {}
        boolean isCurse = key.contains("curse") || (ench != null && isCurseEnchantment(ench));
        String name = prettifyEnchantmentName(key);
        int maxLvl = fallbackMaxLevel;
        if (ench != null) {
            try {
                int bukkitMax = ench.getMaxLevel();
                if (bukkitMax > 0) {
                    maxLvl = bukkitMax;
                }
            } catch (Throwable ignored) {}
        }
        list.add(new EnchantmentSpec(key, ench, name, isCurse, maxLvl));
    }

    public static boolean isCurseEnchantment(Enchantment ench) {
        if (ench == null) return false;
        try {
            if (ench.isCursed()) return true;
        } catch (Throwable ignored) {}
        try {
            String key = ench.getKey().getKey().toLowerCase(Locale.ROOT);
            return key.contains("curse");
        } catch (Throwable ignored) {}
        return false;
    }

    public static String prettifyEnchantment(Enchantment ench) {
        if (ench == null) return "";
        return prettifyEnchantmentName(ench.getKey().getKey());
    }

    public static String prettifyEnchantmentName(String key) {
        if (key == null || key.isBlank()) return "";
        String clean = key.trim().toLowerCase(Locale.ROOT);
        if (clean.startsWith("minecraft:")) {
            clean = clean.substring("minecraft:".length());
        }
        return switch (clean) {
            case "curse_of_vanishing", "vanishing_curse" -> "Curse of Vanishing";
            case "curse_of_binding", "binding_curse" -> "Curse of Binding";
            case "sharpness" -> "Sharpness";
            case "smite" -> "Smite";
            case "bane_of_arthropods" -> "Bane of Arthropods";
            case "sweeping_edge", "sweeping" -> "Sweeping Edge";
            case "fire_aspect" -> "Fire Aspect";
            case "knockback" -> "Knockback";
            case "looting" -> "Looting";
            case "unbreaking" -> "Unbreaking";
            case "mending" -> "Mending";
            case "protection" -> "Protection";
            case "fire_protection" -> "Fire Protection";
            case "blast_protection" -> "Blast Protection";
            case "projectile_protection" -> "Projectile Protection";
            case "feather_falling" -> "Feather Falling";
            case "respiration" -> "Respiration";
            case "aqua_affinity" -> "Aqua Affinity";
            case "thorns" -> "Thorns";
            case "depth_strider" -> "Depth Strider";
            case "frost_walker" -> "Frost Walker";
            case "soul_speed" -> "Soul Speed";
            case "swift_sneak" -> "Swift Sneak";
            case "efficiency" -> "Efficiency";
            case "silk_touch" -> "Silk Touch";
            case "fortune" -> "Fortune";
            case "power" -> "Power";
            case "punch" -> "Punch";
            case "flame" -> "Flame";
            case "infinity" -> "Infinity";
            case "luck_of_the_sea" -> "Luck of the Sea";
            case "lure" -> "Lure";
            case "loyalty" -> "Loyalty";
            case "impaling" -> "Impaling";
            case "riptide" -> "Riptide";
            case "channeling" -> "Channeling";
            case "multishot" -> "Multishot";
            case "quick_charge" -> "Quick Charge";
            case "piercing" -> "Piercing";
            case "density" -> "Density";
            case "breach" -> "Breach";
            case "wind_burst" -> "Wind Burst";
            default -> {
                String[] parts = clean.split("_");
                StringBuilder sb = new StringBuilder();
                for (String p : parts) {
                    if (p.isEmpty()) continue;
                    if (!sb.isEmpty()) sb.append(" ");
                    if (p.equalsIgnoreCase("of") || p.equalsIgnoreCase("the")) {
                        sb.append(p.toLowerCase(Locale.ROOT));
                    } else {
                        sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1).toLowerCase(Locale.ROOT));
                    }
                }
                yield sb.toString();
            }
        };
    }

    public static String toRoman(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(number);
        };
    }

    public static boolean hasKnownTextures() {
        return !KNOWN_TEXTURES.isEmpty();
    }

    public static boolean hasTexture(String path) {
        return KNOWN_TEXTURES.contains(path);
    }
}
