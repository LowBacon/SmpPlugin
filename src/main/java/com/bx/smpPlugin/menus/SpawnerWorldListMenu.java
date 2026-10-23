// SmpPlugin by LowBacon
package com.bx.smpPlugin.menus;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.SpawnerManager;
import com.bx.smpPlugin.models.SpawnerInstance;
import com.bx.smpPlugin.utils.ItemUtils;
import com.bx.smpPlugin.utils.NumberUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SpawnerWorldListMenu extends BaseMenu {

    private static final int[] WORLD_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };
    private static final int SLOT_TOTALS = 4;
    private static final int SLOT_CLOSE = 40;

    private final Map<Integer, String> slotWorlds = new HashMap<>();

    public SpawnerWorldListMenu(SmpPlugin plugin) {
        super(plugin, plugin.getSpawnerManager().getWorldListTitle(), 45);
    }

    @Override
    public void build(Player player) {
        clear();
        slotWorlds.clear();
        fill(Material.GRAY_STAINED_GLASS_PANE);
        for (int slot = 0; slot < 9; slot++) {
            set(slot, ItemUtils.createPlaceholder(Material.BLACK_STAINED_GLASS_PANE));
            set(36 + slot, ItemUtils.createPlaceholder(Material.BLACK_STAINED_GLASS_PANE));
        }

        SpawnerManager manager = plugin.getSpawnerManager();
        List<SpawnerManager.WorldSummary> worlds = manager.getWorldSummaries().stream()
                .filter(summary -> summary.count() > 0)
                .toList();

        long blocks = 0L;
        long stacked = 0L;
        long stored = 0L;
        Set<UUID> owners = new HashSet<>();
        for (SpawnerInstance instance : manager.getAllSpawners()) {
            if (manager.isTemporarySpawner(instance)) {
                continue;
            }
            blocks++;
            stacked += instance.getStackAmount();
            stored += instance.getTotalStoredItems();
            owners.add(instance.getOwnerUuid());
        }
        set(SLOT_TOTALS, ItemUtils.createItem(Material.SPAWNER, "&d&lѕᴘᴀᴡɴᴇʀѕ", List.of(
                "&7ѕᴘᴀᴡɴᴇʀ ʙʟᴏᴄᴋѕ: &f" + NumberUtils.format(blocks),
                "&7ѕᴘᴀᴡɴᴇʀѕ ɪɴ ѕᴛᴀᴄᴋѕ: &f" + NumberUtils.format(stacked),
                "&7ɪᴛᴇᴍѕ ѕᴛᴏʀᴇᴅ: &f" + NumberUtils.format(stored),
                "&7ᴏᴡɴᴇʀѕ: &f" + owners.size()
        )));

        if (worlds.isEmpty()) {
            set(22, ItemUtils.createItem(Material.BARRIER, "&cɴᴏ ѕᴘᴀᴡɴᴇʀѕ ʏᴇᴛ",
                    List.of("&7Nobody has placed a spawner.")));
        }

        for (int index = 0; index < worlds.size() && index < WORLD_SLOTS.length; index++) {
            SpawnerManager.WorldSummary summary = worlds.get(index);
            long worldStacked = 0L;
            long worldStored = 0L;
            for (SpawnerInstance instance : manager.getSpawnersInWorld(summary.worldName())) {
                if (manager.isTemporarySpawner(instance)) {
                    continue;
                }
                worldStacked += instance.getStackAmount();
                worldStored += instance.getTotalStoredItems();
            }
            int slot = WORLD_SLOTS[index];
            set(slot, ItemUtils.createItem(
                    manager.getWorldIcon(Bukkit.getWorld(summary.worldName())),
                    "&b" + capitalize(manager.describeWorld(summary.worldName())),
                    List.of(
                            "&7ѕᴘᴀᴡɴᴇʀ ʙʟᴏᴄᴋѕ: &f" + NumberUtils.format(summary.count()),
                            "&7ѕᴘᴀᴡɴᴇʀѕ ɪɴ ѕᴛᴀᴄᴋѕ: &f" + NumberUtils.format(worldStacked),
                            "&7ɪᴛᴇᴍѕ ѕᴛᴏʀᴇᴅ: &f" + NumberUtils.format(worldStored),
                            "",
                            "&e➜ ᴄʟɪᴄᴋ ᴛᴏ ʙʀᴏᴡѕᴇ"
                    )));
            slotWorlds.put(slot, summary.worldName());
        }
        if (worlds.size() > WORLD_SLOTS.length) {
            set(SLOT_TOTALS + 4, ItemUtils.createItem(Material.PAPER, "&e+" + (worlds.size() - WORLD_SLOTS.length) + " ᴍᴏʀᴇ ᴡᴏʀʟᴅѕ",
                    List.of("&7Only the first " + WORLD_SLOTS.length + " worlds fit here.")));
        }

        set(SLOT_CLOSE, ItemUtils.createItem(Material.BARRIER, "&cᴄʟᴏѕᴇ", List.of("&7Close this menu.")));
    }

    @Override
    public void handleClick(int slot, Player player, ClickType clickType) {
        if (slot == SLOT_CLOSE) {
            close(player);
            return;
        }
        String world = slotWorlds.get(slot);
        if (world != null) {
            plugin.getSpawnerManager().openWorldPanel(player, world, 1);
        }
    }

    static String capitalize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(text.length());
        boolean upper = true;
        for (char c : text.toCharArray()) {
            builder.append(upper ? Character.toUpperCase(c) : c);
            upper = c == ' ';
        }
        return builder.toString();
    }
}
