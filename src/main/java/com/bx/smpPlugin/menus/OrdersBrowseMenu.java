// SmpPlugin by LowBacon
package com.bx.smpPlugin.menus;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.OrdersManager;
import com.bx.smpPlugin.models.Order;
import com.bx.smpPlugin.models.OrderSort;
import com.bx.smpPlugin.models.OrderUiState;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;
import java.util.Locale;

public class OrdersBrowseMenu extends BaseMenu {

    private final int page;
    private final OrderSort sortMode;
    private final String categoryFilter;
    private final String query;

    public OrdersBrowseMenu(SmpPlugin plugin, int page, OrderSort sortMode, String categoryFilter) {
        this(plugin, page, sortMode, categoryFilter, "");
    }

    public OrdersBrowseMenu(SmpPlugin plugin, int page, OrderSort sortMode, String categoryFilter, String query) {
        super(plugin, plugin.getOrdersManager().getBrowseTitle(page), plugin.getOrdersManager().getBrowseSize());
        this.page = Math.max(1, page);
        this.sortMode = sortMode == null ? plugin.getOrdersManager().getDefaultSort() : sortMode;
        this.categoryFilter = plugin.getOrdersManager().normalizeCategory(categoryFilter);
        this.query = query == null ? "" : query.trim();
    }

    @Override
    public void build(Player player) {
        clear();

        OrdersManager manager = plugin.getOrdersManager();
        OrderUiState state = manager.getUiState(player.getUniqueId());
        state.page(page - 1);
        state.sort(sortMode);
        state.filter(categoryFilter);
        state.search(query);

        List<Order> orders = visibleOrders();
        int itemsPerPage = Math.min(45, manager.getBrowseItemsPerPage());
        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = Math.min(orders.size(), startIndex + itemsPerPage);
        for (int slot = 0; slot < itemsPerPage; slot++) {
            int orderIndex = startIndex + slot;
            if (orderIndex >= endIndex) {
                break;
            }
            Order order = orders.get(orderIndex);
            set(slot, OrdersMenuSupport.createOrderDisplay(
                    plugin,
                    manager,
                    order,
                    order.ownerUuid().equals(player.getUniqueId())
            ));
        }

        OrdersMenuSupport.placeNavigation(
                this,
                player,
                true,
                false,
                page,
                page > 1,
                hasNextPage(orders.size(), itemsPerPage),
                sortMode
        );
    }

    @Override
    public void handleClick(int slot, Player player, ClickType clickType) {
        OrdersManager manager = plugin.getOrdersManager();
        List<Order> orders = visibleOrders();
        int itemsPerPage = Math.min(45, manager.getBrowseItemsPerPage());
        boolean hasNext = hasNextPage(orders.size(), itemsPerPage);

        if (OrdersMenuSupport.handleNavigationClick(
                this, player, slot, clickType, page, page > 1, hasNext,
                sortMode, categoryFilter, query, false
        )) {
            return;
        }
        if (slot < 0 || slot >= itemsPerPage) {
            return;
        }

        int orderIndex = ((page - 1) * itemsPerPage) + slot;
        if (orderIndex >= orders.size()) {
            return;
        }
        Order selected = orders.get(orderIndex);
        OrdersMenuSupport.click(player, plugin);
        if (selected.ownerUuid().equals(player.getUniqueId())) {
            new OrdersEditMenu(plugin, selected.id(), true, page, sortMode, categoryFilter).open(player);
        } else if (manager.getDeliveryMode() == OrdersManager.DeliveryMode.DEPOSIT_GUI) {
            new OrdersDepositMenu(plugin, selected.id(), page, sortMode, categoryFilter).open(player);
        } else {
            new OrdersDeliverConfirmMenu(plugin, selected.id(), page, sortMode, categoryFilter).open(player);
        }
    }

    private List<Order> visibleOrders() {
        List<Order> orders = plugin.getOrdersManager().getActiveOrders(sortMode, categoryFilter);
        if (query.isBlank()) {
            return orders;
        }
        String normalized = query.toLowerCase(Locale.ROOT);
        return orders.stream()
                .filter(order -> plugin.getOrdersManager().describeItem(order.requestedItem())
                        .toLowerCase(Locale.ROOT).contains(normalized)
                        || order.requestedMaterialKey().toLowerCase(Locale.ROOT).contains(normalized)
                        || (plugin.getHideManager() != null
                                ? plugin.getHideManager().publicName(order.ownerUuid(), order.ownerName())
                                : order.ownerName()).toLowerCase(Locale.ROOT).contains(normalized))
                .toList();
    }

    private boolean hasNextPage(int totalItems, int itemsPerPage) {
        return page < Math.max(1, (int) Math.ceil(totalItems / (double) itemsPerPage));
    }
}
