package com.bx.smpPlugin.tasks;

import com.bx.smpPlugin.SmpPlugin;

/**
 * Auto-saves dirty player data every 5 minutes.
 */
public class AutoSaveTask implements Runnable {

    private final SmpPlugin plugin;

    public AutoSaveTask(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (plugin.getServerWipeManager() != null && plugin.getServerWipeManager().isMaintenanceMode()) {
            return;
        }
        plugin.getPlayerDataManager().autoSaveDirty();
        if (plugin.getConfigManager().getDatabase().getBoolean("DATABASE.MONGODB.SYNC-ON-AUTOSAVE", true)) {
            plugin.getDatabaseManager().flush();
        }
    }

    public static void start(SmpPlugin plugin) {
        plugin.getSpigotScheduler().runAsyncTimer(new AutoSaveTask(plugin), 5 * 60 * 20L, 5 * 60 * 20L);
    }
}
