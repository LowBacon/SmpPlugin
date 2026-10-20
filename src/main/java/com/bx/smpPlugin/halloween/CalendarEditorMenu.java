package com.bx.smpPlugin.halloween;

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

/** /halloween edit: every door with its prize; click one to change what is behind it. */
public final class CalendarEditorMenu extends BaseMenu {

    private static final int BACK_SLOT = 45;
    private static final int HELP_SLOT = 53;

    private final HalloweenManager halloween;
    private final Map<Integer, Integer> doorBySlot = new HashMap<>();

    public CalendarEditorMenu(SmpPlugin plugin, HalloweenManager halloween) {
        super(plugin, "&8✎ &6Edit Halloween prizes", 54);
        this.halloween = halloween;
    }

    @Override
    public void build(Player player) {
        clear();
        doorBySlot.clear();
        fill(Material.BLACK_STAINED_GLASS_PANE);
        CalendarRules rules = halloween.rules();
        int year = halloween.seasonYear();
        int lastDoor = rules.lastDayIn(year);
        int index = 0;
        for (int door = rules.firstDay(); door <= lastDoor; door++) {
            int slot;
            if (door == lastDoor) {
                slot = 49;
            } else if (index < CalendarMenu.DOOR_SLOTS.length - 1) {
                slot = CalendarMenu.DOOR_SLOTS[index++];
            } else {
                continue;
            }
            doorBySlot.put(slot, door);
            Reward prize = halloween.prize(door);
            List<String> lore = new ArrayList<>();
            lore.add(halloween.hasOverride(door) ? "&aSet in game" : "&7From halloween.yml");
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
            ItemStack item = prize.previewItem(plugin, "&6Door " + door + " &8- " + halloween.doorName(door), null);
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
                "&fhalloween.yml &7(DOORS.<day>.COMMANDS).")));
    }

    @Override
    public void handleClick(int slot, Player player, ClickType clickType) {
        if (!PermissionUtils.has(player, HalloweenCommand.ADMIN_PERMISSION)) {
            player.closeInventory();
            return;
        }
        if (slot == BACK_SLOT) {
            new CalendarMenu(plugin, halloween).open(player);
            return;
        }
        Integer door = doorBySlot.get(slot);
        if (door != null) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5F, 1.2F);
            new DoorEditorMenu(plugin, halloween, door).open(player);
        }
    }
}
