package com.bx.finnishSmp.menus;

import com.bx.finnishSmp.FinnishSmp;
import com.bx.finnishSmp.managers.AfkPointManager;
import com.bx.finnishSmp.managers.SpawnManager;
import com.bx.finnishSmp.utils.ColorUtils;
import com.bx.finnishSmp.utils.ItemUtils;
import com.bx.finnishSmp.utils.PermissionUtils;
import com.bx.finnishSmp.utils.SoundUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Destination picker for {@code /afk}.
 *
 * <p>Lists the named points created with {@code /setafk} alongside any legacy cuboid-based AFK areas
 * from {@code menus.yml → AFK-MENU.AREAS}, with live occupancy, distance and pagination. Admins can
 * save a new point or delete an existing one without leaving the GUI.</p>
 */
public class AfkMenu extends BaseMenu {

    private static final String MENU_PATH = "AFK-MENU";
    private static final String DELETE_PERMISSION = "finnishsmp.admin.teleportareas.delete";
    private static final String MANAGE_PERMISSION = "finnishsmp.admin.setafk";
    private static final double HERE_RADIUS = 16.0D;
    private static final int BAR_SEGMENTS = 10;

    private static final int[] ENTRY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private static final int STATUS_SLOT = 45;
    private static final int ADD_SLOT = 46;
    private static final int PREV_PAGE_SLOT = 47;
    private static final int CLOSEST_SLOT = 48;
    private static final int DEFAULT_RANDOM_SLOT = 49;
    private static final int REFRESH_SLOT = 50;
    private static final int NEXT_PAGE_SLOT = 53;

    private final Map<Integer, SlotAction> slotActions = new HashMap<>();

    private int page;

    public AfkMenu(FinnishSmp plugin) {
        super(plugin, configuredTitle(plugin), configuredSize(plugin));
    }

    private record Entry(
            String id,
            String label,
            Material icon,
            int capacity,
            String cuboidName,
            Location destination,
            AfkPointManager.AfkPoint point,
            SpawnManager.TeleportArea area
    ) {
    }

    @FunctionalInterface
    private interface SlotAction {
        void execute(Player player, ClickType clickType);
    }

    @Override
    public void build(Player player) {
        clear();
        slotActions.clear();
        fillBackground();

        List<Entry> entries = collectEntries();
        if (entries.isEmpty()) {
            buildEmptyState(player);
            buildStatusButton(player);
            buildAdminAddButton(player);
            return;
        }

        int perPage = ENTRY_SLOTS.length;
        int totalPages = Math.max(1, (entries.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, totalPages - 1));

        buildEntries(player, entries, perPage);
        buildStatusButton(player);
        buildAdminAddButton(player);
        buildPageButtons(totalPages);
        buildClosestButton(player, entries);
        buildRefreshButton(entries.size(), totalPages);
        buildRandomButton(entries);
    }

    @Override
    public void handleClick(int slot, Player player, ClickType clickType) {
        SlotAction action = slotActions.get(slot);
        if (action == null) {
            return;
        }

        SoundUtils.play(player, plugin.getConfigManager().getSound("MENUS.BUTTON-CLICK"));
        action.execute(player, clickType);
    }

    // ------------------------------------------------------------------ entries

    /** New {@code /setafk} points first, then legacy cuboid areas that are not already listed. */
    private List<Entry> collectEntries() {
        Map<String, Entry> byLocation = new LinkedHashMap<>();

        for (AfkPointManager.AfkPoint point : plugin.getAfkPointManager().getResolvablePoints()) {
            Location destination = point.location();
            byLocation.putIfAbsent(locationKey(destination), new Entry(
                    point.id(),
                    point.displayName(),
                    point.icon(),
                    point.capacity(),
                    null,
                    destination,
                    point,
                    null
            ));
        }

        for (SpawnManager.TeleportArea area : plugin.getSpawnManager().getMenuAreas(SpawnManager.AreaType.AFK)) {
            Location destination = plugin.getSpawnManager().resolveDestination(area);
            if (destination == null) {
                continue;
            }
            byLocation.putIfAbsent(locationKey(destination), new Entry(
                    area.id(),
                    area.displayName(),
                    area.material(),
                    area.capacity(),
                    area.cuboidName(),
                    destination,
                    null,
                    area
            ));
        }

        return new ArrayList<>(byLocation.values());
    }

    private void buildEntries(Player player, List<Entry> entries, int perPage) {
        int startIndex = page * perPage;

        for (int offset = 0; offset < perPage; offset++) {
            int entryIndex = startIndex + offset;
            if (entryIndex >= entries.size()) {
                return;
            }

            Entry entry = entries.get(entryIndex);
            boolean standingHere = isStandingAt(player, entry.destination());
            boolean deletable = canDelete(player, entry);

            List<String> lore = lore(MENU_PATH + ".ENTRY.LORE", defaultEntryLore(), entryPlaceholders(player, entry));
            if (standingHere) {
                lore = append(lore, menus().getString(MENU_PATH + ".ENTRY.HERE-TEXT",
                        "&aʏᴏᴜ ᴀʀᴇ ᴀʟʀᴇᴀᴅʏ ɪɴ ᴛʜɪѕ ᴢᴏɴᴇ"));
            }
            if (deletable) {
                lore = append(lore, menus().getString(MENU_PATH + ".ENTRY.DELETE-TEXT",
                        "&cѕʜɪꜰᴛ + ʀɪɢʜᴛ-ᴄʟɪᴄᴋ ᴛᴏ ᴅᴇʟᴇᴛᴇ"));
            }

            ItemStack item = ItemUtils.createItem(
                    entry.icon(),
                    replacePlaceholders(entry.label(), entryPlaceholders(player, entry)),
                    lore
            );
            if (standingHere) {
                item = ItemUtils.addEnchantments(item, List.of("unbreaking:1"));
                hideEnchantments(item);
            }

            int slot = ENTRY_SLOTS[offset];
            set(slot, item);
            slotActions.put(slot, (clicker, click) -> {
                if (deletable && click == ClickType.SHIFT_RIGHT) {
                    deleteEntry(clicker, entry);
                    return;
                }
                teleport(clicker, entry.destination());
            });
        }
    }

    private void buildEmptyState(Player player) {
        boolean admin = PermissionUtils.has(player, MANAGE_PERMISSION);
        List<String> lore = new ArrayList<>(lore(
                MENU_PATH + ".EMPTY.LORE",
                List.of("&7ɴᴏ ᴀꜰᴋ ᴢᴏɴᴇѕ ʜᴀᴠᴇ ʙᴇᴇɴ ѕᴇᴛ ᴜᴘ ʏᴇᴛ."),
                Map.of()
        ));
        if (admin) {
            lore.add("&7ѕᴛᴀɴᴅ ᴡʜᴇʀᴇ ʏᴏᴜ ᴡᴀɴᴛ ᴏɴᴇ ᴀɴᴅ ʀᴜɴ &f/setafk&7.");
        }

        int slot = ENTRY_SLOTS[ENTRY_SLOTS.length / 2];
        set(slot, ItemUtils.createItem(
                material(MENU_PATH + ".EMPTY.MATERIAL", Material.BARRIER),
                menus().getString(MENU_PATH + ".EMPTY.DISPLAY-NAME", "&cɴᴏ ᴀꜰᴋ ᴢᴏɴᴇѕ"),
                lore
        ));
    }

    // ------------------------------------------------------------------ buttons

    private void buildStatusButton(Player player) {
        int timeout = plugin.getAFKManager().getTimeoutSeconds();
        long idleSeconds = plugin.getAFKManager().getSecondsSinceLastMovement(player.getUniqueId());
        boolean afk = plugin.getAFKManager().isAfk(player.getUniqueId());

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("status", afk ? "&#A303F9ᴀꜰᴋ" : "&aᴀᴄᴛɪᴠᴇ");
        placeholders.put("idle", idleSeconds == Long.MAX_VALUE ? "-" : formatDuration(idleSeconds));
        placeholders.put("timeout", formatDuration(timeout));
        placeholders.put("player", player.getName());

        set(STATUS_SLOT, ItemUtils.createItem(
                material(MENU_PATH + ".STATUS-BUTTON.MATERIAL", Material.CLOCK),
                text(MENU_PATH + ".STATUS-BUTTON.DISPLAY-NAME", "&fʏᴏᴜʀ ѕᴛᴀᴛᴜѕ", placeholders),
                lore(MENU_PATH + ".STATUS-BUTTON.LORE", List.of(
                        "&7ѕᴛᴀᴛᴇ: {status}",
                        "&7ɪᴅʟᴇ ꜰᴏʀ: &f{idle}",
                        "&7ᴀᴜᴛᴏ-ᴀꜰᴋ ᴀꜰᴛᴇʀ: &f{timeout}",
                        "&8ᴏꜰ ɪɴᴀᴄᴛɪᴠɪᴛʏ ɪɴ ѕᴘᴀᴡɴ"
                ), placeholders)
        ));
    }

    private void buildAdminAddButton(Player player) {
        if (!PermissionUtils.has(player, MANAGE_PERMISSION)) {
            return;
        }

        set(ADD_SLOT, ItemUtils.createItem(
                material(MENU_PATH + ".ADD-BUTTON.MATERIAL", Material.AMETHYST_SHARD),
                menus().getString(MENU_PATH + ".ADD-BUTTON.DISPLAY-NAME", "&aѕᴀᴠᴇ ᴀɴ ᴀꜰᴋ ᴢᴏɴᴇ ʜᴇʀᴇ"),
                lore(MENU_PATH + ".ADD-BUTTON.LORE", List.of(
                        "&7ᴄʟɪᴄᴋ ᴛᴏ ѕᴀᴠᴇ ʏᴏᴜʀ ᴄᴜʀʀᴇɴᴛ",
                        "&7ᴘᴏѕɪᴛɪᴏɴ ᴀѕ ᴀ ɴᴇᴡ ᴀꜰᴋ ᴢᴏɴᴇ.",
                        "&8ѕᴀᴍᴇ ᴀѕ &7/setafk"
                ), Map.of())
        ));
        slotActions.put(ADD_SLOT, this::addPointHere);
    }

    private void buildPageButtons(int totalPages) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("page", String.valueOf(page + 1));
        placeholders.put("pages", String.valueOf(totalPages));

        if (page > 0) {
            set(PREV_PAGE_SLOT, ItemUtils.createItem(
                    material(MENU_PATH + ".PREV-PAGE.MATERIAL", Material.ARROW),
                    text(MENU_PATH + ".PREV-PAGE.DISPLAY-NAME", "&fᴘʀᴇᴠɪᴏᴜѕ ᴘᴀɢᴇ", placeholders),
                    lore(MENU_PATH + ".PREV-PAGE.LORE", List.of("&7ᴘᴀɢᴇ &f{page}&7/&f{pages}"), placeholders)
            ));
            slotActions.put(PREV_PAGE_SLOT, (clicker, click) -> {
                page--;
                build(clicker);
            });
        }

        if (page < totalPages - 1) {
            set(NEXT_PAGE_SLOT, ItemUtils.createItem(
                    material(MENU_PATH + ".NEXT-PAGE.MATERIAL", Material.ARROW),
                    text(MENU_PATH + ".NEXT-PAGE.DISPLAY-NAME", "&fɴᴇxᴛ ᴘᴀɢᴇ", placeholders),
                    lore(MENU_PATH + ".NEXT-PAGE.LORE", List.of("&7ᴘᴀɢᴇ &f{page}&7/&f{pages}"), placeholders)
            ));
            slotActions.put(NEXT_PAGE_SLOT, (clicker, click) -> {
                page++;
                build(clicker);
            });
        }
    }

    private void buildClosestButton(Player player, List<Entry> entries) {
        Entry closest = findClosest(player, entries);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("name", closest == null ? "-" : ColorUtils.strip(closest.label()));
        placeholders.put("distance", closest == null ? "-" : formatDistance(player, closest.destination()));

        List<String> defaultLore = closest == null
                ? List.of("&7ɴᴏ ᴀꜰᴋ ᴢᴏɴᴇ ɪɴ ʏᴏᴜʀ ᴡᴏʀʟᴅ.")
                : List.of("&7ɴᴇᴀʀᴇѕᴛ: &f{name}", "&7ᴅɪѕᴛᴀɴᴄᴇ: &f{distance}", "&aᴄʟɪᴄᴋ ᴛᴏ ᴛᴇʟᴇᴘᴏʀᴛ");

        set(CLOSEST_SLOT, ItemUtils.createItem(
                material(MENU_PATH + ".CLOSEST-BUTTON.MATERIAL",
                        closest == null ? Material.GRAY_DYE : Material.RECOVERY_COMPASS),
                text(MENU_PATH + ".CLOSEST-BUTTON.DISPLAY-NAME", "&fᴄʟᴏѕᴇѕᴛ ᴀꜰᴋ ᴢᴏɴᴇ", placeholders),
                lore(MENU_PATH + ".CLOSEST-BUTTON.LORE", defaultLore, placeholders)
        ));

        if (closest != null) {
            slotActions.put(CLOSEST_SLOT, (clicker, click) -> teleport(clicker, closest.destination()));
        }
    }

    private void buildRefreshButton(int entryCount, int totalPages) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("areas", String.valueOf(entryCount));
        placeholders.put("page", String.valueOf(page + 1));
        placeholders.put("pages", String.valueOf(totalPages));

        set(REFRESH_SLOT, ItemUtils.createItem(
                material(MENU_PATH + ".REFRESH-BUTTON.MATERIAL", Material.SUNFLOWER),
                text(MENU_PATH + ".REFRESH-BUTTON.DISPLAY-NAME", "&fʀᴇꜰʀᴇѕʜ", placeholders),
                lore(MENU_PATH + ".REFRESH-BUTTON.LORE", List.of(
                        "&7ᴢᴏɴᴇѕ: &f{areas}",
                        "&7ᴘᴀɢᴇ: &f{page}&7/&f{pages}",
                        "&7ᴄʟɪᴄᴋ ᴛᴏ ᴜᴘᴅᴀᴛᴇ ᴘʟᴀʏᴇʀ ᴄᴏᴜɴᴛѕ."
                ), placeholders)
        ));
        slotActions.put(REFRESH_SLOT, (clicker, click) -> build(clicker));
    }

    /** Placed last so a custom {@code RANDOM-BUTTON.SLOT} always wins any collision. */
    private void buildRandomButton(List<Entry> entries) {
        if (entries.size() < 2) {
            return;
        }

        int slot = menus().getInt(MENU_PATH + ".RANDOM-BUTTON.SLOT", DEFAULT_RANDOM_SLOT);
        if (slot < 0 || slot >= inventory.getSize()) {
            slot = DEFAULT_RANDOM_SLOT;
        }

        Map<String, String> placeholders = Map.of("areas", String.valueOf(entries.size()));
        set(slot, ItemUtils.createItem(
                material(MENU_PATH + ".RANDOM-BUTTON.MATERIAL", Material.AMETHYST_BLOCK),
                text(MENU_PATH + ".RANDOM-BUTTON.DISPLAY-NAME", "&#A303F9ʀᴀɴᴅᴏᴍ ᴀꜰᴋ ᴢᴏɴᴇ", placeholders),
                lore(MENU_PATH + ".RANDOM-BUTTON.LORE", List.of(
                        "&7ᴘɪᴄᴋѕ ᴏɴᴇ ᴏꜰ &f{areas}&7 ᴢᴏɴᴇѕ.",
                        "&aᴄʟɪᴄᴋ ᴛᴏ ᴛᴇʟᴇᴘᴏʀᴛ"
                ), placeholders)
        ));

        List<Entry> pickPool = List.copyOf(entries);
        slotActions.put(slot, (clicker, click) ->
                teleport(clicker, pickPool.get(ThreadLocalRandom.current().nextInt(pickPool.size())).destination()));
    }

    // ------------------------------------------------------------------ actions

    private void teleport(Player player, Location destination) {
        if (destination == null || destination.getWorld() == null) {
            player.sendMessage(ColorUtils.toComponent("&cᴛʜᴀᴛ ᴀꜰᴋ ᴢᴏɴᴇ ɪѕ ɴᴏ ʟᴏɴɢᴇʀ ᴀᴠᴀɪʟᴀʙʟᴇ."));
            return;
        }

        if (plugin.getCombatManager().isInCombat(player.getUniqueId())) {
            player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getConfig()
                    .getString("COMBAT-MANAGER.BLOCK-MESSAGE", "&cʏᴏᴜ ᴄᴀɴ'ᴛ ᴜѕᴇ ᴛʜɪѕ ɪɴ ᴄᴏᴍʙᴀᴛ.")));
            return;
        }

        player.closeInventory();
        plugin.getTeleportManager().queue(player, destination, "AFK", null);
    }

    private void addPointHere(Player player, ClickType clickType) {
        if (!PermissionUtils.has(player, MANAGE_PERMISSION)) {
            return;
        }

        String id = plugin.getAfkPointManager().nextAutoId();
        if (id == null) {
            player.sendMessage(ColorUtils.toComponent("&cᴛᴏᴏ ᴍᴀɴʏ ᴀꜰᴋ ᴢᴏɴᴇѕ. ᴜѕᴇ &f/setafk <name>&c."));
            return;
        }

        AfkPointManager.PointResult result = plugin.getAfkPointManager().setPoint(id, player.getLocation());
        if (!result.success()) {
            player.sendMessage(ColorUtils.toComponent("&cᴄᴏᴜʟᴅ ɴᴏᴛ ѕᴀᴠᴇ ᴛʜᴇ ᴀꜰᴋ ᴢᴏɴᴇ: &f" + result.message()));
            return;
        }

        player.sendMessage(ColorUtils.toComponent("&aѕᴀᴠᴇᴅ ᴀꜰᴋ ᴢᴏɴᴇ &f" + result.id() + "&a ᴀᴛ ʏᴏᴜʀ ᴘᴏѕɪᴛɪᴏɴ."));
        build(player);
    }

    private void deleteEntry(Player player, Entry entry) {
        if (entry.point() != null) {
            AfkPointManager.PointResult result = plugin.getAfkPointManager().deletePoint(entry.point().id());
            if (!result.success()) {
                player.sendMessage(ColorUtils.toComponent("&c" + result.message()));
                return;
            }
            player.sendMessage(ColorUtils.toComponent("&aʀᴇᴍᴏᴠᴇᴅ ᴀꜰᴋ ᴢᴏɴᴇ &f" + result.id() + "&a."));
            build(player);
            return;
        }

        SpawnManager.AreaDeleteResult result = plugin.getSpawnManager().deleteMenuArea(entry.area());
        if (!result.success()) {
            player.sendMessage(ColorUtils.toComponent("&cᴄᴏᴜʟᴅ ɴᴏᴛ ᴅᴇʟᴇᴛᴇ ᴛʜɪѕ ᴀꜰᴋ ᴀʀᴇᴀ: &f" + result.message()));
            return;
        }

        player.sendMessage(ColorUtils.toComponent("&a" + result.message()));
        build(player);
    }

    private boolean canDelete(Player player, Entry entry) {
        boolean allowed = PermissionUtils.hasAny(player, DELETE_PERMISSION, "finnishsmp.admin")
                || PermissionUtils.has(player, MANAGE_PERMISSION);
        if (!allowed) {
            return false;
        }
        return entry.point() != null || plugin.getSpawnManager().isStoredMenuArea(entry.area());
    }

    // ------------------------------------------------------------------ helpers

    private Entry findClosest(Player player, List<Entry> entries) {
        Entry closest = null;
        double closestDistanceSquared = Double.MAX_VALUE;

        for (Entry entry : entries) {
            Location destination = entry.destination();
            if (destination.getWorld() == null || !destination.getWorld().equals(player.getWorld())) {
                continue;
            }

            double distanceSquared = destination.distanceSquared(player.getLocation());
            if (distanceSquared < closestDistanceSquared) {
                closest = entry;
                closestDistanceSquared = distanceSquared;
            }
        }
        return closest;
    }

    private boolean isStandingAt(Player player, Location destination) {
        return destination.getWorld() != null
                && destination.getWorld().equals(player.getWorld())
                && destination.distanceSquared(player.getLocation()) <= HERE_RADIUS * HERE_RADIUS;
    }

    private int countPlayers(Entry entry) {
        String cuboidName = entry.cuboidName();
        if (cuboidName != null && !cuboidName.isBlank() && plugin.getCuboidManager().exists(cuboidName)) {
            return plugin.getCuboidManager().countPlayersInCuboid(cuboidName);
        }
        return plugin.getAfkPointManager().countPlayersNear(entry.destination());
    }

    private Map<String, String> entryPlaceholders(Player player, Entry entry) {
        Location destination = entry.destination();
        int players = countPlayers(entry);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("id", entry.id());
        placeholders.put("name", ColorUtils.strip(entry.label()));
        placeholders.put("world", destination.getWorld() == null ? "?" : destination.getWorld().getName());
        placeholders.put("x", String.valueOf(destination.getBlockX()));
        placeholders.put("y", String.valueOf(destination.getBlockY()));
        placeholders.put("z", String.valueOf(destination.getBlockZ()));
        placeholders.put("players", String.valueOf(players));
        placeholders.put("capacity", String.valueOf(entry.capacity()));
        placeholders.put("distance", formatDistance(player, destination));
        placeholders.put("bar", occupancyBar(players, entry.capacity()));
        placeholders.put("cuboid", entry.cuboidName() == null ? "" : entry.cuboidName());
        return placeholders;
    }

    private List<String> defaultEntryLore() {
        return List.of(
                "&8{bar} &7{players}&8/&7{capacity}",
                "&7ᴡᴏʀʟᴅ: &f{world}",
                "&7ᴀᴛ: &f{x}, {y}, {z}",
                "&7ᴅɪѕᴛᴀɴᴄᴇ: &f{distance}",
                "&aᴄʟɪᴄᴋ ᴛᴏ ᴛᴇʟᴇᴘᴏʀᴛ"
        );
    }

    private String occupancyBar(int players, int capacity) {
        int filled = capacity <= 0
                ? 0
                : Math.max(0, Math.min(BAR_SEGMENTS, (int) Math.ceil((double) players * BAR_SEGMENTS / capacity)));

        StringBuilder bar = new StringBuilder("&a");
        for (int index = 0; index < BAR_SEGMENTS; index++) {
            if (index == filled) {
                bar.append("&8");
            }
            bar.append('|');
        }
        return bar.toString();
    }

    private String formatDistance(Player player, Location destination) {
        if (destination.getWorld() == null || !destination.getWorld().equals(player.getWorld())) {
            return "another world";
        }
        return (int) Math.round(destination.distance(player.getLocation())) + " blocks";
    }

    private String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        long remainder = seconds % 60;
        return remainder == 0 ? minutes + "m" : minutes + "m " + remainder + "s";
    }

    private String locationKey(Location location) {
        return (location.getWorld() == null ? "?" : location.getWorld().getName())
                + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private List<String> append(List<String> lines, String extra) {
        List<String> combined = new ArrayList<>(lines);
        if (extra != null && !extra.isBlank()) {
            combined.add(extra);
        }
        return combined;
    }

    private void hideEnchantments(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
    }

    private void fillBackground() {
        ItemStack filler = ItemUtils.createPlaceholder(
                material(MENU_PATH + ".FILLER-MATERIAL", Material.GRAY_STAINED_GLASS_PANE));
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            set(slot, filler);
        }
    }

    private Material material(String path, Material fallback) {
        return ItemUtils.parseMaterial(menus().getString(path, fallback.name()));
    }

    private String text(String path, String fallback, Map<String, String> placeholders) {
        return replacePlaceholders(menus().getString(path, fallback), placeholders);
    }

    private List<String> lore(String path, List<String> fallback, Map<String, String> placeholders) {
        List<String> lines = new ArrayList<>();
        Object raw = menus().get(path);

        if (raw instanceof List<?> list && !list.isEmpty()) {
            for (Object line : list) {
                lines.add(String.valueOf(line));
            }
        } else if (raw instanceof String string && !string.isBlank()) {
            lines.add(string);
        } else {
            lines.addAll(fallback);
        }

        for (int index = 0; index < lines.size(); index++) {
            lines.set(index, replacePlaceholders(lines.get(index), placeholders));
        }
        return lines;
    }

    private String replacePlaceholders(String value, Map<String, String> placeholders) {
        String output = value == null ? "" : value;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            output = output.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return output;
    }

    private FileConfiguration menus() {
        return plugin.getConfigManager().getMenus();
    }

    private static String configuredTitle(FinnishSmp plugin) {
        return plugin.getConfigManager().getMenus().getString(MENU_PATH + ".TITLE", "&8ᴀꜰᴋ ᴢᴏɴᴇѕ");
    }

    /** Always a double chest: the entry grid and the control row below it are laid out for 54 slots. */
    private static int configuredSize(FinnishSmp plugin) {
        return 54;
    }
}
