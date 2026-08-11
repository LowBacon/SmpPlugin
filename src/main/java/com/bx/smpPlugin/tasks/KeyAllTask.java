package com.bx.smpPlugin.tasks;

import com.bx.smpPlugin.SmpPlugin;

/**
 * Checks every second if it's time for key-all.
 */
public class KeyAllTask implements Runnable {

    private final SmpPlugin plugin;

    public KeyAllTask(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (!plugin.getKeyAllManager().isEnabled()) return;
        plugin.getKeyAllManager().tickOnlinePlayers();
    }

    public static void start(SmpPlugin plugin) {
        plugin.getSpigotScheduler().runGlobalTimer(new KeyAllTask(plugin), 20L, 20L);
    }
}
