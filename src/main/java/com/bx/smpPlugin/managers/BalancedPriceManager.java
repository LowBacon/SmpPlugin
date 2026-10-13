package com.bx.smpPlugin.managers;

import com.bx.smpPlugin.SmpPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.StonecuttingRecipe;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Calculates fair, balanced, and realistic prices for Minecraft items that
 * are not explicitly listed in worth.yml.
 *
 * <p>Pricing strategy:
 * <ol>
 *   <li>Blocks unobtainable/admin items (Bedrock, Barrier, Command Blocks).</li>
 *   <li>Curated base prices for non-craftable items (1.21 Trial items, Spawn Eggs, Music Discs, etc.).</li>
 *   <li>Dynamic vanilla recipe cost calculation (synthesizing ingredient costs from worth.yml).</li>
 *   <li>Material name pattern heuristics (slabs, stairs, walls, signs, variants).</li>
 *   <li>Sensible generic fallbacks.</li>
 * </ol>
 * </p>
 */
public class BalancedPriceManager {

    private final SmpPlugin plugin;
    private final Map<Material, Double> priceCache = new ConcurrentHashMap<>();

    private static final Set<Material> UNOBTAINABLE = Set.of(
            Material.AIR,
            Material.CAVE_AIR,
            Material.VOID_AIR,
            Material.BEDROCK,
            Material.BARRIER,
            Material.STRUCTURE_BLOCK,
            Material.STRUCTURE_VOID,
            Material.JIGSAW,
            Material.COMMAND_BLOCK,
            Material.CHAIN_COMMAND_BLOCK,
            Material.REPEATING_COMMAND_BLOCK,
            Material.COMMAND_BLOCK_MINECART,
            Material.DEBUG_STICK,
            Material.LIGHT,
            Material.REINFORCED_DEEPSLATE,
            Material.END_PORTAL_FRAME,
            Material.SPAWNER
    );

    private static final Map<Material, Double> CURATED_PRICES = createCuratedPrices();

    public BalancedPriceManager(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        priceCache.clear();
    }

    private static boolean isAir(Material material) {
        return material == null || material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }

    private static boolean isLikelyBlock(Material material) {
        if (material == null) return false;
        String name = material.name();
        return name.endsWith("_BLOCK") || name.endsWith("_ORE") || name.endsWith("_STONE")
                || name.endsWith("_LOG") || name.endsWith("_WOOD") || name.endsWith("_DIRT")
                || name.endsWith("_SAND") || name.endsWith("_GRAVEL") || name.endsWith("_BRICKS")
                || name.endsWith("_TILES") || name.endsWith("_CONCRETE") || name.endsWith("_TERRACOTTA")
                || name.endsWith("_WOOL") || name.endsWith("_GLASS") || name.endsWith("_PLANKS")
                || name.endsWith("_WALL") || name.endsWith("_SLAB") || name.endsWith("_STAIRS");
    }

    /**
     * Resolves an automatic balanced price for the given material.
     *
     * @param material The material to price.
     * @return A positive balanced price, or -1.0 if unobtainable / invalid.
     */
    public double getBalancedPrice(Material material) {
        if (isAir(material) || UNOBTAINABLE.contains(material)) {
            return -1.0;
        }

        Double cached = priceCache.get(material);
        if (cached != null) {
            return cached;
        }

        double price = computePrice(material, 0, new HashSet<>());
        priceCache.put(material, price);
        return price;
    }

    private double computePrice(Material material, int depth, Set<Material> visited) {
        if (isAir(material) || UNOBTAINABLE.contains(material)) {
            return -1.0;
        }

        if (plugin != null && plugin.getConfigManager() != null && plugin.getConfigManager().getWorth() != null) {
            List<String> blocked = plugin.getConfigManager().getWorth().getStringList("BLOCK-ITEMS");
            if (blocked != null && blocked.contains(material.name())) {
                return -1.0;
            }
        }

        // 1. Check curated prices
        Double curated = CURATED_PRICES.get(material);
        if (curated != null && curated > 0) {
            return curated;
        }

        // 2. Check if worth.yml already has a direct worth for this material
        if (plugin != null && plugin.getWorthManager() != null) {
            double existingWorth = plugin.getWorthManager().getBaseWorth(material);
            if (existingWorth > 0) {
                return existingWorth;
            }
        }

        // Prevent deep cycles in recipe trees
        if (depth >= 4 || !visited.add(material)) {
            return fallbackPatternPrice(material);
        }

        try {
            // 3. Dynamic recipe synthesis via Bukkit recipes
            if (Bukkit.getServer() != null) {
                double recipeCost = computeRecipeCost(material, depth, visited);
                if (recipeCost > 0) {
                    return Math.round(recipeCost * 100.0) / 100.0;
                }
            }

            // 4. Pattern / suffix based derivation
            double patternCost = fallbackPatternPrice(material);
            if (patternCost > 0) {
                return Math.round(patternCost * 100.0) / 100.0;
            }

            // 5. Final fallback
            return isLikelyBlock(material) ? 2.0 : 10.0;
        } finally {
            visited.remove(material);
        }
    }

    private double computeRecipeCost(Material material, int depth, Set<Material> visited) {
        try {
            List<Recipe> recipes = Bukkit.getRecipesFor(new ItemStack(material));
            if (recipes == null || recipes.isEmpty()) {
                return -1.0;
            }

            double lowestCost = Double.MAX_VALUE;

            for (Recipe recipe : recipes) {
                double cost = calculateSingleRecipeCost(recipe, depth, visited);
                if (cost > 0 && cost < lowestCost) {
                    lowestCost = cost;
                }
            }

            return lowestCost == Double.MAX_VALUE ? -1.0 : lowestCost;
        } catch (Exception ignored) {
            return -1.0;
        }
    }

    private double calculateSingleRecipeCost(Recipe recipe, int depth, Set<Material> visited) {
        if (recipe == null) {
            return -1.0;
        }

        int resultAmount = recipe.getResult().getAmount();
        if (resultAmount <= 0) {
            resultAmount = 1;
        }

        if (recipe instanceof ShapedRecipe shaped) {
            double sum = 0;
            for (RecipeChoice choice : shaped.getChoiceMap().values()) {
                if (choice == null) continue;
                double c = getChoiceCost(choice, depth, visited);
                if (c <= 0) return -1.0;
                sum += c;
            }
            return sum / resultAmount;
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            double sum = 0;
            for (RecipeChoice choice : shapeless.getChoiceList()) {
                if (choice == null) continue;
                double c = getChoiceCost(choice, depth, visited);
                if (c <= 0) return -1.0;
                sum += c;
            }
            return sum / resultAmount;
        } else if (recipe instanceof StonecuttingRecipe stonecutting) {
            double inputCost = getChoiceCost(stonecutting.getInputChoice(), depth, visited);
            return inputCost > 0 ? inputCost / resultAmount : -1.0;
        } else if (recipe instanceof CookingRecipe<?> cooking) {
            double inputCost = getChoiceCost(cooking.getInputChoice(), depth, visited);
            return inputCost > 0 ? (inputCost + 0.25) / resultAmount : -1.0;
        } else if (recipe instanceof SmithingTransformRecipe smithing) {
            double templateCost = getChoiceCost(smithing.getTemplate(), depth, visited);
            double baseCost = getChoiceCost(smithing.getBase(), depth, visited);
            double additionCost = getChoiceCost(smithing.getAddition(), depth, visited);
            if (baseCost > 0 && additionCost > 0) {
                return (Math.max(0, templateCost) + baseCost + additionCost) / resultAmount;
            }
        }

        return -1.0;
    }

    private double getChoiceCost(RecipeChoice choice, int depth, Set<Material> visited) {
        if (choice == null) {
            return 0;
        }

        if (choice instanceof RecipeChoice.MaterialChoice matChoice) {
            List<Material> choices = matChoice.getChoices();
            if (choices.isEmpty()) return 0;
            double lowest = Double.MAX_VALUE;
            for (Material m : choices) {
                if (isAir(m)) continue;
                double p = computePrice(m, depth + 1, visited);
                if (p > 0 && p < lowest) {
                    lowest = p;
                }
            }
            return lowest == Double.MAX_VALUE ? -1.0 : lowest;
        } else if (choice instanceof RecipeChoice.ExactChoice exactChoice) {
            List<ItemStack> choices = exactChoice.getChoices();
            if (choices.isEmpty()) return 0;
            double lowest = Double.MAX_VALUE;
            for (ItemStack item : choices) {
                if (item == null || isAir(item.getType())) continue;
                double p = computePrice(item.getType(), depth + 1, visited);
                if (p > 0 && p < lowest) {
                    lowest = p;
                }
            }
            return lowest == Double.MAX_VALUE ? -1.0 : lowest;
        }

        return -1.0;
    }

    private double fallbackPatternPrice(Material material) {
        String name = material.name();

        // Spawn eggs
        if (name.endsWith("_SPAWN_EGG")) {
            return 3000.0;
        }

        // Music discs
        if (name.startsWith("MUSIC_DISC_")) {
            return 1500.0;
        }

        // Pottery sherds
        if (name.endsWith("_POTTERY_SHERD")) {
            return 750.0;
        }

        // Armor trim templates
        if (name.endsWith("_SMITHING_TEMPLATE")) {
            return 3500.0;
        }

        // Banner patterns
        if (name.endsWith("_BANNER_PATTERN")) {
            return 1000.0;
        }

        // Walls: base material * 1.0
        if (name.endsWith("_WALL")) {
            String base = name.substring(0, name.length() - 5);
            double basePrice = findBaseMaterialWorth(base);
            return basePrice > 0 ? basePrice : 1.5;
        }

        // Slabs: base material * 0.5
        if (name.endsWith("_SLAB")) {
            String base = name.substring(0, name.length() - 5);
            double basePrice = findBaseMaterialWorth(base);
            return basePrice > 0 ? basePrice * 0.5 : 1.0;
        }

        // Stairs: base material * 1.5
        if (name.endsWith("_STAIRS")) {
            String base = name.substring(0, name.length() - 7);
            double basePrice = findBaseMaterialWorth(base);
            return basePrice > 0 ? basePrice * 1.5 : 3.0;
        }

        // Buttons: base material * 0.5
        if (name.endsWith("_BUTTON")) {
            String base = name.substring(0, name.length() - 7);
            double basePrice = findBaseMaterialWorth(base);
            return basePrice > 0 ? basePrice * 0.5 : 1.0;
        }

        // Pressure plates: base material * 1.0
        if (name.endsWith("_PRESSURE_PLATE")) {
            String base = name.substring(0, name.length() - 15);
            double basePrice = findBaseMaterialWorth(base);
            return basePrice > 0 ? basePrice : 2.0;
        }

        // Doors
        if (name.endsWith("_DOOR")) {
            String base = name.substring(0, name.length() - 5);
            double basePrice = findBaseMaterialWorth(base + "_PLANKS");
            if (basePrice <= 0) basePrice = findBaseMaterialWorth(base);
            return basePrice > 0 ? basePrice * 2.0 : 5.0;
        }

        // Trapdoors
        if (name.endsWith("_TRAPDOOR")) {
            String base = name.substring(0, name.length() - 9);
            double basePrice = findBaseMaterialWorth(base + "_PLANKS");
            if (basePrice <= 0) basePrice = findBaseMaterialWorth(base);
            return basePrice > 0 ? basePrice * 2.5 : 5.0;
        }

        // Fences
        if (name.endsWith("_FENCE")) {
            String base = name.substring(0, name.length() - 6);
            double basePrice = findBaseMaterialWorth(base + "_PLANKS");
            if (basePrice <= 0) basePrice = findBaseMaterialWorth(base);
            return basePrice > 0 ? basePrice * 1.5 : 3.5;
        }

        // Fence gates
        if (name.endsWith("_FENCE_GATE")) {
            String base = name.substring(0, name.length() - 11);
            double basePrice = findBaseMaterialWorth(base + "_PLANKS");
            if (basePrice <= 0) basePrice = findBaseMaterialWorth(base);
            return basePrice > 0 ? basePrice * 2.0 : 5.0;
        }

        // Signs & Hanging Signs
        if (name.endsWith("_SIGN") || name.endsWith("_HANGING_SIGN")) {
            return 5.0;
        }

        // Boats
        if (name.endsWith("_CHEST_BOAT")) {
            return 25.0;
        }
        if (name.endsWith("_BOAT")) {
            return 15.0;
        }

        // Bundles
        if (name.endsWith("_BUNDLE")) {
            return 500.0;
        }

        // Candle cakes
        if (name.endsWith("_CANDLE_CAKE")) {
            return 25.0;
        }

        // Banners
        if (name.endsWith("_BANNER")) {
            return 20.0;
        }

        // Beds
        if (name.endsWith("_BED")) {
            return 20.0;
        }

        // Carpets
        if (name.endsWith("_CARPET")) {
            return 5.0;
        }

        // Leaves
        if (name.endsWith("_LEAVES")) {
            return 2.0;
        }

        // Saplings
        if (name.endsWith("_SAPLING")) {
            return 3.0;
        }

        // Copper blocks / bulbs / grates
        if (name.contains("COPPER_BULB") || name.contains("COPPER_GRATE")) {
            return 25.0;
        }

        // Tuff variants
        if (name.contains("TUFF")) {
            return 2.0;
        }

        // Mud variants
        if (name.contains("MUD")) {
            return 2.0;
        }

        return isLikelyBlock(material) ? 2.0 : 10.0;
    }

    private double findBaseMaterialWorth(String baseName) {
        if (baseName == null || baseName.isBlank()) {
            return -1.0;
        }

        Material baseMat = Material.matchMaterial(baseName);
        if (baseMat != null && plugin != null && plugin.getWorthManager() != null) {
            double w = plugin.getWorthManager().getBaseWorth(baseMat);
            if (w > 0) {
                return w;
            }
        }

        return -1.0;
    }

    private static Map<Material, Double> createCuratedPrices() {
        Map<Material, Double> map = new HashMap<>();

        // ── 1.21 & Trial Chamber Items ──────────────────────────────────────────
        map.put(Material.HEAVY_CORE, 50000.0);
        map.put(Material.BREEZE_ROD, 80.0);
        map.put(Material.WIND_CHARGE, 20.0);
        map.put(Material.MACE, 50080.0);
        map.put(Material.TRIAL_KEY, 500.0);
        map.put(Material.OMINOUS_TRIAL_KEY, 2500.0);
        map.put(Material.OMINOUS_BOTTLE, 1500.0);
        map.put(Material.ARMADILLO_SCUTE, 100.0);
        map.put(Material.WOLF_ARMOR, 600.0);

        // ── Smithing Templates ──────────────────────────────────────────────────
        map.put(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 20000.0);
        map.put(Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE, 15000.0);
        map.put(Material.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE, 7500.0);
        map.put(Material.WARD_ARMOR_TRIM_SMITHING_TEMPLATE, 7500.0);
        map.put(Material.VEX_ARMOR_TRIM_SMITHING_TEMPLATE, 7500.0);
        map.put(Material.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE, 7500.0);
        map.put(Material.RIB_ARMOR_TRIM_SMITHING_TEMPLATE, 7500.0);
        map.put(Material.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE, 5000.0);
        map.put(Material.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE, 5000.0);

        // ── Music Discs (Rare) ──────────────────────────────────────────────────
        map.put(Material.MUSIC_DISC_PIGSTEP, 5000.0);
        map.put(Material.MUSIC_DISC_OTHERSIDE, 5000.0);
        map.put(Material.MUSIC_DISC_5, 5000.0);
        map.put(Material.MUSIC_DISC_RELIC, 5000.0);
        map.put(Material.MUSIC_DISC_CREATOR, 5000.0);
        map.put(Material.MUSIC_DISC_CREATOR_MUSIC_BOX, 3500.0);
        map.put(Material.MUSIC_DISC_PRECIPICE, 3500.0);

        // ── Rare Collectibles & Boss Drops ──────────────────────────────────────
        map.put(Material.ENCHANTED_GOLDEN_APPLE, 10000.0);
        map.put(Material.TOTEM_OF_UNDYING, 5000.0);
        map.put(Material.NETHER_STAR, 25000.0);
        map.put(Material.DRAGON_EGG, 100000.0);
        map.put(Material.DRAGON_BREATH, 500.0);
        map.put(Material.ELYTRA, 25000.0);
        map.put(Material.SHULKER_SHELL, 750.0);
        map.put(Material.TRIDENT, 15000.0);
        map.put(Material.HEART_OF_THE_SEA, 5000.0);
        map.put(Material.NAUTILUS_SHELL, 500.0);
        map.put(Material.ECHO_SHARD, 2500.0);
        map.put(Material.GOAT_HORN, 1000.0);
        map.put(Material.SNIFFER_EGG, 5000.0);
        map.put(Material.TORCHFLOWER_SEEDS, 25.0);
        map.put(Material.PITCHER_POD, 25.0);
        map.put(Material.SCULK_CATALYST, 1000.0);
        map.put(Material.SCULK_SHRIEKER, 1500.0);
        map.put(Material.SCULK_SENSOR, 500.0);

        // ── Spawn Eggs: Boss / Ultra-Rare ───────────────────────────────────────
        map.put(Material.WARDEN_SPAWN_EGG, 50000.0);
        map.put(Material.ELDER_GUARDIAN_SPAWN_EGG, 50000.0);
        map.put(Material.WITHER_SPAWN_EGG, 50000.0);
        map.put(Material.VILLAGER_SPAWN_EGG, 25000.0);
        map.put(Material.EVOKER_SPAWN_EGG, 15000.0);
        map.put(Material.RAVAGER_SPAWN_EGG, 15000.0);
        map.put(Material.BREEZE_SPAWN_EGG, 15000.0);

        // ── Spawn Eggs: Nether / End / Dangerous ────────────────────────────────
        map.put(Material.SHULKER_SPAWN_EGG, 7500.0);
        map.put(Material.GHAST_SPAWN_EGG, 7500.0);
        map.put(Material.BLAZE_SPAWN_EGG, 7500.0);
        map.put(Material.WITHER_SKELETON_SPAWN_EGG, 7500.0);
        map.put(Material.PIGLIN_BRUTE_SPAWN_EGG, 7500.0);
        map.put(Material.ENDERMAN_SPAWN_EGG, 5000.0);
        map.put(Material.MAGMA_CUBE_SPAWN_EGG, 5000.0);
        map.put(Material.GUARDIAN_SPAWN_EGG, 5000.0);

        // ── Spawn Eggs: Common Monsters ─────────────────────────────────────────
        map.put(Material.CREEPER_SPAWN_EGG, 3500.0);
        map.put(Material.SKELETON_SPAWN_EGG, 3500.0);
        map.put(Material.ZOMBIE_SPAWN_EGG, 3500.0);
        map.put(Material.SPIDER_SPAWN_EGG, 3500.0);
        map.put(Material.CAVE_SPIDER_SPAWN_EGG, 3500.0);
        map.put(Material.SLIME_SPAWN_EGG, 3500.0);
        map.put(Material.DROWNED_SPAWN_EGG, 3500.0);
        map.put(Material.STRAY_SPAWN_EGG, 3500.0);
        map.put(Material.HUSK_SPAWN_EGG, 3500.0);
        map.put(Material.WITCH_SPAWN_EGG, 3500.0);
        map.put(Material.PHANTOM_SPAWN_EGG, 3500.0);
        map.put(Material.BOGGED_SPAWN_EGG, 3500.0);

        // ── Spawn Eggs: Farm / Utility / Pets ───────────────────────────────────
        map.put(Material.IRON_GOLEM_SPAWN_EGG, 5000.0);
        map.put(Material.SNOW_GOLEM_SPAWN_EGG, 1500.0);
        map.put(Material.COW_SPAWN_EGG, 1500.0);
        map.put(Material.SHEEP_SPAWN_EGG, 1500.0);
        map.put(Material.PIG_SPAWN_EGG, 1500.0);
        map.put(Material.CHICKEN_SPAWN_EGG, 1500.0);
        map.put(Material.RABBIT_SPAWN_EGG, 1500.0);
        map.put(Material.BEE_SPAWN_EGG, 1500.0);
        map.put(Material.WOLF_SPAWN_EGG, 2500.0);
        map.put(Material.CAT_SPAWN_EGG, 2500.0);
        map.put(Material.OCELOT_SPAWN_EGG, 2500.0);
        map.put(Material.HORSE_SPAWN_EGG, 2500.0);
        map.put(Material.DONKEY_SPAWN_EGG, 2500.0);
        map.put(Material.MULE_SPAWN_EGG, 2500.0);
        map.put(Material.LLAMA_SPAWN_EGG, 2500.0);
        map.put(Material.PARROT_SPAWN_EGG, 2500.0);
        map.put(Material.ARMADILLO_SPAWN_EGG, 2500.0);
        map.put(Material.AXOLOTL_SPAWN_EGG, 5000.0);
        map.put(Material.FROG_SPAWN_EGG, 3500.0);
        map.put(Material.ALLAY_SPAWN_EGG, 5000.0);
        map.put(Material.SNIFFER_SPAWN_EGG, 5000.0);

        // ── Banner Patterns ─────────────────────────────────────────────────────
        map.put(Material.FLOW_BANNER_PATTERN, 3500.0);
        map.put(Material.GUSTER_BANNER_PATTERN, 3500.0);
        map.put(Material.SKULL_BANNER_PATTERN, 2500.0);
        map.put(Material.CREEPER_BANNER_PATTERN, 2500.0);
        map.put(Material.PIGLIN_BANNER_PATTERN, 2500.0);
        map.put(Material.MOJANG_BANNER_PATTERN, 2500.0);
        map.put(Material.FLOWER_BANNER_PATTERN, 1000.0);
        map.put(Material.FIELD_MASONED_BANNER_PATTERN, 1000.0);
        map.put(Material.BORDURE_INDENTED_BANNER_PATTERN, 1000.0);

        return Collections.unmodifiableMap(map);
    }
}
