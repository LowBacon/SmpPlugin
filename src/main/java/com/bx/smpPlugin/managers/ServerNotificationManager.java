// SmpPlugin by LowBacon
package com.bx.smpPlugin.managers;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.models.AuctionListing;
import com.bx.smpPlugin.models.Order;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.NumberUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import com.bx.smpPlugin.utils.PlayerSettingUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.function.Predicate;

public class ServerNotificationManager {

    private static final String ROOT = "SERVER-NOTIFICATIONS.";

    private final SmpPlugin plugin;

    public ServerNotificationManager(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    public String joinAnnouncement(Player player, boolean firstJoin) {
        if (firstJoin && isEnabled("FIRST-JOIN")) {
            return format(message("FIRST-JOIN", player), "{player}", publicName(player));
        }
        if (!isEnabled("JOIN")) {
            return null;
        }
        return format(message("JOIN", player), "{player}", publicName(player));
    }

    public String leaveAnnouncement(Player player) {
        if (!isEnabled("LEAVE")) {
            return null;
        }
        return format(message("LEAVE", player), "{player}", publicName(player));
    }

    public void announceAuctionListing(Player seller, AuctionListing listing) {
        if (listing == null || isPrivate(seller) || !isEnabled("AUCTION-HOUSE") || !isEnabled("AUCTION-HOUSE.LISTING")) {
            return;
        }
        broadcast(
                format(
                        message("AUCTION-HOUSE.LISTING"),
                        "{player}", publicName(seller),
                        "{item}", describeAuctionItem(listing.item()),
                        "{amount}", String.valueOf(amountOf(listing.item())),
                        "{price}", NumberUtils.format(listing.price()),
                        "{price_formatted}", plugin.getCurrencyManager().formatMoney(listing.price()),
                        "{category}", listing.category()
                ),
                PlayerSettingUtils.NotificationChannel.AUCTION
        );
    }

    public void announceAuctionPurchase(Player buyer, AuctionListing listing) {
        if (listing == null || isPrivate(buyer) || !isEnabled("AUCTION-HOUSE") || !isEnabled("AUCTION-HOUSE.PURCHASE")) {
            return;
        }
        broadcast(
                format(
                        message("AUCTION-HOUSE.PURCHASE"),
                        "{player}", publicName(buyer),
                        "{seller}", sellerName(listing),
                        "{item}", describeAuctionItem(listing.item()),
                        "{amount}", String.valueOf(amountOf(listing.item())),
                        "{price}", NumberUtils.format(listing.price()),
                        "{price_formatted}", plugin.getCurrencyManager().formatMoney(listing.price())
                ),
                PlayerSettingUtils.NotificationChannel.AUCTION
        );
    }

    public void announceOrderCreated(Player owner, Order order) {
        if (order == null || !isEnabled("ORDERS") || !isEnabled("ORDERS.CREATE")) {
            return;
        }
        broadcast(
                format(
                        message("ORDERS.CREATE"),
                        "{player}", publicName(owner),
                        "{item}", describeOrderItem(order.requestedItem()),
                        "{amount}", String.valueOf(order.requestedQuantity()),
                        "{price}", NumberUtils.format(order.priceEach()),
                        "{price_formatted}", plugin.getCurrencyManager().formatMoney(order.priceEach()),
                        "{total}", NumberUtils.format(order.totalBudget()),
                        "{total_formatted}", plugin.getCurrencyManager().formatMoney(order.totalBudget())
                ),
                PlayerSettingUtils.NotificationChannel.ORDER
        );
    }

    public void announceOrderCompleted(Player deliverer, Order order) {
        if (order == null || !isEnabled("ORDERS") || !isEnabled("ORDERS.COMPLETE")) {
            return;
        }
        broadcast(
                format(
                        message("ORDERS.COMPLETE"),
                        "{player}", publicName(deliverer),
                        "{owner}", ownerName(order),
                        "{item}", describeOrderItem(order.requestedItem()),
                        "{amount}", String.valueOf(order.requestedQuantity()),
                        "{price}", NumberUtils.format(order.priceEach()),
                        "{price_formatted}", plugin.getCurrencyManager().formatMoney(order.priceEach()),
                        "{total}", NumberUtils.format(order.totalBudget()),
                        "{total_formatted}", plugin.getCurrencyManager().formatMoney(order.totalBudget())
                ),
                PlayerSettingUtils.NotificationChannel.ORDER
        );
    }

    private boolean isPrivate(Player player) {
        if (player == null || plugin.getPlayerDataManager() == null) {
            return false;
        }
        com.bx.smpPlugin.models.PlayerData data = plugin.getPlayerDataManager().get(player);
        return data != null && data.isPrivateTransactionsEnabled();
    }

    private void broadcast(String message, PlayerSettingUtils.NotificationChannel channel) {
        if (message == null || message.isBlank()) {
            return;
        }
        plugin.getSpigotScheduler().forEachOnlinePlayer(viewer -> {
            if (!PlayerSettingUtils.notificationEnabled(
                    plugin,
                    viewer,
                    PlayerSettingUtils.NotificationChannel.SERVER_BROADCAST
            )) {
                return;
            }
            if (!PlayerSettingUtils.notificationEnabled(plugin, viewer, channel)) {
                return;
            }
            viewer.sendMessage(ColorUtils.toComponent(message));
        });
    }

    static String format(String message, String... replacements) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String result = message;
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            String value = replacements[index + 1];
            result = result.replace(replacements[index], value == null ? "" : value);
        }
        return result;
    }

    private String publicName(Player player) {
        if (player == null) {
            return "";
        }
        return plugin.getHideManager() == null ? player.getName() : plugin.getHideManager().publicName(player);
    }

    private String sellerName(AuctionListing listing) {
        String fallback = listing.sellerName() == null ? "" : listing.sellerName();
        return plugin.getHideManager() == null
                ? fallback
                : plugin.getHideManager().publicName(listing.sellerUuid(), fallback);
    }

    private String ownerName(Order order) {
        String fallback = order.ownerName() == null ? "" : order.ownerName();
        return plugin.getHideManager() == null
                ? fallback
                : plugin.getHideManager().publicName(order.ownerUuid(), fallback);
    }

    private String describeAuctionItem(ItemStack item) {
        return plugin.getAuctionHouseManager() == null
                ? fallbackItemName(item)
                : plugin.getAuctionHouseManager().describeItem(item);
    }

    private String describeOrderItem(ItemStack item) {
        return plugin.getOrdersManager() == null
                ? fallbackItemName(item)
                : plugin.getOrdersManager().describeItem(item);
    }

    private String fallbackItemName(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "Unknown Item";
        }
        return plugin.getWorthManager().prettifyMaterial(item.getType());
    }

    private static int amountOf(ItemStack item) {
        return item == null ? 0 : Math.max(0, item.getAmount());
    }

    private boolean isEnabled(String path) {
        return config().getBoolean(ROOT + path + ".ENABLED", false);
    }

    private String message(String path) {
        return config().getString(ROOT + path + ".MESSAGE", "");
    }

    private String message(String path, Player player) {
        return resolveByPermission(
                config().getConfigurationSection(ROOT + path + ".BY-PERMISSION"),
                message(path),
                permission -> PermissionUtils.hasExact(player, permission)
        );
    }

    static String resolveByPermission(
            ConfigurationSection byPermission,
            String fallback,
            Predicate<String> holdsPermission
    ) {
        if (byPermission == null || holdsPermission == null) {
            return fallback;
        }
        for (Map.Entry<String, Object> entry : byPermission.getValues(true).entrySet()) {
            if (!(entry.getValue() instanceof String wording) || wording.isBlank()) {
                continue;
            }
            if (holdsPermission.test(entry.getKey())) {
                return wording;
            }
        }
        return fallback;
    }

    private FileConfiguration config() {
        return plugin.getConfigManager().getConfig();
    }
}
