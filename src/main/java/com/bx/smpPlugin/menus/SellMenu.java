package com.bx.smpPlugin.menus;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.CurrencyManager;
import com.bx.smpPlugin.managers.ShopManager;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.ItemUtils;
import com.bx.smpPlugin.utils.SoundUtils;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * The sell grid.
 *
 * <p>Selling is always confirmed: items sit in the grid until the player clicks the sell button,
 * and closing the menu hands everything back. There is no instant or sell-on-close mode — the
 * button's label carries the running total so a player always sees the price before agreeing to
 * it. Category multiplier progress lives on {@code /sellmulti}.
 */
public class SellMenu extends BaseMenu {

    private static final int SIZE = 54;
    private static final int DEFAULT_SELL_BUTTON_SLOT = 53;
    private static final long REFRESH_DELAY_TICKS = 1L;

    private final int sellButtonSlot;
    private boolean refreshScheduled;

    public SellMenu(SmpPlugin plugin) {
        super(plugin, plugin.getConfigManager().getMenus().getString(
                "SELL-MENU.TITLE",
                "&8Place items in here to sell"
        ), SIZE);
        this.sellButtonSlot = resolveSellButtonSlot(plugin);
    }

    @Override
    public void build(Player player) {
        clear();
        refreshSellButton(player);
    }

    /** Redraws the sell button so its label matches what is currently in the grid. */
    private void refreshSellButton(Player player) {
        FileConfiguration menus = plugin.getConfigManager().getMenus();
        double worth = plugin.getShopManager().previewSellWorth(player, inventory, 0, sellButtonSlot);

        Material material = ItemUtils.parseMaterial(
                menus.getString("SELL-MENU.SELL-BUTTON.MATERIAL", "LIME_STAINED_GLASS_PANE"));
        String title = applyWorth(menus.getString("SELL-MENU.SELL-BUTTON.TITLE", "&#00FC00$ &f{worth}"), worth);
        List<String> lore = menus.getStringList("SELL-MENU.SELL-BUTTON.LORE");
        if (lore.isEmpty()) {
            lore = List.of("&7Click to sell items");
        }
        lore = lore.stream().map(line -> applyWorth(line, worth)).toList();

        set(sellButtonSlot, ItemUtils.createItem(material, title, lore));
    }

    private String applyWorth(String text, double worth) {
        if (text == null) {
            return "";
        }
        String compact = plugin.getCurrencyManager()
                .formatCompactAmount(CurrencyManager.CurrencyType.MONEY, worth);
        String formatted = plugin.getCurrencyManager().formatMoney(worth);
        return ShopManager.applySellPricePlaceholders(text, compact, formatted)
                .replace("{worth_full}", formatted)
                .replace("{worth}", compact);
    }

    @Override
    public void handleClick(int slot, Player player, ClickType clickType) {
        if (slot != sellButtonSlot) {
            return;
        }

        SoundUtils.play(player, plugin.getConfigManager().getSound("MENUS.BUTTON-CLICK"));
        ShopManager.SellResult result = plugin.getShopManager()
                .sellInventoryContents(player, inventory, 0, sellButtonSlot);
        if (result.status() == ShopManager.SellStatus.NO_SELLABLE_ITEMS) {
            player.sendMessage(ColorUtils.toComponent(
                    plugin.getConfigManager().getMessageOrDefault("WORTH.NO-SELLABLE", "&cThis item is not sellable.")
            ));
        }
        refreshSellButton(player);
        player.updateInventory();
    }

    public void handleInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.isShiftClick()) {
            plugin.getSpigotScheduler().runEntityLater(player, player::updateInventory, 1L);
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < inventory.getSize()) {
            if (isSellableSlot(rawSlot)) {
                event.setCancelled(false);
                scheduleRefresh(player);
                return;
            }

            event.setCancelled(true);
            handleClick(rawSlot, player, event.getClick());
            return;
        }

        if (event.getClickedInventory() == null) {
            return;
        }

        // A click in the player's own inventory can shift items into the grid, so the total has
        // to be recomputed even though the top inventory was not the one clicked.
        event.setCancelled(false);
        scheduleRefresh(player);
    }

    public void handleInventoryDrag(InventoryDragEvent event) {
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < inventory.getSize() && !isSellableSlot(rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }

        event.setCancelled(false);
        if (event.getWhoClicked() instanceof Player player) {
            scheduleRefresh(player);
        }
    }

    @Override
    public void onClose(Player player) {
        for (int slot = 0; slot < sellButtonSlot; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }

            player.getInventory().addItem(item).values()
                    .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            inventory.setItem(slot, null);
        }
    }

    /**
     * The click event fires before the item actually lands in the slot, so the total is read a
     * tick later. One pending refresh at a time is enough for a burst of shift-clicks.
     */
    private void scheduleRefresh(Player player) {
        if (refreshScheduled) {
            return;
        }

        refreshScheduled = true;
        plugin.getSpigotScheduler().runEntityLater(player, () -> {
            refreshScheduled = false;
            if (!player.isOnline() || !inventory.equals(player.getOpenInventory().getTopInventory())) {
                return;
            }
            refreshSellButton(player);
            player.updateInventory();
        }, REFRESH_DELAY_TICKS);
    }

    private boolean isSellableSlot(int slot) {
        return slot >= 0 && slot < sellButtonSlot;
    }

    /** Everything before the button is sellable, so the slot doubles as the grid size. */
    static int resolveSellButtonSlot(SmpPlugin plugin) {
        int configured = plugin.getConfigManager().getMenus()
                .getInt("SELL-MENU.SELL-BUTTON.SLOT", DEFAULT_SELL_BUTTON_SLOT);
        return clampSellButtonSlot(configured);
    }

    static int clampSellButtonSlot(int configured) {
        return configured < 1 || configured >= SIZE ? DEFAULT_SELL_BUTTON_SLOT : configured;
    }
}
