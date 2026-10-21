package com.bx.smpPlugin.events;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.Branding;
import com.bx.smpPlugin.menus.BaseMenu;
import com.bx.smpPlugin.utils.ItemUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The /event screen: one icon per event with its status and your progress. Click a running event
 * to open it. Staff right-click to switch an event on or off and shift-right-click to put it back
 * on its dates; the manager checks the permission again.
 */
final class EventsMenu extends BaseMenu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
    private static final int CLOSE_SLOT = 31;

    private final EventManager manager;
    private final Map<Integer, SeasonalEvent> eventAt = new HashMap<>();

    EventsMenu(SmpPlugin plugin, EventManager manager) {
        super(plugin, "&8" + Branding.name() + " &e&lEvents", 36);
        this.manager = manager;
    }

    @Override
    public void build(Player player) {
        inventory.clear();
        eventAt.clear();
        fill(Material.BLACK_STAINED_GLASS_PANE);
        boolean staff = PermissionUtils.has(player, EventCommand.ADMIN_PERMISSION);
        int index = 0;
        for (SeasonalEvent event : manager.all()) {
            if (index >= SLOTS.length) {
                break;
            }
            List<String> lore = new ArrayList<>();
            lore.add("&7" + event.description());
            lore.add("");
            lore.add("&7Status: " + manager.statusLine(event));
            if (event.isRunning()) {
                lore.addAll(event.progress(player));
                lore.add("");
                lore.add("&e▶ Click to open");
            }
            if (staff) {
                lore.add("");
                lore.add("&8Staff: right-click to switch " + (event.isRunning() ? "off" : "on"));
                if (event.isManaged()) {
                    lore.add("&8Staff: shift-right-click to follow the dates");
                }
            }
            ItemStack item = ItemUtils.createItem(event.icon(), event.displayName(), lore);
            if (event.isRunning()) {
                ItemUtils.setGlint(item, true);
            }
            eventAt.put(SLOTS[index], event);
            set(SLOTS[index++], item);
        }
        set(CLOSE_SLOT, ItemUtils.createItem(Material.BARRIER, "&cClose"));
    }

    @Override
    public void handleClick(int slot, Player player, ClickType clickType) {
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        SeasonalEvent event = eventAt.get(slot);
        if (event == null) {
            return;
        }
        if (clickType.isRightClick() && PermissionUtils.has(player, EventCommand.ADMIN_PERMISSION)) {
            String action = clickType.isShiftClick() ? "auto" : event.isRunning() ? "disable" : "enable";
            plugin.getSpigotScheduler().dispatchPlayerCommand(player, "event " + action + " " + event.id());
            build(player);
            return;
        }
        if (event.isRunning()) {
            player.closeInventory();
            event.open(player);
        }
    }
}
