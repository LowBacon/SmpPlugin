// SmpPlugin by LowBacon
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
        boolean countdownZone = plugin.getRtpZoneManager() != null && plugin.getRtpZoneManager().isEnabled();
        boolean queueZone = plugin.getRtpQueueManager() != null && plugin.getRtpQueueManager().isZoneActive();
        if (!countdownZone && !queueZone) {
            return;
        }

        plugin.getSpigotScheduler().forEachOnlinePlayer((Player player) -> {
            if (countdownZone) {
                plugin.getRtpZoneManager().tick(player);
            }
            if (queueZone) {
                plugin.getRtpQueueManager().tickZone(player);
            }
        });
    }

    public static void start(SmpPlugin plugin) {
        plugin.getSpigotScheduler().runGlobalTimer(new RTPZoneTask(plugin), 20L, 20L);
    }
}
