package com.bx.finnishSmp.tasks;

import com.bx.finnishSmp.FinnishSmp;

public class DuelMatchTask implements Runnable {

    private final FinnishSmp plugin;

    private DuelMatchTask(FinnishSmp plugin) {
        this.plugin = plugin;
    }

    public static void start(FinnishSmp plugin) {
        plugin.getSpigotScheduler().runGlobalTimer(new DuelMatchTask(plugin), 1L, 1L);
    }

    @Override
    public void run() {
        if (plugin.getDuelManager() != null && plugin.getDuelManager().isEnabled()) {
            plugin.getDuelManager().tick();
        }
    }
}
