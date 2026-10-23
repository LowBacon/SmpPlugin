// SmpPlugin by LowBacon
package com.bx.smpPlugin.dialogs.screens;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.dialogs.DialogActions;
import com.bx.smpPlugin.dialogs.DialogConfig;
import com.bx.smpPlugin.dialogs.DialogFactory;
import com.bx.smpPlugin.dialogs.DialogScreen;
import com.bx.smpPlugin.dialogs.DialogSession;
import com.bx.smpPlugin.dialogs.DialogSupport;
import com.bx.smpPlugin.managers.AuctionHouseManager;
import com.bx.smpPlugin.models.AuctionListing;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.NumberUtils;
import com.bx.smpPlugin.utils.SoundUtils;
import io.papermc.paper.dialog.DialogResponseView;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

// The Auction House as dialogs: buying shows the real item with its tooltip, seller, price and time
// left before the purchase; /ah sell without a price asks for one next to the held item. Both run
// the same Auction House code as the chest menus, so every rule, fee and check stays the same.
public final class AuctionDialog extends DialogScreen {

    private static final String BUY = "AH_BUY_DIALOG";
    private static final String SELL = "AH_SELL_DIALOG";

    public AuctionDialog(SmpPlugin plugin, DialogConfig config, DialogSession.Store sessions) {
        super(plugin, config, sessions);
    }

    public boolean openPurchase(Player player, long listingId) {
        AuctionHouseManager manager = plugin.getAuctionHouseManager();
        AuctionListing listing = manager.getListing(listingId);
        if (listing == null || !listing.active()) {
            return false;
        }
        String price = plugin.getCurrencyManager().formatMoney(listing.price());
        long secondsLeft = Math.max(0L, (listing.expiresAt() - System.currentTimeMillis()) / 1000L);
        Map<String, String> tokens = DialogConfig.tokens(
                "item", manager.describeItem(listing.item()),
                "amount", String.valueOf(listing.item().getAmount()),
                "seller", listing.sellerName(),
                "price", price,
                "time", NumberUtils.formatTime(secondsLeft));
        int width = config.buttonWidth(BUY, 130);
        DialogFactory.Screen screen = screen(player)
                .title(config.string(BUY + ".TITLE", "&6&lBuy this item?", tokens))
                .externalTitle(config.string(BUY + ".EXTERNAL-TITLE", "Auction House", tokens))
                .columns(2)
                .item(listing.item(), true)
                .text(config.string(BUY + ".TEXT", "&f%item% &7x%amount%\n&7Seller: &f%seller%\n"
                        + "&7Price: &a%price%\n&7Ends in: &f%time%", tokens), config.integer(BUY + ".TEXT-WIDTH", 250));
        double balance = plugin.getEconomyManager().getBalance(player);
        if (balance < listing.price()) {
            screen.text(config.string(BUY + ".TOO-EXPENSIVE", "&cYou have %balance%, not enough for this.",
                    DialogConfig.tokens("balance", plugin.getCurrencyManager().formatMoney(balance))));
        }
        screen.button(config.string(BUY + ".CANCEL-LABEL", "&cCancel", tokens), null, width, DialogActions.AH_BACK)
                .button(config.string(BUY + ".BUY-LABEL", "&aBuy for %price%", tokens),
                        config.string(BUY + ".BUY-TOOLTIP", "Pay %price% to %seller%", tokens),
                        width, DialogActions.AH_BUY + listingId);
        return show(player, screen.build());
    }

    public boolean openSell(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            return false;
        }
        AuctionHouseManager manager = plugin.getAuctionHouseManager();
        Map<String, String> tokens = DialogConfig.tokens(
                "item", manager.describeItem(hand),
                "amount", String.valueOf(hand.getAmount()));
        int width = config.buttonWidth(SELL, 130);
        DialogFactory.Screen screen = screen(player)
                .title(config.string(SELL + ".TITLE", "&6&lSell on the Auction House", tokens))
                .externalTitle(config.string(SELL + ".EXTERNAL-TITLE", "Auction House", tokens))
                .columns(2)
                .item(hand, true)
                .text(config.string(SELL + ".TEXT", "&f%item% &7x%amount%\n&7Type a price, like &f1500&7, &f2.5k &7or &f1m&7.",
                        tokens), config.integer(SELL + ".TEXT-WIDTH", 250))
                .input(new DialogConfig.InputSpec("price",
                        config.string(SELL + ".INPUT-LABEL", "Price", tokens), 200, 16, "", List.of()))
                .button(config.string(SELL + ".CANCEL-LABEL", "&cCancel", tokens), null, width, DialogActions.CLOSE)
                .commandButton(config.string(SELL + ".SELL-LABEL", "&aList item", tokens),
                        config.string(SELL + ".SELL-TOOLTIP", "List the item in your hand for that price", tokens),
                        width, "ah sell $(price)");
        return show(player, screen.build());
    }

    @Override
    public boolean handle(Player player, String action, DialogResponseView response) {
        if (DialogActions.AH_BACK.equals(action)) {
            DialogSupport.close(player);
            runCommand(player, "ah");
            return true;
        }
        if (action.startsWith(DialogActions.AH_BUY)) {
            long id;
            try {
                id = Long.parseLong(action.substring(DialogActions.AH_BUY.length()));
            } catch (NumberFormatException exception) {
                return true;
            }
            DialogSupport.close(player);
            buy(player, id);
            return true;
        }
        if (DialogActions.CLOSE.equals(action)) {
            DialogSupport.close(player);
            return true;
        }
        return false;
    }

    private void buy(Player player, long listingId) {
        AuctionHouseManager manager = plugin.getAuctionHouseManager();
        AuctionListing listing = manager.getListing(listingId);
        if (listing == null) {
            message(player, plugin.getConfigManager().getMessage("AUCTION_HOUSE.LISTING_NOT_FOUND"));
            return;
        }
        manager.purchaseListing(player, listingId).thenAccept(result -> plugin.getSpigotScheduler().runEntity(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (result.success()) {
                player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage(
                        "AUCTION_HOUSE.PURCHASE_SUCCESS",
                        "{item}", manager.describeItem(listing.item()),
                        "{price}", NumberUtils.format(listing.price()),
                        "{price_formatted}", plugin.getCurrencyManager().formatMoney(listing.price()),
                        "{seller}", listing.sellerName())));
                SoundUtils.play(player, plugin.getConfigManager().getSound("AUCTION_HOUSE.SUCCESS"));
            } else {
                String key = switch (result.reason()) {
                    case DISABLED -> "AUCTION_HOUSE.DISABLED";
                    case NO_PERMISSION -> "AUCTION_HOUSE.NO_PERMISSION";
                    case LISTING_NOT_FOUND -> "AUCTION_HOUSE.LISTING_NOT_FOUND";
                    case NOT_ACTIVE -> "AUCTION_HOUSE.LISTING_NOT_ACTIVE";
                    case OWN_LISTING -> "AUCTION_HOUSE.CANNOT_BUY_OWN";
                    case NO_MONEY -> "AUCTION_HOUSE.NOT_ENOUGH_MONEY";
                    case INVENTORY_FULL -> "AUCTION_HOUSE.FULL_INVENTORY";
                    case NO_PLAYER_DATA, DATABASE_ERROR -> "AUCTION_HOUSE.PURCHASE_DATABASE_ERROR";
                };
                player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage(key)));
                SoundUtils.play(player, plugin.getConfigManager().getSound("AUCTION_HOUSE.FAIL"));
            }
            runCommand(player, "ah");
        }));
    }
}
