// SmpPlugin by LowBacon
package com.bx.smpPlugin.listeners;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.SpawnManager;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;

import java.util.Set;

public class ItemDropListener implements Listener {

    private final SmpPlugin plugin;

    public ItemDropListener(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();

        // 1. Check if prevention is configured/enabled
        boolean preventSpawn = plugin.getConfigManager().getConfig().getBoolean("PREVENT-ITEM-DROP.SPAWN", true);
        boolean preventAfk = plugin.getConfigManager().getConfig().getBoolean("PREVENT-ITEM-DROP.AFK", true);

        if (!preventSpawn && !preventAfk) {
            return;
        }

        // 2. Check if player has bypass permission
        String bypassPermission = plugin.getConfigManager().getConfig().getString(
                "PREVENT-ITEM-DROP.BYPASS-PERMISSION", "smpplugin.preventdrop.bypass");
        if (PermissionUtils.has(player, bypassPermission)) {
            return;
        }

        boolean block = false;

        // 3. Check spawn prevention
        if (preventSpawn && isInSpawnArea(player)) {
            block = true;
        }

        // 4. Check AFK prevention
        if (!block && preventAfk && isInAfkArea(player)) {
            block = true;
        }

        if (block) {
            event.setCancelled(true);
            String message = plugin.getConfigManager().getConfig().getString(
                    "PREVENT-ITEM-DROP.MESSAGE", "&c✗ You are not allowed to drop items in spawn or AFK areas!");
            if (message != null && !message.isBlank()) {
                player.sendMessage(ColorUtils.toComponent(message));
            }
        }
    }

    private boolean isInSpawnArea(Player player) {
        // Check if player is in spawn cuboids
        if (plugin.getSpawnManager().isInSpawnArea(player)) {
            return true;
        }
        // Fallback or additional check for other Spawn areas configured in SpawnManager
        Set<String> spawnCuboids = plugin.getSpawnManager().getAreaCuboidNames(SpawnManager.AreaType.SPAWN);
        if (!spawnCuboids.isEmpty() && plugin.getCuboidManager().isInAnyCuboid(player, spawnCuboids.toArray(String[]::new))) {
            return true;
        }
        return false;
    }

    private boolean isInAfkArea(Player player) {
        return plugin.getAfkManager().zoneAt(player.getLocation()) != null;
    }
}
