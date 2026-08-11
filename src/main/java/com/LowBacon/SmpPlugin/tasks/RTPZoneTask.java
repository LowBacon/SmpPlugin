package com.bx.finnishSmp.tasks;

import com.bx.finnishSmp.FinnishSmp;
import org.bukkit.entity.Player;

public class RTPZoneTask implements Runnable {

    private final FinnishSmp plugin;

    public RTPZoneTask(FinnishSmp plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (plugin.getRtpZoneManager() == null) {
            return;
        }
        plugin.getSpigotScheduler().forEachOnlinePlayer((Player player) -> plugin.getRtpZoneManager().tick(player));
    }

    public static void start(FinnishSmp plugin) {
        plugin.getSpigotScheduler().runGlobalTimer(new RTPZoneTask(plugin), 20L, 20L);
    }
}
