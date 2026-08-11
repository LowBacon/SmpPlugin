package com.bx.smpPlugin.tasks;

import com.bx.smpPlugin.SmpPlugin;
import org.bukkit.entity.Player;

public class RTPZoneTask implements Runnable {

    private final SmpPlugin plugin;

    public RTPZoneTask(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (plugin.getRtpZoneManager() == null) {
            return;
        }
        plugin.getSpigotScheduler().forEachOnlinePlayer((Player player) -> plugin.getRtpZoneManager().tick(player));
    }

    public static void start(SmpPlugin plugin) {
        plugin.getSpigotScheduler().runGlobalTimer(new RTPZoneTask(plugin), 20L, 20L);
    }
}
