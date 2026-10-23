// SmpPlugin by LowBacon
package com.bx.smpPlugin.menus;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.models.AuctionCategory;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.ItemUtils;
import com.bx.smpPlugin.utils.NumberUtils;
import com.bx.smpPlugin.utils.PlayerSettingUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class QuickBuyConfirmListingMenu extends BaseMenu {

    private static final int SIZE = 27;
    private static final int CANCEL_SLOT = 11;
    private static final int ITEM_SLOT = 13;
    private static final int CONFIRM_SLOT = 15;

    private final ItemStack itemToSell;
    private final double price;
    private boolean confirmed;

    public QuickBuyConfirmListingMenu(SmpPlugin plugin, ItemStack itemToSell, double price) {
        super(
                plugin,
                plugin.getConfigManager().getShop().getString("CONFIRM-LISTING.TITLE",
                        plugin.getConfigManager().getMessageOrDefault("QUICK_BUY.CONFIRM_LISTING_TITLE", "Confirm Listing")),
                plugin.getConfigManager().getShop().getInt("CONFIRM-LISTING.SIZE", SIZE)
        );
        this.itemToSell = itemToSell;
        this.price = price;
    }

    @Override
    public void build(Player player) {
        clear();
        var shopCfg = plugin.getConfigManager().getShop();

        // Cancel
        int cancelSlot = shopCfg.getInt("CONFIRM-LISTING.CANCEL-BUTTON.SLOT", CANCEL_SLOT);
        Material cancelMat = ItemUtils.parseMaterial(shopCfg.getString("CONFIRM-LISTING.CANCEL-BUTTON.MATERIAL", "RED_STAINED_GLASS_PANE"));
        String cancelName = shopCfg.getString("CONFIRM-LISTING.CANCEL-BUTTON.NAME", "&fCancel");
        List<String> cancelLore = shopCfg.getStringList("CONFIRM-LISTING.CANCEL-BUTTON.LORE");
        if (cancelLore == null || cancelLore.isEmpty()) {
            cancelLore = List.of("&o&7Click to cancel the sale");
        }
        set(cancelSlot, ItemUtils.createItem(cancelMat, cancelName, cancelLore));

        // Item Preview
        int itemSlot = shopCfg.getInt("CONFIRM-LISTING.PREVIEW-ITEM.SLOT", ITEM_SLOT);
        String notice1 = shopCfg.getString("CONFIRM-LISTING.PREVIEW-ITEM.NOTICE-LINE-1", "&fYou're going to sell");
        String notice2 = shopCfg.getString("CONFIRM-LISTING.PREVIEW-ITEM.NOTICE-LINE-2", "&fthis item for &#00FC00$ &#00FC00{price}");

        ItemStack preview = itemToSell.clone();
        ItemMeta meta = preview.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add(ColorUtils.colorize(notice1));
            lore.add(ColorUtils.colorize(notice2.replace("{price}", NumberUtils.format(price))));
            meta.setLore(lore);
            preview.setItemMeta(meta);
        }
        set(itemSlot, preview);

        // Confirm
        int confirmSlot = shopCfg.getInt("CONFIRM-LISTING.CONFIRM-BUTTON.SLOT", CONFIRM_SLOT);
        Material confirmMat = ItemUtils.parseMaterial(shopCfg.getString("CONFIRM-LISTING.CONFIRM-BUTTON.MATERIAL", "LIME_STAINED_GLASS_PANE"));
        String confirmName = shopCfg.getString("CONFIRM-LISTING.CONFIRM-BUTTON.NAME", "&fConfirm");
        List<String> confirmLore = shopCfg.getStringList("CONFIRM-LISTING.CONFIRM-BUTTON.LORE");
        if (confirmLore == null || confirmLore.isEmpty()) {
            confirmLore = List.of("&o&7Click to sell");
        }
        set(confirmSlot, ItemUtils.createItem(confirmMat, confirmName, confirmLore));
    }

    @Override
    public boolean isEscBackTarget() {
        return false;
    }

    @Override
    public void handleClick(int slot, Player player, ClickType clickType) {
        if (confirmed) {
            return;
        }
        var shopCfg = plugin.getConfigManager().getShop();
        int cancelSlot = shopCfg.getInt("CONFIRM-LISTING.CANCEL-BUTTON.SLOT", CANCEL_SLOT);
        int confirmSlot = shopCfg.getInt("CONFIRM-LISTING.CONFIRM-BUTTON.SLOT", CONFIRM_SLOT);

        if (slot == cancelSlot) {
            confirmed = true;
            QuickBuySounds.play(player, plugin, QuickBuySounds.CANCEL);
            plugin.getAuctionHouseManager().restoreEscrow(player, itemToSell);
            new AuctionYourItemsMenu(plugin).open(player);
            return;
        }

        if (slot == confirmSlot) {
            confirmed = true;
            QuickBuySounds.click(player, plugin);
            int duration = plugin.getAuctionHouseManager().defaultDurationHours();

            plugin.getAuctionHouseManager().createListingFromItem(
                    player,
                    itemToSell,
                    price,
                    duration,
                    AuctionCategory.ALL
            ).thenAccept(result -> plugin.getSpigotScheduler().runEntity(player, () -> {
                if (result.success()) {
                    QuickBuySounds.play(player, plugin, QuickBuySounds.LIST_ITEM);
                    String successMsg = plugin.getConfigManager().getMessageOrDefault(
                            "AUCTION_HOUSE.ITEM_LISTED",
                            "&fYou listed {amount} {item} for &a$ &f{price}",
                            "{amount}", String.valueOf(itemToSell.getAmount()),
                            "{item}", plugin.getAuctionHouseManager().describeItem(itemToSell),
                            "{price}", NumberUtils.format(price)
                    );
                    player.sendMessage(ColorUtils.toComponent(successMsg));

                    String actionBar = plugin.getConfigManager().getMessageOrDefault(
                            "AUCTION_HOUSE.ITEM_LISTED_ACTION_BAR",
                            "&fYou listed {amount} {item} for &a$ &f{price}",
                            "{amount}", String.valueOf(itemToSell.getAmount()),
                            "{item}", plugin.getAuctionHouseManager().describeItem(itemToSell),
                            "{price}", NumberUtils.format(price)
                    );
                    PlayerSettingUtils.sendActionBar(plugin, player, actionBar);
                } else {
                    QuickBuySounds.fail(player, plugin);
                    String errorMsg = switch (result.reason()) {
                        case NO_MONEY -> plugin.getConfigManager().getMessageOrDefault(
                                "AUCTION_HOUSE.NOT_ENOUGH_MONEY_FEE",
                                "&cYou don't have enough money to pay the listing fee."
                        );
                        case MAX_LISTINGS_REACHED -> plugin.getConfigManager().getMessageOrDefault(
                                "AUCTION_HOUSE.LIMIT_REACHED",
                                "&cYou have reached the maximum active listings limit."
                        );
                        default -> plugin.getConfigManager().getMessageOrDefault(
                                "AUCTION_HOUSE.LISTING_FAILED",
                                "&cFailed to create listing."
                        );
                    };
                    player.sendMessage(ColorUtils.toComponent(errorMsg));
                }
                new AuctionYourItemsMenu(plugin).open(player);
            }));
        }
    }

    @Override
    public void onClose(Player player) {
        if (!confirmed) {
            confirmed = true;
            plugin.getAuctionHouseManager().restoreEscrow(player, itemToSell);
        }
    }
}
