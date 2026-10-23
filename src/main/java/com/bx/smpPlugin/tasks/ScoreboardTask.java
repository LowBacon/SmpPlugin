// SmpPlugin by LowBacon
package com.bx.smpPlugin.tasks;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.OptimizationManager;

public class ScoreboardTask implements Runnable {

    private final SmpPlugin plugin;

    public ScoreboardTask(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (plugin.getOptimizationManager() != null
                && !plugin.getOptimizationManager().shouldRun(OptimizationManager.OptimizedTask.SCOREBOARD)) {
            return;
        }
        plugin.getScoreboardManager().updateAll();
    }

    public static void start(SmpPlugin plugin) {
        if (!plugin.getScoreboardManager().isRuntimeSupported()) {
            return;
        }
        plugin.getSpigotScheduler().runGlobalTimer(new ScoreboardTask(plugin), 2L, 2L);
    }
}
