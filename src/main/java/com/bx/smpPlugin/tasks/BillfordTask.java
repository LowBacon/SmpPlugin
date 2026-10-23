// SmpPlugin by LowBacon
package com.bx.smpPlugin.tasks;

import com.bx.smpPlugin.SmpPlugin;

public class BillfordTask implements Runnable {

    private final SmpPlugin plugin;

    private BillfordTask(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    public static void start(SmpPlugin plugin) {
        // 600 ticks = 30 seconds; first check after 30 s as well
        plugin.getSpigotScheduler().runGlobalTimer(new BillfordTask(plugin), 600L, 600L);
    }

    @Override
    public void run() {
        if (plugin.getBillfordManager().isTimeToAdvance()) {
            plugin.getBillfordManager().advanceTrade();
        }
    }
}
