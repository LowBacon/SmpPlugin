package com.bx.finnishSmp.tasks;

import com.bx.finnishSmp.FinnishSmp;
import com.bx.finnishSmp.managers.OptimizationManager;

/**
 * Updates all player scoreboards every 2 ticks (~10x/sec).
 */
public class ScoreboardTask implements Runnable {

    private final FinnishSmp plugin;

    public ScoreboardTask(FinnishSmp plugin) {
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

    public static void start(FinnishSmp plugin) {
        if (!plugin.getScoreboardManager().isRuntimeSupported()) {
            return;
        }
        plugin.getSpigotScheduler().runGlobalTimer(new ScoreboardTask(plugin), 2L, 2L);
    }
}
