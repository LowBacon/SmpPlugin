package com.bx.smpPlugin.listeners;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.models.PlayerData;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetEvent;

public class PhantomListener implements Listener {

    private final SmpPlugin plugin;

    public PhantomListener(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPhantomTarget(EntityTargetEvent event) {
        if (!(event.getEntity() instanceof Phantom)) return;
        if (!(event.getTarget() instanceof Player player)) return;

        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data != null && !data.isPhantomEnabled()) {
            event.setCancelled(true);
        }
    }
}
