package com.bx.smpPlugin.halloween;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.MenuFx;
import com.bx.smpPlugin.core.Reward;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.halloween.CalendarRules.DoorState;
import com.bx.smpPlugin.menus.BaseMenu;
import com.bx.smpPlugin.utils.DialogGateway;
import com.bx.smpPlugin.utils.ItemUtils;
import com.bx.smpPlugin.utils.NoticeSpec;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /halloween: the Spooky Calendar. One door per day; today's door spins and reveals its prize.
 *
 * <pre>
 *  row 0   frame ........ [info] ........ frame
 *  rows 1-4  doors 1-28, seven per row
 *  row 5   frame [edit] [29] [30] [31] frame [trick or treat] frame [close]
 * </pre>
 */
public final class CalendarMenu extends BaseMenu {

    static final int[] DOOR_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43,
            47, 48, 49
    };
    private static final int INFO_SLOT = 4;
    private static final int EDIT_SLOT = 46;
    private static final int TRICK_SLOT = 51;
    private static final int CLOSE_SLOT = 53;

    private final HalloweenManager halloween;
    private final Map<Integer, Integer> doorBySlot = new HashMap<>();
    private boolean busy;

    public CalendarMenu(SmpPlugin plugin, HalloweenManager halloween) {
        super(plugin, halloween.config().getString("CALENDAR.TITLE", halloween.theme().defaultTitle()), 54);
        this.halloween = halloween;
    }

    @Override
    public void open(Player player) {
        build(player);
        player.openInventory(inventory);
        MenuFx.sound(player, Sound.BLOCK_CHEST_OPEN, 0.6F, 0.7F);
        MenuFx.sound(player, halloween.theme().ambientSound(), 0.4F, 1.2F);
        if (halloween.config().getBoolean("CALENDAR.OPEN-ANIMATION", true)) {
            Map<Integer, ItemStack> doors = new LinkedHashMap<>();
            for (int slot : doorBySlot.keySet()) {
                doors.put(slot, inventory.getItem(slot));
            }
            MenuFx.wave(plugin, player, this, inventory, doors, ItemUtils.createGlassPane(Material.BLACK_STAINED_GLASS_PANE));
        }
        if (halloween.config().getBoolean("CALENDAR.BORDER-ANIMATION", true)) {
            MenuFx.shimmer(plugin, player, this, inventory, frameSlots(player), borderColours(), 8L);
        }
    }

    @Override
    public void build(Player player) {
        clear();
        doorBySlot.clear();
        List<Material> colours = borderColours();
        for (int slot : frameSlots(player)) {
            set(slot, ItemUtils.createGlassPane(colours.get(slot % colours.size())));
        }
        set(INFO_SLOT, infoItem(player));

        CalendarRules rules = halloween.rules();
        int year = halloween.seasonYear();
        int lastDoor = rules.lastDayIn(year);
        int index = 0;
        for (int door = rules.firstDay(); door <= lastDoor; door++) {
            int slot;
            if (door == lastDoor) {
                // The last door (Halloween itself) always sits in the middle of the bottom row.
                slot = 49;
            } else if (index < DOOR_SLOTS.length - 1) {
                slot = DOOR_SLOTS[index++];
            } else {
                continue;
            }
            doorBySlot.put(slot, door);
            set(slot, doorItem(player, door));
        }

        if (PermissionUtils.has(player, halloween.theme().adminPermission())) {
            set(EDIT_SLOT, ItemUtils.createItem(Material.WRITABLE_BOOK, "&e&lEdit prizes",
                    List.of("&7Choose what is behind each door.", "", "&e▶ Click to edit")));
        }
        if (trickOrTreat()) {
            set(TRICK_SLOT, ItemUtils.createItem(Material.CAULDRON, "&5&lTrick or Treat!",
                    List.of("&7Knock on a door and hope for", "&7candy... or a trick.", "",
                            "&e▶ Click or use /trickortreat")));
        }
        set(CLOSE_SLOT, ItemUtils.createItem(Material.BARRIER, "&cClose"));
    }

    private List<Integer> frameSlots(Player player) {
        List<Integer> frame = new ArrayList<>(MenuFx.borderSlots(6));
        frame.removeIf(slot -> slot == INFO_SLOT || slot == TRICK_SLOT || slot == CLOSE_SLOT
                || slot == 47 || slot == 48 || slot == 49);
        if (PermissionUtils.has(player, halloween.theme().adminPermission())) {
            frame.remove(Integer.valueOf(EDIT_SLOT));
        }
        return frame;
    }

    private boolean trickOrTreat() {
        return halloween.theme().trickOrTreatButton() && halloween.config().getBoolean("TRICK-OR-TREAT.ENABLED", true);
    }

    private List<Material> borderColours() {
        List<Material> colours = new ArrayList<>();
        for (String raw : halloween.config().getStringList("CALENDAR.BORDER-COLOURS")) {
            Material material = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
            if (material != null && material.isItem()) {
                colours.add(material);
            }
        }
        if (colours.isEmpty()) {
            colours.addAll(halloween.theme().defaultBorder());
        }
        return colours;
    }

    private ItemStack infoItem(Player player) {
        CalendarRules rules = halloween.rules();
        ZonedDateTime now = halloween.now();
        int year = halloween.seasonYear();
        List<String> lore = new ArrayList<>();
        String accent = halloween.theme().accent();
        lore.addAll(halloween.theme().intro());
        lore.add("");
        lore.add("&7Doors opened: " + accent + halloween.openedCount(player.getUniqueId()) + "&7/" + accent + rules.doorCount(year));
        if (!halloween.inSeason()) {
            long wait = rules.secondsUntilSeason(now);
            lore.add(wait > 0 ? "&7The calendar opens in &f" + Text.duration(wait) : "&7This year's calendar has closed.");
        } else {
            long untilLastDay = rules.secondsUntilLastDay(now);
            lore.add(untilLastDay > 0 ? "&7" + halloween.theme().countdownLabel() + " &f" + Text.duration(untilLastDay)
                    : halloween.theme().lastDayText());
            int ready = halloween.claimableCount(player.getUniqueId());
            lore.add(ready > 0 ? "&a" + ready + " door" + (ready == 1 ? "" : "s") + " ready to open!" : "&7Come back tomorrow for the next door.");
        }
        if (halloween.previewDate() != null) {
            lore.add("");
            lore.add("&dPreview date: " + halloween.previewDate());
        }
        ItemStack item = ItemUtils.createItem(halloween.theme().infoIcon(),
                accent + "&l" + halloween.theme().calendarName() + " &8" + year, lore);
        return ItemUtils.setGlint(item, true);
    }

    private ItemStack doorItem(Player player, int door) {
        DoorState state = halloween.state(player.getUniqueId(), door);
        boolean last = halloween.isLastDoor(door);
        String name = halloween.doorName(door);
        Reward prize = halloween.prize(door);
        ItemStack item;
        switch (state) {
            case OPEN -> {
                List<String> lore = new ArrayList<>();
                lore.add(last ? halloween.theme().lastDoorLore() : halloween.theme().doorLore());
                if (halloween.config().getBoolean("CALENDAR.SHOW-PRIZES", false)) {
                    lore.add("");
                    for (String line : prize.describe(plugin)) {
                        lore.add("&8• " + line);
                    }
                }
                lore.add("");
                lore.add("&e&l▶ Click to open!");
                item = ItemUtils.createItem(halloween.icon(last ? "LAST-DOOR" : "OPEN"),
                        halloween.theme().accent() + "&l" + plainDay(door, last) + " &8- " + name, lore);
                ItemUtils.setGlint(item, true);
            }
            case LOCKED -> {
                long wait = halloween.rules().secondsUntilOpen(door, halloween.now());
                item = ItemUtils.createItem(halloween.icon("LOCKED"),
                        "&7" + plainDay(door, last), List.of("&8Locked", "&7Opens in &f" + Text.duration(wait)));
            }
            case CLAIMED -> {
                List<String> lore = new ArrayList<>();
                lore.add("&aOpened! You found:");
                for (String line : prize.describe(plugin)) {
                    lore.add("&8• " + line);
                }
                item = ItemUtils.createItem(halloween.icon("CLAIMED"),
                        "&8" + plainDay(door, last) + " &7- " + name, lore);
            }
            default -> item = ItemUtils.createItem(halloween.icon("MISSED"),
                    "&8" + plainDay(door, last), List.of("&8This door was missed."));
        }
        item.setAmount(Math.max(1, Math.min(64, door)));
        return item;
    }

    private static String plainDay(int door, boolean last) {
        return last ? "Door " + door + " ★" : "Door " + door;
    }

    @Override
    public void handleClick(int slot, Player player, ClickType clickType) {
        if (busy) {
            return;
        }
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot == TRICK_SLOT && trickOrTreat()) {
            player.closeInventory();
            plugin.getSpigotScheduler().dispatchPlayerCommand(player, "trickortreat");
            return;
        }
        if (slot == EDIT_SLOT && PermissionUtils.has(player, halloween.theme().adminPermission())) {
            new CalendarEditorMenu(plugin, halloween).open(player);
            return;
        }
        Integer door = doorBySlot.get(slot);
        if (door == null) {
            return;
        }
        DoorState state = halloween.state(player.getUniqueId(), door);
        switch (state) {
            case OPEN -> openDoor(player, slot, door);
            case LOCKED -> {
                MenuFx.sound(player, Sound.BLOCK_WOODEN_DOOR_CLOSE, 0.7F, 0.6F);
                Text.send(player, halloween.theme().messageKey("LOCKED"),
                        "&7Door " + halloween.theme().accent() + "{door} &7is still locked. It opens in &f{time}&7.",
                        "{door}", Integer.toString(door),
                        "{time}", Text.duration(halloween.rules().secondsUntilOpen(door, halloween.now())));
            }
            case CLAIMED -> MenuFx.sound(player, Sound.BLOCK_CANDLE_EXTINGUISH, 0.8F, 1.0F);
            case MISSED -> MenuFx.sound(player, halloween.theme().missedSound(), 0.6F, 1.2F);
        }
    }

    private void openDoor(Player player, int slot, int door) {
        if (!halloween.claim(player, door)) {
            build(player);
            return;
        }
        busy = true;
        Reward prize = halloween.prize(door);
        List<ItemStack> frames = new ArrayList<>();
        for (String raw : halloween.config().getStringList("CALENDAR.SPIN-ITEMS")) {
            Material material = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
            if (material != null && material.isItem()) {
                frames.add(ItemUtils.createItem(material, halloween.theme().accent() + "&l? ? ?"));
            }
        }
        if (frames.isEmpty()) {
            for (Material material : halloween.theme().defaultSpin()) {
                frames.add(ItemUtils.createItem(material, halloween.theme().accent() + "&l? ? ?"));
            }
        }
        MenuFx.sound(player, Sound.BLOCK_WOODEN_DOOR_OPEN, 0.8F, 0.8F);
        int steps = Math.max(4, Math.min(60, halloween.config().getInt("CALENDAR.SPIN-STEPS", 18)));
        MenuFx.spin(plugin, player, this, inventory, slot, frames, steps, () -> reveal(player, slot, door, prize));
    }

    private void reveal(Player player, int slot, int door, Reward prize) {
        if (!player.isOnline()) {
            // They left during the spin: close the door again so the prize is waiting next time.
            halloween.reset(player.getUniqueId(), door, ignored -> {
            });
            return;
        }
        prize.give(plugin, player);
        boolean last = halloween.isLastDoor(door);
        List<String> lines = prize.describe(plugin);

        if (MenuFx.isViewing(player, this)) {
            ItemStack shown = prize.previewItem(plugin, halloween.theme().accent() + "&l" + plainDay(door, last) + " &8- "
                    + halloween.doorName(door), List.of());
            ItemUtils.setGlint(shown, true);
            inventory.setItem(slot, shown);
        }
        MenuFx.sound(player, Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.2F);
        CalendarTheme theme = halloween.theme();
        MenuFx.sound(player, theme.revealSound(), 0.9F, 1.0F);
        player.getWorld().spawnParticle(theme.particle(), player.getLocation().add(0, 1.0, 0), 40, 0.5, 0.8, 0.5, 0.05);
        Text.title(player, theme.accent() + "&l" + plainDay(door, last),
                lines.isEmpty() ? theme.lastDayText() : String.join("&7, ", lines), 5, 50, 15);
        Text.send(player, theme.messageKey("OPENED"), "&7You opened " + theme.accent() + "Door {door}&7: {prize}",
                "{door}", Integer.toString(door), "{prize}", String.join("&7, ", lines));
        if (last && halloween.config().getBoolean("CALENDAR.ANNOUNCE-LAST-DOOR", true)) {
            Text.broadcast(Text.prefix() + Text.get(theme.messageKey("LAST-DOOR-BROADCAST"),
                    theme.accent() + "{player} &7opened the last door of the " + theme.calendarName() + "! &8(/"
                            + theme.command() + ")",
                    "{player}", player.getName()));
        }

        plugin.getSpigotScheduler().runEntityLater(player, () -> {
            busy = false;
            if (!player.isOnline()) {
                return;
            }
            DialogGateway dialogs = plugin.getDialogManager();
            if (dialogs != null && halloween.config().getBoolean("CALENDAR.DIALOG-REVEAL", true)
                    && MenuFx.isViewing(player, this)) {
                player.closeInventory();
                dialogs.showNoticeAnimated(player, revealFrames(door, last, lines), 5L);
                return;
            }
            if (MenuFx.isViewing(player, this)) {
                build(player);
            }
        }, 30L);
    }

    private List<NoticeSpec> revealFrames(int door, boolean last, List<String> lines) {
        CalendarTheme theme = halloween.theme();
        String accent = theme.accent();
        String emoji = theme.revealEmoji();
        String title = accent + "&l" + plainDay(door, last);
        List<NoticeSpec> frames = new ArrayList<>();
        String[] steps = {"&8" + emoji + "  ·  ·  ·", accent + emoji + " &8 " + emoji + "  ·  ·",
                accent + emoji + "  " + emoji + " &8 " + emoji + "  ·", accent + emoji + "  " + emoji + "  " + emoji + "  " + emoji};
        for (String step : steps) {
            frames.add(new NoticeSpec(title, theme.revealIcon(), List.of(step), List.of()));
        }
        List<String> body = new ArrayList<>();
        body.add(last ? theme.lastDayText() : "&7Behind the door you found:");
        body.add(" ");
        body.addAll(lines.isEmpty() ? List.of(theme.surpriseText()) : lines);
        frames.add(new NoticeSpec(title + " &aopened!", theme.revealDoneIcon(), body, List.of(
                new NoticeSpec.Button(accent + "Back to calendar", theme.command()),
                new NoticeSpec.Button("&7Close", null))));
        return frames;
    }
}
