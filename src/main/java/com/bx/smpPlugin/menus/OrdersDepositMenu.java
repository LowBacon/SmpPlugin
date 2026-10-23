// SmpPlugin by LowBacon
package com.bx.smpPlugin.menus;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.OrdersManager;
import com.bx.smpPlugin.models.DeliveryQuote;
import com.bx.smpPlugin.models.DeliveryRequest;
import com.bx.smpPlugin.models.Order;
import com.bx.smpPlugin.models.OrderSort;
import com.bx.smpPlugin.utils.ColorUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class OrdersDepositMenu extends BaseMenu {

    private final long orderId;
    private final int originPage;
    private final OrderSort sortMode;
    private final String categoryFilter;
    private boolean finalized;

    public OrdersDepositMenu(
            SmpPlugin plugin,
            long orderId,
            int originPage,
            OrderSort sortMode,
            String categoryFilter
    ) {
        super(
                plugin,
                OrdersMenuSupport.text(plugin, "ORDERS.GUI.DEPOSIT.TITLE", "Orders -> Deliver Items"),
                plugin.getConfigManager().getOrders().getInt("GUI.DELIVERY_DEPOSIT.SIZE", 36)
        );
        this.orderId = orderId;
        this.originPage = Math.max(1, originPage);
        this.sortMode = sortMode == null ? plugin.getOrdersManager().getDefaultSort() : sortMode;
        this.categoryFilter = plugin.getOrdersManager().normalizeCategory(categoryFilter);
    }

    @Override
    public void build(Player player) {
        clear();
        Order order = plugin.getOrdersManager().getOrder(orderId);
        if (order == null || !order.active() || order.ownerUuid().equals(player.getUniqueId())) {
            player.sendMessage(ColorUtils.toComponent(OrdersMenuSupport.text(
                    plugin, "ORDERS.ORDER_NOT_ACTIVE", "&cThat order is no longer available."
            )));
            plugin.getSpigotScheduler().runEntityLater(player, () ->
                    new OrdersBrowseMenu(plugin, originPage, sortMode, categoryFilter).open(player), 1L);
        }
    }

    public void handleInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            event.setCancelled(true);
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < inventory.getSize()) {
            event.setCancelled(false);
            return;
        }
        event.setCancelled(false);
    }

    public void handleInventoryDrag(InventoryDragEvent event) {
        event.setCancelled(false);
    }

    @Override
    public void onClose(Player player) {
        if (finalized) {
            return;
        }
        finalized = true;
        List<ItemStack> deposited = takeDepositedItems();
        if (deposited.isEmpty()) {
            return;
        }
        deliver(player, deposited);
    }

    private void deliver(Player player, List<ItemStack> deposited) {
        OrdersManager manager = plugin.getOrdersManager();
        if (!manager.beginAction(player.getUniqueId())) {
            manager.giveOrDrop(player, deposited);
            return;
        }
        try {
            DeliveryQuote quote = manager.quoteDelivery(player, orderId, deposited);
            manager.giveOrDrop(player, quote.returnedItems());
            if (!quote.success()) {
                player.sendMessage(ColorUtils.toComponent(resolveFailure(quote.failureCode())));
                OrdersMenuSupport.play(player, plugin, "ORDERS.FAIL");
                return;
            }

            OrdersManager.DeliverOrderResult result = manager.deliverOrder(
                    player,
                    new DeliveryRequest(orderId, quote.acceptedItems(), quote.quantity(), quote.order().priceEach())
            );
            if (!result.success()) {
                manager.giveOrDrop(player, quote.acceptedItems());
                player.sendMessage(ColorUtils.toComponent(resolveFailure(
                        result.reason() == null ? "" : result.reason().name()
                )));
                OrdersMenuSupport.play(player, plugin, "ORDERS.FAIL");
                return;
            }

            player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessageOrDefault(
                    "ORDERS.DELIVERY_SUCCESS",
                    "&aDelivered &e{quantity} {item}&a and received &a{payout}&a.",
                    "{quantity}", String.valueOf(result.deliveredQuantity()),
                    "{item}", manager.describeItem(quote.order().requestedItem()),
                    "{payout}", plugin.getCurrencyManager().formatMoney(result.payout())
            )));
            OrdersMenuSupport.play(player, plugin, "ORDERS.DELIVER");
        } finally {
            manager.endAction(player.getUniqueId());
        }
    }

    private List<ItemStack> takeDepositedItems() {
        List<ItemStack> items = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                items.add(item.clone());
                inventory.setItem(slot, null);
            }
        }
        return items;
    }

    private String resolveFailure(String failureCode) {
        return switch (failureCode == null ? "" : failureCode) {
            case "OWN_ORDER" -> OrdersMenuSupport.text(plugin, "ORDERS.CANNOT_DELIVER_OWN", "&cYou cannot deliver to your own order.");
            case "NO_MATCHING_ITEMS" -> OrdersMenuSupport.text(plugin, "ORDERS.NO_MATCHING_ITEMS", "&cNo matching items were deposited.");
            case "ORDER_FULL" -> OrdersMenuSupport.text(plugin, "ORDERS.ORDER_FULL", "&cThat order is already full.");
            case "PAYOUT_ERROR", "DELIVERY_FAILED_ECONOMY" -> OrdersMenuSupport.text(plugin, "ORDERS.DELIVERY_FAILED_ECONOMY", "&cThe order escrow could not cover this delivery.");
            default -> OrdersMenuSupport.text(plugin, "ORDERS.ORDER_NOT_ACTIVE", "&cThat order is no longer active.");
        };
    }
}
