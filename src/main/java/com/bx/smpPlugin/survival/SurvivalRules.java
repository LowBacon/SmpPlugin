package com.bx.smpPlugin.survival;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Banned items and disabled recipes from survival-rules.yml.
 *
 * <p>An item is banned by its material, so renaming it or giving it custom data does not get round
 * the ban. Staff with {@link #BYPASS_PERMISSION} are never stopped. Disabled recipes are blocked at
 * the crafting table (the result slot stays empty); the server's recipe list itself is not changed,
 * so turning the rules off brings every recipe straight back.
 */
public final class SurvivalRules implements Listener {

    public static final String BYPASS_PERMISSION = "smpplugin.bypass.itemban";

    public enum Action {
        PLACE, USE, CRAFT, PICKUP
    }

    /** The parsed rules, kept free of server calls so they can be tested. */
    public record Rules(Map<Material, Set<Action>> banned, Set<String> disabledRecipes, Set<String> worlds, String message) {

        public static Rules read(ConfigurationSection config) {
            Map<Material, Set<Action>> banned = new EnumMap<>(Material.class);
            Set<String> recipes = new HashSet<>();
            Set<String> worlds = new HashSet<>();
            String message = "&cThat item is not allowed here: &f{item}&c.";
            if (config != null && config.getBoolean("ENABLED", true)) {
                ConfigurationSection items = config.getConfigurationSection("BANNED-ITEMS");
                if (items != null) {
                    for (String key : items.getKeys(false)) {
                        Material material = Material.matchMaterial(key.trim().toUpperCase(Locale.ROOT));
                        if (material == null) {
                            continue;
                        }
                        Set<Action> actions = EnumSet.noneOf(Action.class);
                        for (String raw : items.getStringList(key)) {
                            try {
                                actions.add(Action.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
                            } catch (IllegalArgumentException ignored) {
                                // Unknown action names are skipped.
                            }
                        }
                        if (actions.isEmpty()) {
                            actions = EnumSet.allOf(Action.class);
                        }
                        banned.put(material, actions);
                    }
                }
                for (String raw : config.getStringList("DISABLED-RECIPES")) {
                    if (raw != null && !raw.isBlank()) {
                        String key = raw.trim().toLowerCase(Locale.ROOT);
                        recipes.add(key.contains(":") ? key : "minecraft:" + key);
                    }
                }
                for (String world : config.getStringList("WORLDS")) {
                    worlds.add(world.toLowerCase(Locale.ROOT));
                }
                message = config.getString("MESSAGE", message);
            }
            return new Rules(Collections.unmodifiableMap(banned), Set.copyOf(recipes), Set.copyOf(worlds), message);
        }

        public boolean isBanned(Material material, Action action) {
            Set<Action> actions = material == null ? null : banned.get(material);
            return actions != null && actions.contains(action);
        }

        public boolean appliesIn(String world) {
            return worlds.isEmpty() || (world != null && worlds.contains(world.toLowerCase(Locale.ROOT)));
        }

        public boolean isRecipeDisabled(String key) {
            return key != null && disabledRecipes.contains(key.toLowerCase(Locale.ROOT));
        }

        public boolean isEmpty() {
            return banned.isEmpty() && disabledRecipes.isEmpty();
        }
    }

    private final SmpPlugin plugin;
    private volatile Rules rules;
    /** When each player was last told, so a held right-click does not flood chat. */
    private final Map<UUID, Long> lastWarning = new HashMap<>();

    public SurvivalRules(SmpPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration config = plugin.getConfigManager().getSurvivalRules();
        rules = Rules.read(config);
    }

    public Rules rules() {
        return rules;
    }

    private boolean blocked(Player player, Material material, Action action) {
        Rules current = rules;
        return player != null && current.isBanned(material, action) && current.appliesIn(player.getWorld().getName())
                && !PermissionUtils.has(player, BYPASS_PERMISSION);
    }

    private void warn(Player player, Material material) {
        long now = System.currentTimeMillis();
        Long last = lastWarning.get(player.getUniqueId());
        if (last != null && now - last < 2_000L) {
            return;
        }
        lastWarning.put(player.getUniqueId(), now);
        if (lastWarning.size() > 256) {
            lastWarning.values().removeIf(time -> now - time > 2_000L);
        }
        player.sendMessage(ColorUtils.toComponent(rules.message().replace("{item}",
                material.name().toLowerCase(Locale.ROOT).replace('_', ' '))));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Material material = event.getItemInHand().getType();
        if (blocked(event.getPlayer(), material, Action.PLACE)) {
            event.setCancelled(true);
            warn(event.getPlayer(), material);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item != null && blocked(event.getPlayer(), item.getType(), Action.USE)) {
            event.setCancelled(true);
            warn(event.getPlayer(), item.getType());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player
                && blocked(player, event.getItem().getItemStack().getType(), Action.PICKUP)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (recipe == null || !(event.getView().getPlayer() instanceof Player player)) {
            return;
        }
        Rules current = rules;
        if (!current.appliesIn(player.getWorld().getName()) || PermissionUtils.has(player, BYPASS_PERMISSION)) {
            return;
        }
        boolean disabled = recipe instanceof Keyed keyed && current.isRecipeDisabled(keyed.getKey().toString());
        Material result = recipe.getResult().getType();
        if (disabled || current.isBanned(result, Action.CRAFT)) {
            event.getInventory().setResult(null);
        }
    }

    /** For /smp status: how many items and recipes are restricted. */
    public String summary() {
        Rules current = rules;
        return current.banned().size() + " banned items, " + current.disabledRecipes().size() + " disabled recipes"
                + (current.worlds().isEmpty() ? "" : " in " + current.worlds());
    }
}
