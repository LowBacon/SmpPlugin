package com.bx.smpPlugin.coinflip.bukkit;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Loads preferences and pays claims on join; cleans up per-player state on quit. */
public final class CoinFlipListener implements Listener {

    private final CoinFlipModule module;

    public CoinFlipListener(CoinFlipModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Player data is loaded by the join handler first; a short delay keeps claims from racing it.
        module.plugin().getSpigotScheduler().runEntityLater(player, () -> {
            if (player.isOnline() && module.service().recovered()) {
                module.service().playerJoined(player.getUniqueId());
            }
        }, 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        module.animator().stop(event.getPlayer().getUniqueId());
        module.presenter().forget(event.getPlayer().getUniqueId());
        module.service().playerQuit(event.getPlayer().getUniqueId());
    }
}
