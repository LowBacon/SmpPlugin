// SmpPlugin by LowBacon
package com.bx.smpPlugin.menus;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.ShopManager;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.ItemUtils;
import com.bx.smpPlugin.utils.NumberUtils;
import com.bx.smpPlugin.utils.SignInputUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class QuickBuyInsertItemMenu extends BaseMenu {

    private static final int CANCEL_SLOT = 0;
    private static final int FILLER_LEFT = 1;
    private static final int INSERT_SLOT = 2;
    private static final int FILLER_RIGHT = 3;
    private static final int CONFIRM_SLOT = 4;

    private ItemStack realItem;
    private boolean confirmed;
    private boolean refreshScheduled;

    public QuickBuyInsertItemMenu(SmpPlugin plugin) {
        super(
                plugin,
                plugin.getConfigManager().getShop().getString("INSERT-ITEM.TITLE",
                        plugin.getConfigManager().getMessageOrDefault("QUICK_BUY.INSERT_ITEM_TITLE", "Click to sell an item")),
                InventoryType.HOPPER
        );
    }

    public int getCancelSlot() {
        return plugin.getConfigManager().getShop().getInt("INSERT-ITEM.CANCEL-BUTTON.SLOT", CANCEL_SLOT);
    }

    public int getConfirmSlot() {
        return plugin.getConfigManager().getShop().getInt("INSERT-ITEM.CONFIRM-BUTTON.SLOT", CONFIRM_SLOT);
    }

    @Override
    public void build(Player player) {
        var shopCfg = plugin.getConfigManager().getShop();
        int cancelSlot = getCancelSlot();
        Material cancelMat = ItemUtils.parseMaterial(shopCfg.getString("INSERT-ITEM.CANCEL-BUTTON.MATERIAL", "RED_STAINED_GLASS_PANE"));
        String cancelName = shopCfg.getString("INSERT-ITEM.CANCEL-BUTTON.NAME", "&cCancel");
        List<String> cancelLore = shopCfg.getStringList("INSERT-ITEM.CANCEL-BUTTON.LORE");
        if (cancelLore == null || cancelLore.isEmpty()) {
            cancelLore = List.of("&7Click to cancel and return");
        }
        if (inventory != null && cancelSlot >= 0 && cancelSlot < inventory.getSize()) {
            set(cancelSlot, ItemUtils.createItem(cancelMat, cancelName, cancelLore));
        }

        int confirmSlot = getConfirmSlot();
        Material confirmMat = ItemUtils.parseMaterial(shopCfg.getString("INSERT-ITEM.CONFIRM-BUTTON.MATERIAL", "LIME_STAINED_GLASS_PANE"));
        String confirmName = shopCfg.getString("INSERT-ITEM.CONFIRM-BUTTON.NAME", "&aConfirm");
        List<String> confirmLore = shopCfg.getStringList("INSERT-ITEM.CONFIRM-BUTTON.LORE");
        if (confirmLore == null || confirmLore.isEmpty()) {
            confirmLore = List.of("&7Click to proceed to price input");
        }
        if (inventory != null && confirmSlot >= 0 && confirmSlot < inventory.getSize()) {
            set(confirmSlot, ItemUtils.createItem(confirmMat, confirmName, confirmLore));
        }

        if (inventory != null) {
            for (int i = 0; i < inventory.getSize(); i++) {
                if (i != cancelSlot && i != confirmSlot && i != INSERT_SLOT) {
                    set(i, ItemUtils.createItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
                }
            }
        }
    }

    public void handleInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int rawSlot = event.getRawSlot();
        int cancelSlot = getCancelSlot();
        int confirmSlot = getConfirmSlot();

        if (rawSlot == cancelSlot) {
            event.setCancelled(true);
            QuickBuySounds.play(player, plugin, QuickBuySounds.CANCEL);
            returnItemToPlayer(player);
            new AuctionYourItemsMenu(plugin).open(player);
            return;
        }

        if (rawSlot == confirmSlot) {
            event.setCancelled(true);
            ItemStack itemToSell = realItem != null ? realItem.clone() : inventory.getItem(INSERT_SLOT);
            if (itemToSell == null || itemToSell.getType().isAir()) {
                QuickBuySounds.fail(player, plugin);
                player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessageOrDefault(
                        "QUICK_BUY.NO_ITEM_INSERTED",
                        "&cPlease insert an item to sell."
                )));
                return;
            }

            confirmed = true;
            inventory.setItem(INSERT_SLOT, null);
            realItem = null;
            QuickBuySounds.click(player, plugin);

            SignInputUtil.open(plugin, player, List.of("", "↑↑↑↑↑", "Type price", ""), 0, text -> {
                if (text == null || text.isBlank()) {
                    plugin.getAuctionHouseManager().restoreEscrow(player, itemToSell);
                    new AuctionYourItemsMenu(plugin).open(player);
                    return;
                }

                try {
                    double price = NumberUtils.parse(text);
                    if (price <= 0 || !Double.isFinite(price)) {
                        plugin.getAuctionHouseManager().restoreEscrow(player, itemToSell);
                        player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessageOrDefault(
                                "AUCTION_HOUSE.INVALID_PRICE",
                                "&cInvalid price entered."
                        )));
                        QuickBuySounds.fail(player, plugin);
                        new AuctionYourItemsMenu(plugin).open(player);
                        return;
                    }

                    new QuickBuyConfirmListingMenu(plugin, itemToSell, price).open(player);
                } catch (Exception e) {
                    plugin.getAuctionHouseManager().restoreEscrow(player, itemToSell);
                    player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessageOrDefault(
                            "AUCTION_HOUSE.INVALID_PRICE",
                            "&cInvalid price entered."
                    )));
                    QuickBuySounds.fail(player, plugin);
                    new AuctionYourItemsMenu(plugin).open(player);
                }
            });
            return;
        }

        if (rawSlot == INSERT_SLOT) {
            event.setCancelled(false);
            scheduleRefresh(player);
            return;
        }

        if (inventory != null && rawSlot < inventory.getSize()) {
            event.setCancelled(true);
            return;
        }

        if (event.isShiftClick()) {
            scheduleRefresh(player);
            return;
        }

        event.setCancelled(false);
    }

    public void handleInventoryDrag(InventoryDragEvent event) {
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < inventory.getSize() && rawSlot != INSERT_SLOT) {
                event.setCancelled(true);
                return;
            }
        }
        event.setCancelled(false);
        if (event.getWhoClicked() instanceof Player player) {
            scheduleRefresh(player);
        }
    }

    private void scheduleRefresh(Player player) {
        if (refreshScheduled) {
            return;
        }
        refreshScheduled = true;
        plugin.getSpigotScheduler().runEntityLater(player, () -> {
            refreshScheduled = false;
            updateInsertSlotPreview(player);
        }, 1L);
    }

    private void updateInsertSlotPreview(Player player) {
        ItemStack item = inventory.getItem(INSERT_SLOT);
        if (item == null || item.getType().isAir()) {
            realItem = null;
            return;
        }

        if (realItem == null || !realItem.isSimilar(item) || realItem.getAmount() != item.getAmount()) {
            realItem = item.clone();
        }

        // Add price estimate lore line to the display item
        ItemStack preview = realItem.clone();
        ShopManager.QuickBuyQuote quote = plugin.getShopManager().resolveQuickBuyQuote(preview, preview.getAmount());
        double estPrice = quote.available() ? quote.totalPrice() : 0.0;
        String formattedPrice = NumberUtils.formatNice(estPrice);

        ItemMeta meta = preview.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            String priceFmt = plugin.getConfigManager().getShop().getString("INSERT-ITEM.ESTIMATED-PRICE-FORMAT", "&a$ &f{price}");
            lore.add(ColorUtils.colorize(priceFmt.replace("{price}", formattedPrice)));
            meta.setLore(lore);
            preview.setItemMeta(meta);
        }
        inventory.setItem(INSERT_SLOT, preview);
    }

    private void returnItemToPlayer(Player player) {
        ItemStack itemToReturn = realItem != null ? realItem : inventory.getItem(INSERT_SLOT);
        if (itemToReturn != null && !itemToReturn.getType().isAir()) {
            inventory.setItem(INSERT_SLOT, null);
            realItem = null;
            plugin.getAuctionHouseManager().restoreEscrow(player, itemToReturn);
        }
    }

    @Override
    public void onClose(Player player) {
        if (!confirmed) {
            returnItemToPlayer(player);
        }
    }
}
