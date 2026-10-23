// SmpPlugin by LowBacon
package com.bx.smpPlugin.seasonal;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.Reward;
import com.bx.smpPlugin.menus.BaseMenu;
import com.bx.smpPlugin.utils.ItemUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CalendarEditorMenu extends BaseMenu {

    private static final int BACK_SLOT = 45;
    private static final int HELP_SLOT = 53;

    private final SeasonCalendar calendar;
    private final Map<Integer, Integer> doorBySlot = new HashMap<>();

    public CalendarEditorMenu(SmpPlugin plugin, SeasonCalendar calendar) {
        super(plugin, "&8✎ " + calendar.theme().accent() + "Edit " + calendar.id().displayName() + " prizes", 54);
        this.calendar = calendar;
    }

    @Override
    public void build(Player player) {
        clear();
        doorBySlot.clear();
        fill(Material.BLACK_STAINED_GLASS_PANE);
        for (Map.Entry<Integer, Integer> entry : CalendarMenu.layout(calendar.rules(), calendar.seasonYear()).entrySet()) {
            int slot = entry.getKey();
            int door = entry.getValue();
            doorBySlot.put(slot, door);
            Reward prize = calendar.prize(door);
            List<String> lore = new ArrayList<>();
            lore.add(calendar.hasOverride(door) ? "&aSet in game" : "&7From calendar.yml");
            lore.add("");
            List<String> lines = prize.describe(plugin);
            if (lines.isEmpty()) {
                lore.add("&cNo prize yet");
            }
            for (String line : lines) {
                lore.add("&8• " + line);
            }
            if (!prize.commands().isEmpty()) {
                lore.add("&8• &e" + prize.commands().size() + " command(s)");
            }
            lore.add("");
            lore.add("&e▶ Click to edit");
            ItemStack item = prize.previewItem(plugin, calendar.theme().accent() + "Door " + door + " &8- " + calendar.doorName(door), null);
            item = ItemUtils.withLore(item, lore);
            item.setAmount(Math.max(1, Math.min(64, door)));
            set(slot, item);
        }
        set(BACK_SLOT, ItemUtils.createItem(Material.ARROW, "&7Back to the calendar"));
        set(HELP_SLOT, ItemUtils.createItem(Material.BOOK, "&e&lHow it works", List.of(
                "&7Click a door, then:",
                "&8• &7drop items into the top area",
                "&8• &7click the gold and amethyst",
                "&7  buttons for money and shards",
                "&8• &7press &aSave",
                "",
                "&7Console commands are set in",
                "&fcalendar.yml &7(DOORS.<day>.COMMANDS).")));
    }

    @Override
    public void handleClick(int slot, Player player, ClickType clickType) {
        if (!PermissionUtils.has(player, calendar.adminPermission())) {
            player.closeInventory();
            return;
        }
        if (slot == BACK_SLOT) {
            new CalendarMenu(plugin, calendar).open(player);
            return;
        }
        Integer door = doorBySlot.get(slot);
        if (door != null) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5F, 1.2F);
            new DoorEditorMenu(plugin, calendar, door).open(player);
        }
    }
}
