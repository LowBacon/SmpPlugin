package com.bx.smpPlugin.tasks;

import com.bx.smpPlugin.SmpPlugin;

public class FfaMatchTask implements Runnable {

    private final SmpPlugin plugin;

    private FfaMatchTask(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    public static void start(SmpPlugin plugin) {
        plugin.getSpigotScheduler().runGlobalTimer(new FfaMatchTask(plugin), 1L, 1L);
    }

    @Override
    public void run() {
        if (plugin.getFfaManager() != null && plugin.getFfaManager().isEnabled()) {
            plugin.getFfaManager().tick();
        }
    }
}
