// SmpPlugin by LowBacon
package com.bx.smpPlugin.tasks;

import com.bx.smpPlugin.SmpPlugin;

public class AutoSaveTask implements Runnable {

    static final int DEFAULT_SECONDS = 60;
    static final int DEFAULT_MONGO_SECONDS = 300;

    private final SmpPlugin plugin;
    private final int mongoEveryRuns;
    private final int pruneEveryRuns;
    private int runs;

    public AutoSaveTask(SmpPlugin plugin, int mongoEveryRuns, int pruneEveryRuns) {
        this.plugin = plugin;
        this.mongoEveryRuns = Math.max(1, mongoEveryRuns);
        this.pruneEveryRuns = Math.max(1, pruneEveryRuns);
    }

    @Override
    public void run() {
        if (plugin.getServerWipeManager() != null && plugin.getServerWipeManager().isMaintenanceMode()) {
            return;
        }
        plugin.getPlayerDataManager().autoSaveDirty();
        runs++;
        if (runs % mongoEveryRuns == 0
                && plugin.getConfigManager().getDatabase().getBoolean("DATABASE.MONGODB.SYNC-ON-AUTOSAVE", true)) {
            plugin.getDatabaseManager().flush();
        }
        if (runs % pruneEveryRuns == 0 && plugin.getEconomyManager() != null) {
            plugin.getEconomyManager().pruneLedger();
        }
    }

    public static void start(SmpPlugin plugin) {
        int seconds = Math.max(10, Math.min(3600,
                plugin.getConfigManager().getDatabase().getInt("DATABASE.AUTOSAVE-SECONDS", DEFAULT_SECONDS)));
        int mongoSeconds = Math.max(60, Math.min(86_400, plugin.getConfigManager().getDatabase()
                .getInt("DATABASE.MONGODB.SYNC-INTERVAL-SECONDS", DEFAULT_MONGO_SECONDS)));
        int mongoEveryRuns = Math.max(1, Math.round(mongoSeconds / (float) seconds));
        int pruneEveryRuns = Math.max(1, 86_400 / seconds);
        long ticks = seconds * 20L;
        plugin.getSpigotScheduler().runAsyncTimer(new AutoSaveTask(plugin, mongoEveryRuns, pruneEveryRuns), ticks, ticks);
    }
}
