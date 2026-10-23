// SmpPlugin by LowBacon
package com.bx.smpPlugin.extras;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Location;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ExtrasListener implements Listener {

    public static final String HARMLESS_FIREWORK_TAG = "smpplugin_harmless_firework";
    public static final String SEAT_TAG = "smpplugin_seat";

    private final SmpPlugin plugin;
    private final Map<UUID, Location> lastLocations = new ConcurrentHashMap<>();

    public ExtrasListener(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    public Location lastLocation(UUID uuid) {
        Location location = lastLocations.get(uuid);
        return location == null ? null : location.clone();
    }

    private boolean trackable(Player player) {
        if (plugin.getHomeManager() != null && plugin.getHomeManager().isInArena(player)) {
            return false;
        }
        return plugin.getLobbyManager() == null || !plugin.getLobbyManager().isInLobby(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause != PlayerTeleportEvent.TeleportCause.COMMAND && cause != PlayerTeleportEvent.TeleportCause.PLUGIN) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to != null && from.getWorld() != null && from.getWorld().equals(to.getWorld()) && from.distanceSquared(to) < 16D) {
            return; // a nudge, not a journey
        }
        if (trackable(event.getPlayer())) {
            lastLocations.put(event.getPlayer().getUniqueId(), from.clone());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        boolean allowed = plugin.getConfigManager().getExtras() == null
                || plugin.getConfigManager().getExtras().getBoolean("UTILITIES.BACK-AFTER-DEATH", true);
        if (allowed && PermissionUtils.has(player, "smpplugin.back.death") && trackable(player)) {
            lastLocations.put(player.getUniqueId(), player.getLocation().clone());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        lastLocations.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFireworkDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework firework && firework.getScoreboardTags().contains(HARMLESS_FIREWORK_TAG)) {
            event.setCancelled(true);
        }
    }

}
