package com.bx.finnishSmp.tasks;

import com.bx.finnishSmp.FinnishSmp;

public final class AuctionOrderBotTask implements Runnable {

    private final FinnishSmp plugin;

    private AuctionOrderBotTask(FinnishSmp plugin) {
        this.plugin = plugin;
    }

    public static void start(FinnishSmp plugin) {
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
