package com.bx.smpPlugin.tasks;

import com.bx.smpPlugin.SmpPlugin;

public class SpawnerGenerationTask implements Runnable {

    private final SmpPlugin plugin;

    private SpawnerGenerationTask(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    public static void start(SmpPlugin plugin) {
        long configuredSeconds = plugin.getConfigManager().getSpawners()
                .getLong("SETTINGS.GENERATION_INTERVAL_SECONDS", 5L);
        long periodTicks = Math.max(20L, configuredSeconds * 20L);
        plugin.getSpigotScheduler().runGlobalTimer(new SpawnerGenerationTask(plugin), periodTicks, periodTicks);
    }

    @Override
    public void run() {
        if (plugin.getSpawnerManager() != null && plugin.getSpawnerManager().isEnabled()) {
            plugin.getSpawnerManager().processGeneration();
        }
    }
}
