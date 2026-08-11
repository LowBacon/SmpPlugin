package com.bx.finnishSmp.tasks;

import com.bx.finnishSmp.FinnishSmp;

public class FfaMatchTask implements Runnable {

    private final FinnishSmp plugin;

    private FfaMatchTask(FinnishSmp plugin) {
        this.plugin = plugin;
    }

    public static void start(FinnishSmp plugin) {
        plugin.getSpigotScheduler().runGlobalTimer(new FfaMatchTask(plugin), 1L, 1L);
    }

    @Override
    public void run() {
        if (plugin.getFfaManager() != null && plugin.getFfaManager().isEnabled()) {
            plugin.getFfaManager().tick();
        }
    }
}
