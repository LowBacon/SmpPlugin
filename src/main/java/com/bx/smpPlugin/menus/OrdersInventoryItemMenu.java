// SmpPlugin by LowBacon
package com.bx.smpPlugin.menus;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.OrdersManager;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.ItemUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class OrdersInventoryItemMenu extends BaseMenu {

    private final long editOrderId;
    private final OrdersManager.OrderEditNavigation editNavigation;

    public OrdersInventoryItemMenu(SmpPlugin plugin) {
        super(plugin, plugin.getOrdersManager().getInventoryItemTitle(), plugin.getOrdersManager().getInventoryItemSize());
        this.editOrderId = 0L;
        this.editNavigation = null;
    }

    public OrdersInventoryItemMenu(SmpPlugin plugin, long editOrderId, OrdersManager.OrderEditNavigation editNavigation) {
        super(plugin, plugin.getOrdersManager().getInventoryItemTitle(), plugin.getOrdersManager().getInventoryItemSize());
        this.editOrderId = editOrderId;
        this.editNavigation = editNavigation;
    }

    @Override
    public void build(Player player) {
        clear();
        fill(Material.GRAY_STAINED_GLASS_PANE);

        int lastRow = inventory.getSize() - 9;
        set(11, ItemUtils.createItem(
                Material.HOPPER,
                "&bChoose from inventory",
                List.of(
                        "&7Click an item in your inventory.",
                        "&7The item is cloned as the order template.",
                        "&7Your original item stays in your inventory."
                )
        ));
        set(15, ItemUtils.createItem(
                Material.CHEST,
                "&eExact item matching",
                List.of(
                        "&7Custom names, lore, enchantments,",
                        "&7NBT, and shulker contents must match."
                )
        ));
        set(lastRow, ItemUtils.createItem(Material.COMPASS, "&bBack to orders", List.of("&7Return to the order board")));
        set(lastRow + 8, ItemUtils.createPlaceholder(Material.GRAY_STAINED_GLASS_PANE));
    }

    public void handleInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        event.setCancelled(true);

        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < inventory.getSize()) {
            handleTopClick(rawSlot, player);
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }

        boolean started = plugin.getOrdersManager().selectOrderItem(
                player,
                clicked,
                plugin.getOrdersManager().resolveCategoryForMaterial(clicked.getType()),
                editOrderId,
                editNavigation
        );
        OrdersMenuSupport.play(player, plugin, started ? "ORDERS.CLICK" : "ORDERS.FAIL");
    }

    public void handleInventoryDrag(InventoryDragEvent event) {
        event.setCancelled(true);
    }

    private boolean isEditMode() {
        return editOrderId > 0L;
    }

    private void openEditMenu(Player player) {
        new OrdersEditMenu(
                plugin,
                editOrderId,
                editNavigation != null && editNavigation.backToMyOrders(),
                editNavigation == null ? 1 : editNavigation.originPage(),
                editNavigation == null ? plugin.getOrdersManager().getDefaultSort() : editNavigation.sortMode(),
                editNavigation == null ? "ALL" : editNavigation.categoryFilter()
        ).open(player);
    }

    private void handleTopClick(int slot, Player player) {
        int lastRow = inventory.getSize() - 9;
        if (slot == lastRow) {
            OrdersMenuSupport.click(player, plugin);
            if (isEditMode()) {
                openEditMenu(player);
            } else {
                new OrdersBrowseMenu(plugin, 1, plugin.getOrdersManager().getDefaultSort(), "ALL").open(player);
            }
            return;
        }

        if (slot == lastRow + 8) {
            return;
        }

        player.sendMessage(ColorUtils.toComponent("&7Click an item in your inventory to use it as the order template."));
    }
}
