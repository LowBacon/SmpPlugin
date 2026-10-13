package com.bx.smpPlugin.tasks;

import com.bx.smpPlugin.SmpPlugin;

/**
 * Checks every 30 seconds whether the farming meta rotation timer has expired and, if so,
 * moves the meta on to the next item in the rotation.
 */
public class FarmingMetaTask implements Runnable {

    private final SmpPlugin plugin;

    private FarmingMetaTask(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    public static void start(SmpPlugin plugin) {
        // 600 ticks = 30 seconds; first check after 30 s as well
        plugin.getSpigotScheduler().runGlobalTimer(new FarmingMetaTask(plugin), 600L, 600L);
    }

    @Override
    public void run() {
        if (!plugin.getFeatureManager().isEnabled(com.bx.smpPlugin.managers.FeatureManager.Feature.WORTH)) {
            return;
        }
        if (plugin.getFarmingMetaManager().isTimeToRotate()) {
            plugin.getFarmingMetaManager().rotate();
        }
    }
}
