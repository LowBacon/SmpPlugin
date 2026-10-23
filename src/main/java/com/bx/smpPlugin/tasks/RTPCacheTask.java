// SmpPlugin by LowBacon
package com.bx.smpPlugin.tasks;

import com.bx.smpPlugin.SmpPlugin;

public class RTPCacheTask implements Runnable {

    private final SmpPlugin plugin;

    public RTPCacheTask(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (org.bukkit.Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }
        if (plugin.getRtpManager() == null || !plugin.getRtpManager().isEnabled()) {
            return;
        }
        plugin.getRtpManager().refillPreCacheAllWorlds();
    }

    public static void start(SmpPlugin plugin) {
        // 2 seconds after enable, then every 5 seconds. Long enough to miss the join TPS spike,
        // short enough that the RTP button can hit a cached far spot instead of searching live.
        plugin.getSpigotScheduler().runGlobalTimer(new RTPCacheTask(plugin), 40L, 100L);
    }
}
