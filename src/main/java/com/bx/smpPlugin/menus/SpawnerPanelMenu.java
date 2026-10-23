// SmpPlugin by LowBacon
package com.bx.smpPlugin.menus;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.SpawnerManager;
import com.bx.smpPlugin.models.SpawnerInstance;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.ItemUtils;
import com.bx.smpPlugin.utils.NumberUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SpawnerPanelMenu extends BaseMenu {

    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREVIOUS = 45;
    private static final int SLOT_BACK = 48;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_SORT = 50;
    private static final int SLOT_NEXT = 53;

    enum Sort {
        STACK("ʙɪɢɢᴇѕᴛ ѕᴛᴀᴄᴋ", Comparator.comparingLong(SpawnerInstance::getStackAmount).reversed()),
        STORED("ᴍᴏѕᴛ ʟᴏᴏᴛ ѕᴛᴏʀᴇᴅ", Comparator.comparingLong(SpawnerInstance::getTotalStoredItems).reversed()),
        OWNER("ᴏᴡɴᴇʀ ɴᴀᴍᴇ", Comparator.comparing(SpawnerInstance::getOwnerNameSnapshot, String.CASE_INSENSITIVE_ORDER));

        private final String label;
        private final Comparator<SpawnerInstance> order;

        Sort(String label, Comparator<SpawnerInstance> order) {
            this.label = label;
            this.order = order.thenComparingLong(SpawnerInstance::getId);
        }

        Sort next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private final String worldName;
    private final int page;
    private final Sort sort;
    private List<SpawnerInstance> shown = List.of();
    private int shownPage = 1;
    private int shownPages = 1;

    public SpawnerPanelMenu(SmpPlugin plugin, String worldName, int page) {
        this(plugin, worldName, page, Sort.STACK);
    }

    SpawnerPanelMenu(SmpPlugin plugin, String worldName, int page, Sort sort) {
        super(plugin, plugin.getSpawnerManager().getPanelTitle(worldName), 54);
        this.worldName = worldName;
        this.page = Math.max(1, page);
        this.sort = sort;
    }

    @Override
    public void build(Player player) {
        clear();
        SpawnerManager manager = plugin.getSpawnerManager();
        List<SpawnerInstance> spawners = new ArrayList<>();
        for (SpawnerInstance instance : manager.getSpawnersInWorld(worldName)) {
            if (!manager.isTemporarySpawner(instance)) {
                spawners.add(instance);
            }
        }
        spawners.sort(sort.order);

        int totalPages = totalPages(spawners.size());
        int safePage = Math.min(page, totalPages);
        int start = (safePage - 1) * PAGE_SIZE;
        shown = List.copyOf(spawners.subList(start, Math.min(spawners.size(), start + PAGE_SIZE)));
        shownPage = safePage;
        shownPages = totalPages;

        for (int slot = 0; slot < shown.size(); slot++) {
            set(slot, spawnerItem(shown.get(slot)));
        }
        if (spawners.isEmpty()) {
            set(22, ItemUtils.createItem(Material.BARRIER, "&cɴᴏ ѕᴘᴀᴡɴᴇʀѕ ʜᴇʀᴇ",
                    List.of("&7This world has no spawners any more.")));
        }

        ItemStack bar = ItemUtils.createPlaceholder(Material.BLACK_STAINED_GLASS_PANE);
        for (int slot = 45; slot < 54; slot++) {
            set(slot, bar);
        }
        if (safePage > 1) {
            set(SLOT_PREVIOUS, ItemUtils.createItem(Material.ARROW, "&aᴘʀᴇᴠɪᴏᴜѕ ᴘᴀɢᴇ", List.of("&7Page " + (safePage - 1))));
        }
        if (safePage < totalPages) {
            set(SLOT_NEXT, ItemUtils.createItem(Material.ARROW, "&aɴᴇxᴛ ᴘᴀɢᴇ", List.of("&7Page " + (safePage + 1))));
        }
        set(SLOT_BACK, ItemUtils.createItem(Material.OAK_DOOR, "&cʙᴀᴄᴋ", List.of("&7Back to the world list.")));
        set(SLOT_INFO, ItemUtils.createItem(manager.getWorldIcon(Bukkit.getWorld(worldName)),
                "&b" + SpawnerWorldListMenu.capitalize(manager.describeWorld(worldName)),
                List.of("&7ѕᴘᴀᴡɴᴇʀ ʙʟᴏᴄᴋѕ: &f" + spawners.size(),
                        "&7ᴘᴀɢᴇ: &f" + safePage + "&7/&f" + totalPages)));
        List<String> sortLore = new ArrayList<>();
        for (Sort option : Sort.values()) {
            sortLore.add(option == sort ? "&a▶ &f" + option.label : "&8  " + option.label);
        }
        sortLore.add("");
        sortLore.add("&e➜ ᴄʟɪᴄᴋ ᴛᴏ ᴄʜᴀɴɢᴇ");
        set(SLOT_SORT, ItemUtils.createItem(Material.HOPPER, "&eѕᴏʀᴛ", sortLore));
    }

    private ItemStack spawnerItem(SpawnerInstance instance) {
        SpawnerManager manager = plugin.getSpawnerManager();
        ItemStack item = ItemUtils.createItem(Material.SPAWNER,
                manager.getTypeDisplayName(instance.getMobTypeKey()) + " &7x" + NumberUtils.format(instance.getStackAmount()),
                List.of(
                        "&7ᴏᴡɴᴇʀ: &f" + instance.getOwnerNameSnapshot(),
                        "&7ᴀᴛ: &f" + instance.getX() + ", " + instance.getY() + ", " + instance.getZ(),
                        "&7ѕᴛᴏʀᴇᴅ: &f" + NumberUtils.format(instance.getTotalStoredItems()) + " items, "
                                + NumberUtils.format(instance.getStoredXp()) + " xp",
                        "",
                        "&e➜ ʟᴇꜰᴛ-ᴄʟɪᴄᴋ &7ᴛᴏ ᴛᴇʟᴇᴘᴏʀᴛ",
                        "&e➜ ʀɪɢʜᴛ-ᴄʟɪᴄᴋ &7ᴛᴏ ᴏᴘᴇɴ"
                ));
        item.setAmount((int) Math.max(1L, Math.min(64L, instance.getStackAmount())));
        return ItemUtils.hideMenuNoise(item, true);
    }

    @Override
    public void handleClick(int slot, Player player, ClickType clickType) {
        switch (slot) {
            case SLOT_PREVIOUS -> {
                if (shownPage > 1) {
                    new SpawnerPanelMenu(plugin, worldName, shownPage - 1, sort).open(player);
                }
                return;
            }
            case SLOT_NEXT -> {
                if (shownPage < shownPages) {
                    new SpawnerPanelMenu(plugin, worldName, shownPage + 1, sort).open(player);
                }
                return;
            }
            case SLOT_BACK -> {
                plugin.getSpawnerManager().openPanel(player);
                return;
            }
            case SLOT_SORT -> {
                new SpawnerPanelMenu(plugin, worldName, 1, sort.next()).open(player);
                return;
            }
            default -> {
            }
        }
        if (slot < 0 || slot >= shown.size()) {
            return;
        }

        // Look the spawner up again: it may have been broken since the page was built.
        SpawnerInstance instance = plugin.getSpawnerManager().getSpawner(shown.get(slot).getId());
        if (instance == null) {
            player.sendMessage(ColorUtils.toComponent("&cᴛʜᴀᴛ ѕᴘᴀᴡɴᴇʀ ɪѕ ɢᴏɴᴇ."));
            build(player);
            return;
        }
        if (clickType.isRightClick()) {
            plugin.getSpawnerManager().openMenu(player, instance);
            return;
        }
        teleport(player, instance);
    }

    private void teleport(Player player, SpawnerInstance instance) {
        World world = Bukkit.getWorld(instance.getWorld());
        if (world == null) {
            player.sendMessage(ColorUtils.toComponent("&cᴛʜᴀᴛ ѕᴘᴀᴡɴᴇʀ'ѕ ᴡᴏʀʟᴅ ɪѕ ɴᴏᴛ ʟᴏᴀᴅᴇᴅ."));
            return;
        }
        Location destination = new Location(world, instance.getX() + 0.5D, instance.getY() + 1D, instance.getZ() + 0.5D,
                player.getLocation().getYaw(), 90F);
        close(player);
        plugin.getSpigotScheduler().teleport(player, destination).thenAccept(success ->
                plugin.getSpigotScheduler().runEntity(player, () -> {
                    if (!Boolean.TRUE.equals(success) || !player.isOnline()) {
                        return;
                    }
                    player.sendMessage(ColorUtils.toComponent("&aᴛᴇʟᴇᴘᴏʀᴛᴇᴅ ᴛᴏ &f"
                            + instance.getOwnerNameSnapshot() + "&a'ѕ ѕᴘᴀᴡɴᴇʀ ᴀᴛ &f"
                            + instance.getX() + ", " + instance.getY() + ", " + instance.getZ() + "&a."));
                    plugin.getAntiEspManager().updatePlayer(player);
                }));
    }

    private static int totalPages(int count) {
        return Math.max(1, (int) Math.ceil(count / (double) PAGE_SIZE));
    }
}
