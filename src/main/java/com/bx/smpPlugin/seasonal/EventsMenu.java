// SmpPlugin by LowBacon
package com.bx.smpPlugin.seasonal;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.MenuFx;
import com.bx.smpPlugin.core.MenuStyle;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.menus.BaseMenu;
import com.bx.smpPlugin.utils.ItemUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class EventsMenu extends BaseMenu {

    private static final int INFO_SLOT = 4;
    private static final int CLOSE_SLOT = 22;
    private static final Map<Integer, EventId> SLOTS = Map.of(11, EventId.ADVENT, 13, EventId.HALLOWEEN,
            15, EventId.TRICK_OR_TREAT);

    private final SeasonalEvents events;

    public EventsMenu(SmpPlugin plugin, SeasonalEvents events) {
        super(plugin, Text.get("EVENTS.MENU-TITLE", "&8» " + MenuStyle.ACCENT + "&lSeasonal Events"), 27);
        this.events = events;
    }

    @Override
    public void open(Player player) {
        super.open(player);
        MenuFx.sound(player, Sound.BLOCK_BEACON_ACTIVATE, 0.4F, 1.6F);
    }

    @Override
    public void build(Player player) {
        clear();
        MenuStyle.frame(inventory, Material.CYAN_STAINED_GLASS_PANE);
        for (int slot = 9; slot < 18; slot++) {
            if (!SLOTS.containsKey(slot) && inventory.getItem(slot) == null) {
                set(slot, MenuStyle.pane(Material.GRAY_STAINED_GLASS_PANE));
            }
        }
        boolean staff = PermissionUtils.has(player, EventsCommand.ADMIN_PERMISSION);
        List<String> info = new ArrayList<>();
        info.add(MenuStyle.MUTED + "Seasonal events start and end");
        info.add(MenuStyle.MUTED + "by themselves on their dates.");
        if (staff) {
            info.add("");
            info.add(MenuStyle.WARN + "Staff: " + MenuStyle.MUTED + "switching an event never");
            info.add(MenuStyle.MUTED + "changes its dates or anyone's progress.");
            if (!events.isStorageReadable()) {
                info.add("");
                info.add(MenuStyle.BAD + "data/events.yml is damaged: switches are locked.");
            }
        }
        set(INFO_SLOT, ItemUtils.createItem(Material.CLOCK, MenuStyle.ACCENT + "&lSeasonal Events", info));
        for (Map.Entry<Integer, EventId> entry : SLOTS.entrySet()) {
            set(entry.getKey(), eventItem(entry.getValue(), staff));
        }
        set(CLOSE_SLOT, MenuStyle.close());
    }

    private ItemStack eventItem(EventId id, boolean staff) {
        EventStatus status = events.status(id);
        List<String> body = new ArrayList<>(EventsCommand.describe(events, id, staff));
        List<String> actions = new ArrayList<>();
        if (staff) {
            actions.add("Left click: force on");
            actions.add("Right click: switch off");
            actions.add("Shift click: follow the dates");
            if (status == EventStatus.ACTIVE) {
                actions.add("Press Q: join in");
            }
        } else if (status == EventStatus.ACTIVE) {
            actions.add("Click to join in");
        }
        ItemStack item = ItemUtils.createItem(icon(id, status), colour(status) + "&l" + id.displayName(),
                MenuStyle.lore(body, actions.toArray(String[]::new)));
        return ItemUtils.setGlint(item, status == EventStatus.ACTIVE);
    }

    private static Material icon(EventId id, EventStatus status) {
        if (status == EventStatus.DISABLED) {
            return Material.GRAY_DYE;
        }
        return switch (id) {
            case ADVENT -> Material.SNOW_BLOCK;
            case HALLOWEEN -> Material.JACK_O_LANTERN;
            case TRICK_OR_TREAT -> Material.CAULDRON;
        };
    }

    private static String colour(EventStatus status) {
        return switch (status) {
            case ACTIVE -> MenuStyle.GOOD;
            case SCHEDULED -> MenuStyle.WARN;
            case COMPLETED -> MenuStyle.MUTED;
            case DISABLED -> MenuStyle.BAD;
        };
    }

    @Override
    public void handleClick(int slot, Player player, ClickType clickType) {
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        EventId id = SLOTS.get(slot);
        if (id == null) {
            return;
        }
        // Checked again here: permissions can change while the menu is open.
        if (PermissionUtils.has(player, EventsCommand.ADMIN_PERMISSION) && clickType != ClickType.DROP
                && clickType != ClickType.CONTROL_DROP) {
            EventMode mode = clickType.isShiftClick() ? EventMode.AUTO
                    : clickType.isRightClick() ? EventMode.OFF
                    : clickType.isLeftClick() ? EventMode.ON : null;
            if (mode != null) {
                EventsCommand.apply(plugin, events, player, id, mode);
                MenuFx.sound(player, mode == EventMode.OFF ? Sound.BLOCK_BEACON_DEACTIVATE : Sound.BLOCK_BEACON_POWER_SELECT,
                        0.6F, 1.2F);
                if (MenuFx.isViewing(player, this)) {
                    build(player);
                }
                return;
            }
        }
        if (!PermissionUtils.has(player, EventsCommand.PERMISSION)) {
            player.closeInventory();
            Text.send(player, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return;
        }
        if (!events.isActive(id)) {
            MenuFx.sound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6F, 0.6F);
            Text.send(player, "EVENTS.NOT-RUNNING", "&7{event} is not running right now: {status}&7.",
                    "{event}", id.displayName(), "{status}", EventsCommand.statusText(events, id));
            build(player);
            return;
        }
        events.source(id).ifPresent(source -> {
            player.closeInventory();
            plugin.getSpigotScheduler().dispatchPlayerCommand(player, source.command());
        });
    }
}
