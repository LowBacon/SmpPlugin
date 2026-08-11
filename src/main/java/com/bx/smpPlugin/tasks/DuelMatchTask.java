package com.bx.smpPlugin.tasks;

import com.bx.smpPlugin.SmpPlugin;

public class DuelMatchTask implements Runnable {

    private final SmpPlugin plugin;

    private DuelMatchTask(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    public static void start(SmpPlugin plugin) {
        plugin.getSpigotScheduler().runGlobalTimer(new DuelMatchTask(plugin), 1L, 1L);
    }

    @Override
    public void run() {
        if (plugin.getDuelManager() != null && plugin.getDuelManager().isEnabled()) {
            plugin.getDuelManager().tick();
        }
    }
}
