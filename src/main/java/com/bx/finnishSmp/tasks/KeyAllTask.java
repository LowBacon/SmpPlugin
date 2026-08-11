package com.bx.finnishSmp.tasks;

import com.bx.finnishSmp.FinnishSmp;

/**
 * Checks every second if it's time for key-all.
 */
public class KeyAllTask implements Runnable {

    private final FinnishSmp plugin;

    public KeyAllTask(FinnishSmp plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (!plugin.getKeyAllManager().isEnabled()) return;
        plugin.getKeyAllManager().tickOnlinePlayers();
    }

    public static void start(FinnishSmp plugin) {
        plugin.getSpigotScheduler().runGlobalTimer(new KeyAllTask(plugin), 20L, 20L);
    }
}
