// SmpPlugin by LowBacon
package com.bx.smpPlugin.menus;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.SpawnerManager;
import com.bx.smpPlugin.models.SpawnerInstance;
import com.bx.smpPlugin.models.SpawnerTypeDefinition;
import com.bx.smpPlugin.utils.ItemUtils;
import com.bx.smpPlugin.utils.SoundUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpawnerFilterMenu extends BaseMenu {

    private static final int SIZE = 27;
    private static final int BACK_SLOT = 22;
    private static final int MAX_DROPS = 18;

    private final long spawnerId;
    private final Map<Integer, SpawnerTypeDefinition.DropDefinition> slotDrops = new HashMap<>();

    public SpawnerFilterMenu(SmpPlugin plugin, long spawnerId) {
        super(plugin, "&8Loot filter", SIZE);
        this.spawnerId = spawnerId;
    }

    @Override
    public void build(Player player) {
        clear();
        slotDrops.clear();
        fill(Material.GRAY_STAINED_GLASS_PANE);
        set(BACK_SLOT, ItemUtils.createItem(Material.ARROW, "&aBack", List.of("&7Return to the spawner.")));

        SpawnerManager manager = plugin.getSpawnerManager();
        SpawnerInstance instance = manager.getSpawner(spawnerId);
        SpawnerTypeDefinition type = instance == null ? null : manager.getTypeDefinition(instance.getMobTypeKey());
        if (type == null || type.drops().isEmpty()) {
            set(13, ItemUtils.createItem(Material.BARRIER, "&cNothing to filter",
                    List.of("&7This spawner has no drops.")));
            return;
        }

        List<SpawnerTypeDefinition.DropDefinition> drops = type.drops();
        for (int i = 0; i < drops.size() && i < MAX_DROPS; i++) {
            SpawnerTypeDefinition.DropDefinition drop = drops.get(i);
            int slot = i;
            slotDrops.put(slot, drop);
            set(slot, dropItem(instance, drop));
        }
    }

    private ItemStack dropItem(SpawnerInstance instance, SpawnerTypeDefinition.DropDefinition drop) {
        boolean off = isOff(instance, drop);
        List<String> lore = new ArrayList<>();
        lore.add(off ? "&c✘ Not stored" : "&a✔ Stored");
        lore.add("");
        lore.add("&7Click to " + (off ? "start" : "stop") + " storing it.");
        String name = (off ? "&7&m" : "&f") + plugin.getWorthManager().prettifyMaterial(drop.material());
        ItemStack item = ItemUtils.createItem(drop.material(), name, lore);
        return ItemUtils.hideMenuNoise(ItemUtils.setGlint(item, !off), true);
    }

    private static boolean isOff(SpawnerInstance instance, SpawnerTypeDefinition.DropDefinition drop) {
        return instance.isLootDisabled(drop.key()) || instance.isLootDisabled(drop.material().name());
    }

    @Override
    public void handleClick(int slot, Player player, ClickType clickType) {
        SpawnerManager manager = plugin.getSpawnerManager();
        SpawnerInstance instance = manager.getSpawner(spawnerId);
        if (instance == null || !manager.canOpen(player, instance)) {
            player.closeInventory();
            return;
        }
        if (slot == BACK_SLOT) {
            SoundUtils.play(player, plugin.getConfigManager().getSound("MENUS.BUTTON-CLICK"));
            manager.openMenu(player, instance);
            return;
        }
        SpawnerTypeDefinition.DropDefinition drop = slotDrops.get(slot);
        if (drop == null) {
            return;
        }
        boolean nowOff = !isOff(instance, drop);
        instance.setLootDisabled(drop.key(), nowOff);
        // Older filters were keyed by material name; clear that too so one click always flips it.
        if (!nowOff) {
            instance.setLootDisabled(drop.material().name(), false);
        }
        instance.setUpdatedAt(System.currentTimeMillis());
        manager.saveSpawnerAndLoot(instance);
        SoundUtils.play(player, plugin.getConfigManager().getSpawners()
                .getString("GUI.MAIN.SOUNDS.CLICK", "minecraft:ui.button.click|0.6|1.0"));
        set(slot, dropItem(instance, drop));
    }
}
