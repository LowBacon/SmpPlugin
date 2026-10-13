package com.bx.smpPlugin.tasks;

import com.bx.smpPlugin.SmpPlugin;

/**
 * Checks every 30 seconds whether the Billford trade rotation timer has
 * expired and, if so, advances to the next trade.
 */
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
