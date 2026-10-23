// SmpPlugin by LowBacon
package com.bx.smpPlugin.afk;

import com.bx.smpPlugin.SmpPlugin;

public final class AfkTask implements Runnable {

    private final SmpPlugin plugin;

    private AfkTask(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    public static void start(SmpPlugin plugin) {
        plugin.getSpigotScheduler().runGlobalTimer(new AfkTask(plugin), 20L, 20L);
    }

    @Override
    public void run() {
        AfkManager afk = plugin.getAfkManager();
        if (afk != null && afk.isEnabled()) {
            plugin.getSpigotScheduler().forEachOnlinePlayer(afk::tick);
        }
    }
}
