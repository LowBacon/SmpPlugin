package com.bx.smpPlugin.listeners;

import com.bx.smpPlugin.SmpPlugin;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class PortalListener implements Listener {

    private final SmpPlugin plugin;

    public PortalListener(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        boolean teleported = event instanceof PlayerTeleportEvent;
        if (!teleported
                && event.getFrom().getBlockX() == to.getBlockX()
                && event.getFrom().getBlockY() == to.getBlockY()
                && event.getFrom().getBlockZ() == to.getBlockZ()) {
            return;
        }

        plugin.getPortalManager().handlePlayerMovement(event.getPlayer(), event.getFrom(), to, teleported);
    }
}
