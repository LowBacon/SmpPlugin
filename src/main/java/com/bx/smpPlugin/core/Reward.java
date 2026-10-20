package com.bx.smpPlugin.core;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.CurrencyManager;
import com.bx.smpPlugin.managers.LeaderboardManager;
import com.bx.smpPlugin.models.EconomyReason;
import com.bx.smpPlugin.models.PlayerData;
import com.bx.smpPlugin.utils.ItemUtils;
import com.bx.smpPlugin.utils.NumberUtils;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A prize: money, shards, items and console commands, in any mix.
 *
 * <p>Read from a config section:
 * <pre>
 * MONEY: 1000
 * SHARDS: 5
 * ITEMS:              # "MATERIAL", "MATERIAL:amount", or full items saved by the in-game editor
 *   - "GOLDEN_APPLE:3"
 * COMMANDS:           # run by the console; %player% is the player's name
 *   - "crate key give %player% vote 1"
 * DISPLAY:            # optional lines shown instead of the automatic summary
 *   - "&a$1,000 and a mystery key"
 * </pre>
 */
public record Reward(double money, long shards, List<ItemStack> items, List<String> commands, List<String> display) {

    public static final Reward NONE = new Reward(0D, 0L, List.of(), List.of(), List.of());

    public Reward {
        items = items == null ? List.of() : items.stream().filter(item -> item != null && !item.getType().isAir())
                .map(ItemStack::clone).toList();
        commands = commands == null ? List.of() : List.copyOf(commands);
        display = display == null ? List.of() : List.copyOf(display);
    }

    public boolean isEmpty() {
        return money <= 0D && shards <= 0L && items.isEmpty() && commands.isEmpty();
    }

    public Reward withItems(List<ItemStack> newItems) {
        return new Reward(money, shards, newItems, commands, display);
    }

    public Reward withMoney(double newMoney) {
        return new Reward(Math.max(0D, newMoney), shards, items, commands, display);
    }

    public Reward withShards(long newShards) {
        return new Reward(money, Math.max(0L, newShards), items, commands, display);
    }

    /** Adds two rewards together (used for streak bonuses). */
    public Reward plus(Reward other) {
        if (other == null || other.isEmpty()) {
            return this;
        }
        List<ItemStack> allItems = new ArrayList<>(items);
        allItems.addAll(other.items);
        List<String> allCommands = new ArrayList<>(commands);
        allCommands.addAll(other.commands);
        List<String> allDisplay = new ArrayList<>(display);
        allDisplay.addAll(other.display);
        return new Reward(money + other.money, shards + other.shards, allItems, allCommands, allDisplay);
    }

    public static Reward read(ConfigurationSection section) {
        if (section == null) {
            return NONE;
        }
        List<ItemStack> items = new ArrayList<>();
        List<?> rawItems = section.getList("ITEMS");
        if (rawItems != null) {
            for (Object raw : rawItems) {
                items.addAll(parseItems(raw));
            }
        }
        return new Reward(
                Math.max(0D, section.getDouble("MONEY", 0D)),
                Math.max(0L, section.getLong("SHARDS", 0L)),
                items,
                section.getStringList("COMMANDS"),
                section.getStringList("DISPLAY")
        );
    }

    /** Accepts an ItemStack saved by Bukkit, a map of one, or "MATERIAL[:amount]" (split into stacks). */
    @SuppressWarnings("unchecked")
    static List<ItemStack> parseItems(Object raw) {
        if (raw instanceof ItemStack stack) {
            return List.of(stack.clone());
        }
        if (raw instanceof Map<?, ?> map) {
            try {
                return List.of(ItemStack.deserialize((Map<String, Object>) map));
            } catch (RuntimeException ignored) {
                return List.of();
            }
        }
        if (raw == null) {
            return List.of();
        }
        String[] parts = raw.toString().trim().split(":");
        Material material = Material.matchMaterial(parts[0].trim().toUpperCase(Locale.ROOT));
        if (material == null || material.isAir() || !material.isItem()) {
            return List.of();
        }
        int amount = 1;
        if (parts.length > 1) {
            try {
                amount = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException ignored) {
                amount = 1;
            }
        }
        int max = Math.max(1, material.getMaxStackSize());
        amount = Math.max(1, Math.min(amount, max * 36));
        List<ItemStack> stacks = new ArrayList<>();
        while (amount > 0) {
            int size = Math.min(max, amount);
            stacks.add(new ItemStack(material, size));
            amount -= size;
        }
        return stacks;
    }

    /** Writes this reward into a config section (used by the in-game editors). */
    public void write(ConfigurationSection section) {
        section.set("MONEY", money > 0D ? money : null);
        section.set("SHARDS", shards > 0L ? shards : null);
        section.set("ITEMS", items.isEmpty() ? null : new ArrayList<>(items));
        section.set("COMMANDS", commands.isEmpty() ? null : commands);
        section.set("DISPLAY", display.isEmpty() ? null : display);
    }

    /**
     * Hands the reward to an online player. Items that do not fit go to their reward vault (/claimitems). Must run
     * on the player's thread.
     */
    public void give(SmpPlugin plugin, Player player) {
        give(plugin, player, EconomyReason.REWARD, com.bx.smpPlugin.economy.EconomyContext.NONE);
    }

    /** Like {@link #give(SmpPlugin, Player)}, recording the money under a reason and a note in the ledger. */
    public void give(SmpPlugin plugin, Player player, EconomyReason reason, com.bx.smpPlugin.economy.EconomyContext context) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (money > 0D && plugin.getEconomyManager() != null) {
            com.bx.smpPlugin.models.EconomyTransactionResult result = plugin.getEconomyManager().deposit(player, money, reason, context);
            if (!result.success()) {
                plugin.getLogger().warning("Could not pay the " + money + " reward to " + player.getName() + ": "
                        + result.failureReason());
            }
        }
        if (shards > 0L) {
            PlayerData data = plugin.getPlayerDataManager().get(player);
            if (data != null) {
                data.addShards(shards);
                if (plugin.getLeaderboardManager() != null) {
                    plugin.getLeaderboardManager().invalidate(LeaderboardManager.LeaderboardType.SHARDS);
                }
            }
        }
        if (!items.isEmpty()) {
            com.bx.smpPlugin.engagement.PendingItems.giveOrKeep(plugin, player, items);
        }
        for (String command : commands) {
            String filled = command.replace("%player%", player.getName())
                    .replace("{player}", player.getName())
                    .replace("%uuid%", player.getUniqueId().toString());
            plugin.getSpigotScheduler().dispatchConsoleCommand(filled.startsWith("/") ? filled.substring(1) : filled);
        }
    }

    /** Short lines describing the prize, for lore, chat and dialogs. */
    public List<String> describe(SmpPlugin plugin) {
        if (!display.isEmpty()) {
            return display;
        }
        List<String> lines = new ArrayList<>();
        if (money > 0D) {
            String formatted = plugin.getCurrencyManager() == null
                    ? NumberUtils.formatNice(money)
                    : plugin.getCurrencyManager().formatCompactAmount(CurrencyManager.CurrencyType.MONEY, money);
            lines.add("&a$" + formatted);
        }
        if (shards > 0L) {
            lines.add("&d" + NumberUtils.format(shards) + " Shards");
        }
        Map<String, Integer> counted = new HashMap<>();
        List<String> order = new ArrayList<>();
        for (ItemStack item : items) {
            String name = itemName(item);
            if (counted.merge(name, item.getAmount(), Integer::sum) == item.getAmount()) {
                order.add(name);
            }
        }
        for (String name : order) {
            lines.add("&f" + counted.get(name) + "x " + name);
        }
        if (!commands.isEmpty() && lines.isEmpty()) {
            lines.add("&eA mystery reward");
        }
        return lines;
    }

    /** The item shown for this prize in menus: the first item, else gold for money, else a chest. */
    public ItemStack icon() {
        if (!items.isEmpty()) {
            return items.get(0).clone();
        }
        if (money > 0D) {
            return new ItemStack(Material.GOLD_INGOT);
        }
        if (shards > 0L) {
            return new ItemStack(Material.AMETHYST_SHARD);
        }
        return new ItemStack(Material.CHEST);
    }

    public static String itemName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        String key = item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder out = new StringBuilder(key.length());
        boolean upper = true;
        for (char c : key.toCharArray()) {
            out.append(upper ? Character.toUpperCase(c) : c);
            upper = c == ' ';
        }
        return out.toString();
    }

    /** Builds a menu item that previews this reward, drawn as the given material. */
    public ItemStack previewItem(SmpPlugin plugin, String name, List<String> extraLore, Material material) {
        List<String> lore = new ArrayList<>();
        for (String line : describe(plugin)) {
            lore.add("&8• " + line);
        }
        if (extraLore != null) {
            lore.addAll(extraLore);
        }
        return ItemUtils.createItem(material, name, lore);
    }

    /** Builds a menu item that previews this reward. */
    public ItemStack previewItem(SmpPlugin plugin, String name, List<String> extraLore) {
        List<String> lore = new ArrayList<>();
        for (String line : describe(plugin)) {
            lore.add("&8• " + line);
        }
        if (extraLore != null) {
            lore.addAll(extraLore);
        }
        ItemStack icon = icon();
        icon.setAmount(1);
        return ItemUtils.withDisplay(icon, name, lore);
    }
}
