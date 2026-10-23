// SmpPlugin by LowBacon
package com.bx.smpPlugin.menus;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.OrdersManager;
import com.bx.smpPlugin.models.Order;
import com.bx.smpPlugin.models.OrderBatchClaimResult;
import com.bx.smpPlugin.models.OrderCollectionClaim;
import com.bx.smpPlugin.models.OrderSort;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.PlayerSettingUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;

public class OrdersEditMenu extends BaseMenu {

    private final long orderId;
    private final boolean backToMyOrders;
    private final int originPage;
    private final OrderSort sortMode;
    private final String categoryFilter;

    public OrdersEditMenu(
            SmpPlugin plugin,
            long orderId,
            boolean backToMyOrders,
            int originPage,
            OrderSort sortMode,
            String categoryFilter
    ) {
        super(plugin, plugin.getOrdersManager().getEditOrderTitle(orderId), plugin.getOrdersManager().getEditOrderSize());
        this.orderId = orderId;
        this.backToMyOrders = backToMyOrders;
        this.originPage = Math.max(1, originPage);
        this.sortMode = sortMode == null ? plugin.getOrdersManager().getDefaultSort() : sortMode;
        this.categoryFilter = categoryFilter == null ? "ALL" : categoryFilter;
    }

    @Override
    public void build(Player player) {
        clear();

        OrdersManager manager = plugin.getOrdersManager();
        Order order = manager.getOrder(orderId);
        if (order == null) {
            return;
        }

        int itemSlot = OrdersMenuSupport.slot(plugin, "GUI.EDIT_ORDER.BUTTONS.ITEM.SLOT", 10);
        int cancelSlot = OrdersMenuSupport.slot(plugin, "GUI.EDIT_ORDER.BUTTONS.CANCEL.SLOT", 13);
        int collectSlot = OrdersMenuSupport.slot(plugin, "GUI.EDIT_ORDER.BUTTONS.COLLECT.SLOT", 15);
        int dropSlot = OrdersMenuSupport.slot(plugin, "GUI.EDIT_ORDER.BUTTONS.DROP.SLOT", 16);

        set(itemSlot, OrdersMenuSupport.createOrderDisplay(plugin, manager, order, true));
        set(cancelSlot, OrdersMenuSupport.button(
                plugin, "GUI.EDIT_ORDER.BUTTONS.CANCEL", "ORDERS.GUI.EDIT_ORDER.CANCEL",
                Material.RED_TERRACOTTA, "&fCancel", List.of("&7&oClick to cancel this order")
        ));
        set(collectSlot, OrdersMenuSupport.button(
                plugin, "GUI.EDIT_ORDER.BUTTONS.COLLECT", "ORDERS.GUI.EDIT_ORDER.COLLECT",
                Material.CHEST, "&fCollect", List.of("&7&oClick to collect items")
        ));
        set(dropSlot, OrdersMenuSupport.button(
                plugin, "GUI.EDIT_ORDER.BUTTONS.DROP", "ORDERS.GUI.EDIT_ORDER.DROP",
                Material.DROPPER, "&eDrop", List.of("&7&oDrop items where you're looking")
        ));
    }

    @Override
    public void handleClick(int slot, Player player, ClickType clickType) {
        Order order = plugin.getOrdersManager().getOrder(orderId);
        if (order == null) {
            player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessageOrDefault(
                    "ORDERS.ORDER_NOT_FOUND",
                    "&cThat order no longer exists."
            )));
            return;
        }

        int cancelSlot = OrdersMenuSupport.slot(plugin, "GUI.EDIT_ORDER.BUTTONS.CANCEL.SLOT", 13);
        int collectSlot = OrdersMenuSupport.slot(plugin, "GUI.EDIT_ORDER.BUTTONS.COLLECT.SLOT", 15);
        int dropSlot = OrdersMenuSupport.slot(plugin, "GUI.EDIT_ORDER.BUTTONS.DROP.SLOT", 16);

        if (slot == collectSlot) {
            OrdersMenuSupport.click(player, plugin);
            claimItems(player, false);
            return;
        }
        if (slot == dropSlot) {
            OrdersMenuSupport.click(player, plugin);
            claimItems(player, true);
            return;
        }
        if (slot == cancelSlot && order.active() && order.ownerUuid().equals(player.getUniqueId())) {
            OrdersMenuSupport.click(player, plugin);
            new OrdersDeleteConfirmMenu(
                    plugin, order.id(), backToMyOrders, originPage, sortMode, categoryFilter
            ).open(player);
        }
    }

    private void claimItems(Player player, boolean dropItems) {
        OrdersManager manager = plugin.getOrdersManager();
        String empty = plugin.getConfigManager().getMessageOrDefault(
                "ORDERS.NO_ITEMS_TO_COLLECT",
                "&cYou have no items to collect"
        );
        if (!manager.isClaimsEnabled()) {
            PlayerSettingUtils.sendActionBar(plugin, player, empty);
            OrdersMenuSupport.play(player, plugin, "ORDERS.FAIL");
            return;
        }
        List<Long> itemClaimIds = manager.getUnclaimedClaims(player.getUniqueId(), orderId).stream()
                .filter(OrderCollectionClaim::itemClaim)
                .map(OrderCollectionClaim::id)
                .toList();
        if (itemClaimIds.isEmpty()) {
            PlayerSettingUtils.sendActionBar(plugin, player, empty);
            OrdersMenuSupport.play(player, plugin, "ORDERS.FAIL");
            return;
        }
        if (!manager.beginAction(player.getUniqueId())) {
            return;
        }
        try {
            OrderBatchClaimResult result = manager.claimBatch(player, itemClaimIds, dropItems);
            if (result.itemClaims() <= 0) {
                PlayerSettingUtils.sendActionBar(plugin, player, empty);
                OrdersMenuSupport.play(player, plugin, "ORDERS.FAIL");
                return;
            }
            if (dropItems) {
                player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessageOrDefault(
                        "ORDERS.BATCH_DROPPED",
                        "&aDropped {claims} claims ({items} items). &c{failed} failed.",
                        "{claims}", String.valueOf(result.itemClaims()),
                        "{items}", String.valueOf(result.itemAmount()),
                        "{failed}", String.valueOf(result.failedClaims())
                )));
                OrdersMenuSupport.play(player, plugin, "ORDERS.DROP");
            } else {
                player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessageOrDefault(
                        "ORDERS.BATCH_COLLECTED",
                        "&aCollected {claims} claims ({items} items, {refund} refund). &c{failed} failed.",
                        "{claims}", String.valueOf(result.itemClaims()),
                        "{items}", String.valueOf(result.itemAmount()),
                        "{refund}", plugin.getCurrencyManager().formatMoney(0D),
                        "{failed}", String.valueOf(result.failedClaims())
                )));
                OrdersMenuSupport.play(player, plugin, "ORDERS.COLLECT");
            }
            openAfterCollect(player);
        } finally {
            manager.endAction(player.getUniqueId());
        }
    }

    private void openAfterCollect(Player player) {
        if (plugin.getOrdersManager().hasUnclaimedItemClaims(player.getUniqueId(), orderId)) {
            new OrdersEditMenu(plugin, orderId, backToMyOrders, originPage, sortMode, categoryFilter).open(player);
            return;
        }
        new OrdersMyOrdersMenu(plugin, 1, sortMode).open(player);
    }
}
