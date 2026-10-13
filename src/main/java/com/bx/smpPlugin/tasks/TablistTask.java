package com.bx.smpPlugin.tasks;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.OptimizationManager;

/**
 * Updates tablist header/footer and entry names for all players every 40 ticks (2s).
 */
public class TablistTask implements Runnable {

    private final SmpPlugin plugin;

    public TablistTask(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (plugin.getOptimizationManager() != null
                && !plugin.getOptimizationManager().shouldRun(OptimizationManager.OptimizedTask.TABLIST)) {
            return;
        }
        plugin.getTablistManager().updateAll();
        plugin.getTablistManager().updateNamesAll();
    }

    public static void start(SmpPlugin plugin) {
        plugin.getSpigotScheduler().runGlobalTimer(new TablistTask(plugin), 40L, 40L);
    }
}
