// SmpPlugin by LowBacon
package com.bx.smpPlugin.menus;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.OrdersManager;
import com.bx.smpPlugin.models.Order;
import com.bx.smpPlugin.models.OrderSort;
import com.bx.smpPlugin.utils.ColorUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

public class OrdersDeleteConfirmMenu extends BaseMenu {

    private final long orderId;
    private final boolean backToMyOrders;
    private final int originPage;
    private final OrderSort sortMode;
    private final String categoryFilter;

    public OrdersDeleteConfirmMenu(
            SmpPlugin plugin,
            long orderId,
            boolean backToMyOrders,
            int originPage,
            OrderSort sortMode,
            String categoryFilter
    ) {
        super(
                plugin,
                plugin != null && plugin.getOrdersManager() != null
                        ? plugin.getOrdersManager().getCancelOrderTitle(orderId)
                        : OrdersMenuSupport.text(plugin, "ORDERS.GUI.DELETE.TITLE", "Orders -> Cancel Order"),
                plugin != null && plugin.getOrdersManager() != null
                        ? plugin.getOrdersManager().getCancelOrderSize()
                        : 27
        );
        this.orderId = orderId;
        this.backToMyOrders = backToMyOrders;
        this.originPage = Math.max(1, originPage);
        this.sortMode = sortMode == null ? plugin.getOrdersManager().getDefaultSort() : sortMode;
        this.categoryFilter = plugin.getOrdersManager().normalizeCategory(categoryFilter);
    }

    @Override
    public void build(Player player) {
        clear();
        int backSlot = OrdersMenuSupport.slot(plugin, "GUI.CANCEL_ORDER.BUTTONS.BACK.SLOT", 10);
        int itemSlot = OrdersMenuSupport.slot(plugin, "GUI.CANCEL_ORDER.BUTTONS.ITEM.SLOT", 13);
        int confirmSlot = OrdersMenuSupport.slot(plugin, "GUI.CANCEL_ORDER.BUTTONS.CONFIRM.SLOT", 16);

        Order order = plugin.getOrdersManager().getOrder(orderId);
        if (order != null) {
            set(itemSlot, OrdersMenuSupport.createOrderDisplay(plugin, plugin.getOrdersManager(), order, true));
        }
        set(backSlot, OrdersMenuSupport.button(
                plugin, "GUI.CANCEL_ORDER.BUTTONS.BACK", "ORDERS.GUI.DELETE.BACK",
                Material.RED_STAINED_GLASS_PANE, "&fCancel", List.of("&7&oClick to return")
        ));
        set(confirmSlot, OrdersMenuSupport.button(
                plugin, "GUI.CANCEL_ORDER.BUTTONS.CONFIRM", "ORDERS.GUI.DELETE.CONFIRM",
                Material.LIME_STAINED_GLASS_PANE, "&fConfirm",
                List.of("&7&oClick to cancel order")
        ));
    }

    @Override
    public void handleClick(int slot, Player player) {
        int backSlot = OrdersMenuSupport.slot(plugin, "GUI.CANCEL_ORDER.BUTTONS.BACK.SLOT", 10);
        int confirmSlot = OrdersMenuSupport.slot(plugin, "GUI.CANCEL_ORDER.BUTTONS.CONFIRM.SLOT", 16);
        if (slot == backSlot) {
            OrdersMenuSupport.click(player, plugin);
            openEdit(player);
            return;
        }
        if (slot != confirmSlot) {
            return;
        }

        OrdersManager manager = plugin.getOrdersManager();
        if (!manager.beginAction(player.getUniqueId())) {
            return;
        }
        try {
            manager.updateClickCooldown(player.getUniqueId());
            OrdersManager.CancelOrderResult result = manager.cancelOrder(player, orderId);
            if (!result.success()) {
                player.sendMessage(ColorUtils.toComponent(OrdersMenuSupport.text(
                        plugin, "ORDERS.ORDER_NOT_ACTIVE", "&cThat order can no longer be cancelled."
                )));
                OrdersMenuSupport.play(player, plugin, "ORDERS.FAIL");
                openEdit(player);
                return;
            }
            player.sendMessage(ColorUtils.toComponent(OrdersMenuSupport.text(
                    plugin,
                    "ORDERS.CANCELLED",
                    "&eOrder #{order_id} was cancelled. Remaining money has been refunded.",
                    "{order_id}", String.valueOf(orderId)
            )));
            OrdersMenuSupport.play(player, plugin, "ORDERS.CANCEL");
            new OrdersMyOrdersMenu(plugin, 1, sortMode).open(player);
        } finally {
            manager.endAction(player.getUniqueId());
        }
    }

    private void openEdit(Player player) {
        new OrdersEditMenu(
                plugin, orderId, backToMyOrders, originPage, sortMode, categoryFilter
        ).open(player);
    }
}
