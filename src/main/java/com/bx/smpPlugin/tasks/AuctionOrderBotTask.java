package com.bx.smpPlugin.tasks;

import com.bx.smpPlugin.SmpPlugin;

public final class AuctionOrderBotTask implements Runnable {

    private final SmpPlugin plugin;

    private AuctionOrderBotTask(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    public static void start(SmpPlugin plugin) {
        // Tick every 20 seconds
        long periodTicks = 400L; 
        plugin.getSpigotScheduler().runAsyncTimer(new AuctionOrderBotTask(plugin), periodTicks, periodTicks);
    }

    @Override
    public void run() {
        if (plugin.getAuctionOrderBotManager() != null) {
            plugin.getAuctionOrderBotManager().tick();
        }
    }
}
