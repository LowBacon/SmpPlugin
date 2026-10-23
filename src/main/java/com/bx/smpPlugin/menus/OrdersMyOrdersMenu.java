// SmpPlugin by LowBacon
package com.bx.smpPlugin.menus;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.models.Order;
import com.bx.smpPlugin.models.OrderSort;
import com.bx.smpPlugin.utils.ItemUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

public class OrdersMyOrdersMenu extends BaseMenu {

    static final int GRID_SLOTS = 45;
    static final int DEFAULT_PLUS_PAGE_SLOTS = 45;
    private static final int ITEMS_PER_PAGE = GRID_SLOTS;

    private final int page;
    private final OrderSort sortMode;
    private final String query;

    public OrdersMyOrdersMenu(SmpPlugin plugin, int ignoredPage, OrderSort sortMode) {
        this(plugin, ignoredPage, sortMode, "");
    }

    public OrdersMyOrdersMenu(SmpPlugin plugin, int page, OrderSort sortMode, String query) {
        super(plugin, plugin.getOrdersManager().getMyOrdersTitle(), plugin.getOrdersManager().getMyOrdersSize());
        this.page = Math.max(1, Math.min(2, page));
        this.sortMode = sortMode == null ? plugin.getOrdersManager().getDefaultSort() : sortMode;
        this.query = query == null ? "" : query.trim();
    }

    @Override
    public void build(Player player) {
        clear();
        boolean hasPlusPlus = plugin.getOrdersManager() != null && plugin.getOrdersManager().hasDonutPlusPlus(player);
        if (page > 1 && !hasPlusPlus) {
            buildPlusPage();
            return;
        }

        List<Order> orders = visibleOrders(player);
        int start = (page - 1) * ITEMS_PER_PAGE;
        int visualSlots = plugin.getOrdersManager().getMyOrdersVisibleSlots(player);
        int maxOrders = plugin.getOrdersManager().getMaxActiveOrders(player);

        for (int slot = 0; slot < ITEMS_PER_PAGE && slot < inventory.getSize() - 9; slot++) {
            int orderIndex = start + slot;
            if (orderIndex < orders.size()) {
                set(slot, OrdersMenuSupport.createOrderDisplay(
                        plugin,
                        plugin.getOrdersManager(),
                        orders.get(orderIndex),
                        true
                ));
            } else if (slot < visualSlots && orders.size() < maxOrders) {
                set(slot, OrdersMenuSupport.button(
                        plugin,
                        "GUI.MY_ORDERS.BUTTONS.NEW",
                        "ORDERS.GUI.MY_ORDERS.NEW",
                        Material.GRAY_STAINED_GLASS_PANE,
                        "&aNew Order",
                        List.of("&fClick to create new order")
                ));
            } else {
                set(slot, lockedPane(false));
            }
        }

        OrdersMenuSupport.placeNavigation(
                this,
                player,
                false,
                true,
                page,
                false,
                true,
                sortMode
        );
    }

    @Override
    public void handleClick(int slot, Player player, ClickType clickType) {
        boolean hasPrev = page > 1;
        boolean hasNext = page == 1;

        if (OrdersMenuSupport.handleNavigationClick(
                this, player, slot, clickType, page, hasPrev, hasNext,
                sortMode, "ALL", query, true
        )) {
            return;
        }

        boolean hasPlusPlus = plugin.getOrdersManager() != null && plugin.getOrdersManager().hasDonutPlusPlus(player);
        if (page > 1 && !hasPlusPlus) {
            int configuredSlots = plugin.getOrdersManager() != null
                    ? plugin.getOrdersManager().getMyOrdersPlusPageSlots()
                    : DEFAULT_PLUS_PAGE_SLOTS;
            int contentSlots = Math.min(configuredSlots, Math.max(0, inventory.getSize() - 9));
            if (slot >= 0 && slot < contentSlots) {
                OrdersMenuSupport.click(player, plugin);
            }
            return;
        }

        List<Order> orders = visibleOrders(player);
        int start = (page - 1) * ITEMS_PER_PAGE;
        int visualSlots = plugin.getOrdersManager().getMyOrdersVisibleSlots(player);
        int maxOrders = plugin.getOrdersManager().getMaxActiveOrders(player);
        int orderIndex = start + slot;

        if (slot >= 0 && slot < ITEMS_PER_PAGE && orderIndex < orders.size()) {
            OrdersMenuSupport.click(player, plugin);
            new OrdersEditMenu(plugin, orders.get(orderIndex).id(), true, page, sortMode, "ALL").open(player);
            return;
        }

        if (slot >= 0 && slot < visualSlots && orders.size() < maxOrders) {
            OrdersMenuSupport.click(player, plugin);
            plugin.getOrdersManager().openNewOrderMenu(player);
        } else if (slot >= 0 && slot < ITEMS_PER_PAGE) {
            OrdersMenuSupport.click(player, plugin);
        }
    }

    private void buildPlusPage() {
        // Next Page/main.png fills slots 0-44. A 36-slot loop left row 5 empty.
        int configuredSlots = plugin.getOrdersManager() != null
                ? plugin.getOrdersManager().getMyOrdersPlusPageSlots()
                : DEFAULT_PLUS_PAGE_SLOTS;
        int contentSlots = Math.min(configuredSlots, Math.max(0, inventory.getSize() - 9));
        for (int slot = 0; slot < contentSlots; slot++) {
            set(slot, lockedPane(true));
        }
        OrdersMenuSupport.placeNavigation(
                this,
                null,
                false,
                false,
                page,
                true,
                false,
                sortMode
        );
    }

    private ItemStack lockedPane(boolean plusPlus) {
        String path = plusPlus ? "ORDERS.GUI.MY_ORDERS.LOCKED_PLUS" : "ORDERS.GUI.MY_ORDERS.LOCKED";
        String loreFallback = plusPlus
                ? "&fBuy &bDonut++&f for even more order slots"
                : "&fBuy Donut&b+&f for more order slots";
        return ItemUtils.createItem(
                OrdersMenuSupport.material(plugin, "GUI.MY_ORDERS.BUTTONS.LOCKED.MATERIAL", Material.RED_STAINED_GLASS_PANE),
                OrdersMenuSupport.text(plugin, path + ".NAME", "&cMax Orders"),
                OrdersMenuSupport.list(plugin, path + ".LORE", List.of(loreFallback))
        );
    }

    private List<Order> visibleOrders(Player player) {
        List<Order> orders = plugin.getOrdersManager().getOrdersForOwner(player.getUniqueId(), sortMode);
        if (query.isBlank()) {
            return orders;
        }
        String normalized = query.toLowerCase(Locale.ROOT);
        return orders.stream()
                .filter(order -> plugin.getOrdersManager().describeItem(order.requestedItem())
                        .toLowerCase(Locale.ROOT).contains(normalized)
                        || order.requestedMaterialKey().toLowerCase(Locale.ROOT).contains(normalized))
                .toList();
    }
}
