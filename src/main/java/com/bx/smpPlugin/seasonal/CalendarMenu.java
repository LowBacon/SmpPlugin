package com.bx.smpPlugin.seasonal;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.MenuFx;
import com.bx.smpPlugin.core.Reward;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.halloween.CalendarRules;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A season calendar: one door per day; today's door spins and reveals its prize. The prize is already handed over
 * when the spin starts, so the spin only shows it.
 *
 * <pre>
 *  row 0   frame ........ [info] ........ frame
 *  rows 1-4  doors, seven per row
 *  row 5   [x] [edit] [door] [door] [last door] [x] [x] [x] [close]      x = slots the event may use
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
    static final int LAST_DOOR_SLOT = 49;
    private static final int INFO_SLOT = 4;
    private static final int EDIT_SLOT = 46;
    private static final int CLOSE_SLOT = 53;
    private static final List<Integer> EXTRA_SLOTS = List.of(45, 50, 51, 52);

    private final SeasonCalendar calendar;
    private final CalendarTheme theme;
    private final Map<Integer, Integer> doorBySlot = new HashMap<>();
    private final Map<Integer, SeasonCalendar.MenuButton> buttons = new HashMap<>();
    private boolean busy;

    public CalendarMenu(SmpPlugin plugin, SeasonCalendar calendar) {
        this(plugin, calendar, calendar.theme());
    }

    private CalendarMenu(SmpPlugin plugin, SeasonCalendar calendar, CalendarTheme theme) {
        super(plugin, theme.title(), 54);
        this.calendar = calendar;
        this.theme = theme;
    }

    /** Where each door sits: in order through rows 1-4, the last door always in the middle of the bottom row. */
    static Map<Integer, Integer> layout(CalendarRules rules, int year) {
        Map<Integer, Integer> slots = new LinkedHashMap<>();
        int lastDoor = rules.lastDayIn(year);
        int index = 0;
        for (int door = rules.firstDay(); door <= lastDoor; door++) {
            if (door == lastDoor) {
                slots.put(LAST_DOOR_SLOT, door);
            } else if (index < DOOR_SLOTS.length - 1) {
                slots.put(DOOR_SLOTS[index++], door);
            }
        }
        return slots;
    }

    @Override
    public void open(Player player) {
        build(player);
        player.openInventory(inventory);
        MenuFx.sound(player, Sound.BLOCK_CHEST_OPEN, 0.6F, 0.7F);
        MenuFx.sound(player, theme.ambient(), 0.4F, 1.2F);
        if (calendar.config().getBoolean("CALENDAR.OPEN-ANIMATION", true)) {
            Map<Integer, ItemStack> doors = new LinkedHashMap<>();
            for (int slot : doorBySlot.keySet()) {
                doors.put(slot, inventory.getItem(slot));
            }
            MenuFx.wave(plugin, player, this, inventory, doors, ItemUtils.createGlassPane(Material.BLACK_STAINED_GLASS_PANE));
        }
        if (calendar.config().getBoolean("CALENDAR.BORDER-ANIMATION", true)) {
            MenuFx.shimmer(plugin, player, this, inventory, frameSlots(), theme.borderColours(), 8L);
        }
    }

    @Override
    public void build(Player player) {
        clear();
        doorBySlot.clear();
        buttons.clear();
        buttons.putAll(calendar.extraButtons(player));
        buttons.keySet().retainAll(EXTRA_SLOTS);
        List<Material> colours = theme.borderColours();
        for (int slot : frameSlots()) {
            set(slot, ItemUtils.createGlassPane(colours.get(slot % colours.size())));
        }
        set(INFO_SLOT, infoItem(player));
        doorBySlot.putAll(layout(calendar.rules(), calendar.seasonYear(player.getUniqueId())));
        for (Map.Entry<Integer, Integer> entry : doorBySlot.entrySet()) {
            set(entry.getKey(), doorItem(player, entry.getValue()));
        }
        if (PermissionUtils.has(player, calendar.adminPermission())) {
            set(EDIT_SLOT, ItemUtils.createItem(Material.WRITABLE_BOOK, "&e&lEdit prizes",
                    List.of("&7Choose what is behind each door.", "", "&e▶ Click to edit")));
        }
        buttons.forEach((slot, button) -> set(slot, button.item()));
        set(CLOSE_SLOT, ItemUtils.createItem(Material.BARRIER, "&cClose"));
    }

    private List<Integer> frameSlots() {
        List<Integer> frame = new ArrayList<>(MenuFx.borderSlots(6));
        frame.removeIf(slot -> slot == INFO_SLOT || slot == CLOSE_SLOT || slot == EDIT_SLOT
                || slot == 47 || slot == 48 || slot == LAST_DOOR_SLOT || buttons.containsKey(slot));
        return frame;
    }

    private ItemStack infoItem(Player player) {
        UUID uuid = player.getUniqueId();
        CalendarRules rules = calendar.rules();
        ZonedDateTime now = calendar.now(uuid);
        int year = calendar.seasonYear(uuid);
        List<String> lore = new ArrayList<>(theme.intro());
        lore.add("");
        lore.add("&7Doors opened: " + theme.accent() + calendar.openedCount(uuid) + "&7/" + theme.accent()
                + rules.doorCount(year));
        if (calendar.streaksEnabled()) {
            ClaimStreak streak = calendar.streak(uuid);
            lore.add("&7Streak: " + theme.accent() + calendar.currentStreak(player) + " day(s) &8(best " + streak.best() + ")");
        }
        boolean testing = calendar.isTesting(uuid);
        EventStatus status = calendar.events().status(calendar.id());
        if (!testing && status != EventStatus.ACTIVE) {
            long wait = calendar.events().secondsUntilStart(calendar.id());
            lore.add(status == EventStatus.DISABLED ? "&cSwitched off for now."
                    : wait > 0 ? "&7The calendar opens in &f" + Text.duration(wait) : "&7This year's calendar has closed.");
        } else {
            long untilLastDay = rules.secondsUntilLastDay(now);
            lore.add(untilLastDay > 0 ? "&7" + theme.countdownLabel() + " &f" + Text.duration(untilLastDay) : theme.lastDayGreeting());
            int ready = calendar.claimableCount(uuid);
            lore.add(ready > 0 ? "&a" + ready + " door" + (ready == 1 ? "" : "s") + " ready to open!"
                    : "&7Come back tomorrow for the next door.");
        }
        Optional<String> rank = rankLine(player);
        rank.ifPresent(line -> {
            lore.add("");
            lore.add(line);
        });
        if (testing) {
            lore.add("");
            lore.add("&d&lTEST MODE &d" + calendar.testDate(uuid) + "&7: only you see this,");
            lore.add("&7and opening doors gives nothing.");
        }
        ItemStack item = ItemUtils.createItem(theme.infoIcon(), theme.accent() + "&l" + theme.name() + " &8" + year, lore);
        return ItemUtils.setGlint(item, true);
    }

    private Optional<String> rankLine(Player player) {
        return calendar.rankOf(player).flatMap(rank -> {
            int percent = rank.value(com.bx.smpPlugin.perks.Perk.CALENDAR_BONUS_PERCENT);
            return percent > 0
                    ? Optional.of(rank.display() + " &7+" + percent + "% money and shards on every door")
                    : Optional.empty();
        });
    }

    private ItemStack doorItem(Player player, int door) {
        DoorState state = calendar.state(player.getUniqueId(), door);
        boolean last = calendar.isLastDoor(door);
        String name = calendar.doorName(door);
        Reward prize = calendar.prize(door);
        ItemStack item;
        switch (state) {
            case OPEN -> {
                List<String> lore = new ArrayList<>();
                lore.add(last ? theme.lastOpenHint() : theme.openHint());
                if (calendar.config().getBoolean("CALENDAR.SHOW-PRIZES", false)) {
                    lore.add("");
                    for (String line : prize.describe(plugin)) {
                        lore.add("&8• " + line);
                    }
                }
                lore.add("");
                lore.add("&e&l▶ Click to open!");
                item = ItemUtils.createItem(theme.icon(last ? "LAST-DOOR" : "OPEN"),
                        theme.accent() + "&l" + plainDay(door, last) + " &8- " + name, lore);
                ItemUtils.setGlint(item, true);
            }
            case LOCKED -> {
                long wait = calendar.rules().secondsUntilOpen(door, calendar.now(player.getUniqueId()));
                List<String> lore = new ArrayList<>(List.of("&8Locked", "&7Opens in &f" + Text.duration(wait)));
                if (calendar.config().getBoolean("CALENDAR.SHOW-PRIZES", false)) {
                    lore.add("");
                    for (String line : prize.describe(plugin)) {
                        lore.add("&8• " + line);
                    }
                }
                item = ItemUtils.createItem(theme.icon("LOCKED"), "&7" + plainDay(door, last), lore);
            }
            case CLAIMED -> {
                List<String> lore = new ArrayList<>();
                lore.add("&aOpened! You found:");
                for (String line : prize.describe(plugin)) {
                    lore.add("&8• " + line);
                }
                item = ItemUtils.createItem(theme.icon("CLAIMED"), "&8" + plainDay(door, last) + " &7- " + name, lore);
            }
            default -> item = ItemUtils.createItem(theme.icon("MISSED"), "&8" + plainDay(door, last),
                    List.of("&8This door was missed."));
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
        SeasonCalendar.MenuButton button = buttons.get(slot);
        if (button != null) {
            button.onClick().accept(player);
            return;
        }
        if (slot == EDIT_SLOT && PermissionUtils.has(player, calendar.adminPermission())) {
            new CalendarEditorMenu(plugin, calendar).open(player);
            return;
        }
        Integer door = doorBySlot.get(slot);
        if (door == null) {
            return;
        }
        // Checked again on every click: the use permission or the event may have changed since the menu opened.
        if (!PermissionUtils.has(player, calendar.usePermission())) {
            player.closeInventory();
            Text.send(player, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return;
        }
        DoorState state = calendar.state(player.getUniqueId(), door);
        switch (state) {
            case OPEN -> openDoor(player, slot, door);
            case LOCKED -> {
                MenuFx.sound(player, Sound.BLOCK_WOODEN_DOOR_CLOSE, 0.7F, 0.6F);
                Text.send(player, "CALENDAR.LOCKED", "&7Door {accent}{door} &7is still locked. It opens in &f{time}&7.",
                        "{accent}", theme.accent(), "{door}", Integer.toString(door),
                        "{time}", Text.duration(calendar.rules().secondsUntilOpen(door, calendar.now(player.getUniqueId()))));
            }
            case CLAIMED -> MenuFx.sound(player, Sound.BLOCK_CANDLE_EXTINGUISH, 0.8F, 1.0F);
            case MISSED -> MenuFx.sound(player, Sound.BLOCK_GLASS_BREAK, 0.4F, 1.4F);
        }
    }

    private void openDoor(Player player, int slot, int door) {
        Optional<SeasonCalendar.Granted> granted = calendar.claim(player, door);
        if (granted.isEmpty()) {
            if (!calendar.isEnabled()) {
                Text.send(player, "CALENDAR.OFF", "&7{name} is switched off right now.", "{name}", theme.name());
            }
            build(player);
            return;
        }
        busy = true;
        List<ItemStack> frames = new ArrayList<>();
        for (Material material : theme.spinItems()) {
            frames.add(ItemUtils.createItem(material, theme.accent() + "&l? ? ?"));
        }
        MenuFx.sound(player, Sound.BLOCK_WOODEN_DOOR_OPEN, 0.8F, 0.8F);
        int steps = Math.max(4, Math.min(60, calendar.config().getInt("CALENDAR.SPIN-STEPS", 18)));
        MenuFx.spin(plugin, player, this, inventory, slot, frames, steps, () -> reveal(player, slot, granted.get()));
    }

    private void reveal(Player player, int slot, SeasonCalendar.Granted granted) {
        if (!player.isOnline()) {
            return;
        }
        int door = granted.door();
        boolean last = calendar.isLastDoor(door);
        List<String> lines = granted.lines(plugin);
        if (MenuFx.isViewing(player, this)) {
            ItemStack shown = granted.prize().previewItem(plugin, theme.accent() + "&l" + plainDay(door, last) + " &8- "
                    + calendar.doorName(door), granted.test() ? List.of("", "&d&lTEST &7- nothing was given") : List.of());
            ItemUtils.setGlint(shown, true);
            inventory.setItem(slot, shown);
        }
        MenuFx.sound(player, Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.2F);
        MenuFx.sound(player, theme.reveal(), 0.9F, 1.0F);
        Particle particle = theme.particle();
        if (particle != null) {
            player.getWorld().spawnParticle(particle, player.getLocation().add(0, 1.0, 0), 40, 0.5, 0.8, 0.5, 0.05);
        }
        String summary = lines.isEmpty() ? theme.lastDayGreeting() : String.join("&7, ", lines);
        Text.title(player, theme.accent() + "&l" + plainDay(door, last), summary, 5, 50, 15);
        if (granted.test()) {
            Text.send(player, "CALENDAR.TEST-OPENED", "&d[Test] &7Door {accent}{door} &7would give: {prize}",
                    "{accent}", theme.accent(), "{door}", Integer.toString(door), "{prize}", summary);
        } else {
            Text.send(player, "CALENDAR.OPENED", "&7You opened {accent}Door {door}&7: {prize}",
                    "{accent}", theme.accent(), "{door}", Integer.toString(door), "{prize}", summary);
            if (last && calendar.config().getBoolean("CALENDAR.ANNOUNCE-LAST-DOOR", true)) {
                Text.broadcast(Text.prefix() + Text.get("CALENDAR.LAST-DOOR-BROADCAST",
                        "{accent}{player} &7opened the last door of the {accent}{name}&7! &8(/{command})",
                        "{accent}", theme.accent(), "{player}", player.getName(), "{name}", theme.name(),
                        "{command}", calendar.command()));
            }
        }

        plugin.getSpigotScheduler().runEntityLater(player, () -> {
            busy = false;
            if (!player.isOnline()) {
                return;
            }
            DialogGateway dialogs = plugin.getDialogManager();
            if (dialogs != null && calendar.config().getBoolean("CALENDAR.DIALOG-REVEAL", true)
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
        String title = theme.accent() + "&l" + plainDay(door, last);
        String symbol = theme.revealSymbol();
        String icon = theme.dialogIcon().name();
        List<NoticeSpec> frames = new ArrayList<>();
        for (int lit = 1; lit <= 4; lit++) {
            StringBuilder row = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                row.append(i < lit ? theme.accent() + symbol : "&8·").append("  ");
            }
            frames.add(new NoticeSpec(title, icon, List.of(row.toString().trim()), List.of()));
        }
        List<String> body = new ArrayList<>();
        body.add(last ? theme.lastDayGreeting() : "&7Behind the door you found:");
        body.add(" ");
        body.addAll(lines.isEmpty() ? List.of("&7A surprise!") : lines);
        frames.add(new NoticeSpec(title + " &aopened!", icon, body, List.of(
                new NoticeSpec.Button(theme.accent() + "Back to calendar", calendar.calendarCommand()),
                new NoticeSpec.Button("&7Close", null))));
        return frames;
    }
}
