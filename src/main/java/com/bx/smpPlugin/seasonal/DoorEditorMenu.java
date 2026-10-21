package com.bx.smpPlugin.seasonal;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.InteractiveMenu;
import com.bx.smpPlugin.core.Reward;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.menus.BaseMenu;
import com.bx.smpPlugin.utils.ItemUtils;
import com.bx.smpPlugin.utils.NumberUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Edits the prize behind one door. The top four rows hold the prize items: drop items in or take
 * them out like a chest. The bottom row sets money and shards and saves.
 *
 * <p>Nothing changes until Save is pressed. Items left in the area when the menu closes without
 * saving go back to the editor's inventory, so nothing is lost by accident.
 */
public final class DoorEditorMenu extends BaseMenu implements InteractiveMenu {

    private static final int AREA = 36;
    private static final int BACK_SLOT = 45;
    private static final int MONEY_SLOT = 47;
    private static final int SHARDS_SLOT = 48;
    private static final int INFO_SLOT = 49;
    private static final int RESET_SLOT = 51;
    private static final int SAVE_SLOT = 53;

    private final SeasonCalendar calendar;
    private final int door;
    private double money;
    private long shards;
    private boolean saved;

    public DoorEditorMenu(SmpPlugin plugin, SeasonCalendar calendar, int door) {
        super(plugin, "&8✎ " + calendar.theme().accent() + "Door " + door + " prize", 54);
        this.calendar = calendar;
        this.door = door;
        Reward prize = calendar.prize(door);
        this.money = prize.money();
        this.shards = prize.shards();
        int slot = 0;
        for (ItemStack item : prize.items()) {
            if (slot >= AREA) {
                break;
            }
            inventory.setItem(slot++, item.clone());
        }
    }

    @Override
    public void build(Player player) {
        for (int slot = AREA; slot < 54; slot++) {
            inventory.setItem(slot, ItemUtils.createGlassPane(Material.GRAY_STAINED_GLASS_PANE));
        }
        renderButtons();
    }

    private void renderButtons() {
        inventory.setItem(BACK_SLOT, ItemUtils.createItem(Material.ARROW, "&7Back &8(without saving)"));
        inventory.setItem(MONEY_SLOT, ItemUtils.createItem(Material.GOLD_INGOT, "&6Money: &a$" + NumberUtils.formatNice(money), List.of(
                "&7Left click &a+100&7, right click &c-100",
                "&7Shift + left &a+1,000&7, shift + right &c-1,000",
                "&7Middle click (or drop key): &c0")));
        inventory.setItem(SHARDS_SLOT, ItemUtils.createItem(Material.AMETHYST_SHARD, "&dShards: &f" + shards, List.of(
                "&7Left click &a+1&7, right click &c-1",
                "&7Shift + left &a+10&7, shift + right &c-10",
                "&7Middle click (or drop key): &c0")));
        List<String> info = new ArrayList<>();
        info.add("&7Drop the prize items into the");
        info.add("&7area above, then press &aSave&7.");
        List<String> commands = calendar.prize(door).commands();
        if (!commands.isEmpty()) {
            info.add("");
            info.add("&7Also runs (from " + calendar.fileName() + "):");
            for (String command : commands) {
                info.add("&8/ &f" + command);
            }
        }
        inventory.setItem(INFO_SLOT, ItemUtils.createItem(Material.BOOK, "&e" + Text.color(calendar.doorName(door)), info));
        inventory.setItem(RESET_SLOT, ItemUtils.createItem(Material.LAVA_BUCKET, "&cUse " + calendar.fileName() + " again",
                List.of("&7Removes the in-game prize for", "&7this door.", calendar.hasOverride(door) ? "" : "&8(nothing set in game)")));
        inventory.setItem(SAVE_SLOT, ItemUtils.createItem(Material.LIME_DYE, "&a&lSave", List.of("&7Saves this door's prize.")));
    }

    @Override
    public void handleInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }
        if (!PermissionUtils.has(player, calendar.adminPermission())) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }
        // Collecting to the cursor could pull the glass and buttons out of the bottom row.
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= 54) {
            return; // the player's own inventory, including shift-clicks into the empty prize slots
        }
        if (raw < AREA) {
            return; // the prize area behaves like a chest
        }
        event.setCancelled(true);
        clickButton(player, raw, event.getClick());
    }

    @Override
    public void handleInventoryDrag(InventoryDragEvent event) {
        for (int raw : event.getRawSlots()) {
            if (raw >= AREA && raw < 54) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private void clickButton(Player player, int slot, ClickType click) {
        switch (slot) {
            case MONEY_SLOT -> {
                money = Math.max(0D, adjust(money, click, 100D, 1000D));
                renderButtons();
                ping(player);
            }
            case SHARDS_SLOT -> {
                shards = Math.max(0L, (long) adjust(shards, click, 1D, 10D));
                renderButtons();
                ping(player);
            }
            case BACK_SLOT -> plugin.getSpigotScheduler().runEntity(player,
                    () -> new CalendarEditorMenu(plugin, calendar).open(player));
            case RESET_SLOT -> {
                calendar.clearOverride(door);
                saved = true;
                returnItems(player);
                Text.send(player, "CALENDAR.EDIT-RESET", "&7Door {door} uses the prize from {file} again.",
                        "{door}", Integer.toString(door), "{file}", calendar.fileName());
                plugin.getSpigotScheduler().runEntity(player, () -> new CalendarEditorMenu(plugin, calendar).open(player));
            }
            case SAVE_SLOT -> {
                List<ItemStack> items = new ArrayList<>();
                for (int i = 0; i < AREA; i++) {
                    ItemStack item = inventory.getItem(i);
                    if (item != null && !item.getType().isAir()) {
                        items.add(item.clone());
                    }
                }
                calendar.saveOverride(door, new Reward(money, shards, items, List.of(), List.of()));
                saved = true;
                // The items now live in the prize; clear the area so closing does not hand them back.
                for (int i = 0; i < AREA; i++) {
                    inventory.setItem(i, null);
                }
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.4F);
                Text.send(player, "CALENDAR.EDIT-SAVED", "&aSaved the prize behind door {door}.",
                        "{door}", Integer.toString(door));
                plugin.getSpigotScheduler().runEntity(player, () -> new CalendarEditorMenu(plugin, calendar).open(player));
            }
            default -> {
            }
        }
    }

    private static double adjust(double value, ClickType click, double step, double bigStep) {
        return switch (click) {
            case LEFT -> value + step;
            case RIGHT -> value - step;
            case SHIFT_LEFT -> value + bigStep;
            case SHIFT_RIGHT -> value - bigStep;
            case MIDDLE, DROP, CONTROL_DROP -> 0D;
            default -> value;
        };
    }

    private static void ping(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4F, 1.6F);
    }

    @Override
    public void onClose(Player player) {
        if (!saved) {
            returnItems(player);
        }
    }

    /**
     * Gives the editor back what is in the prize area. The area starts with copies of the saved
     * prize, which the editor never owned, so only items that are not part of the saved prize go back.
     */
    private void returnItems(Player player) {
        List<ItemStack> original = new ArrayList<>(calendar.prize(door).items());
        Inventory area = inventory;
        for (int i = 0; i < AREA; i++) {
            ItemStack item = area.getItem(i);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            area.setItem(i, null);
            if (removeMatch(original, item)) {
                continue;
            }
            com.bx.smpPlugin.engagement.PendingItems.giveOrKeep(plugin, player, List.of(item));
        }
    }

    private static boolean removeMatch(List<ItemStack> pool, ItemStack item) {
        for (int i = 0; i < pool.size(); i++) {
            if (pool.get(i).equals(item)) {
                pool.remove(i);
                return true;
            }
        }
        return false;
    }
}
