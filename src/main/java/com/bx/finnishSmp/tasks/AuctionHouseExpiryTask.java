package com.bx.finnishSmp.tasks;

import com.bx.finnishSmp.FinnishSmp;

public class AuctionHouseExpiryTask implements Runnable {

    private final FinnishSmp plugin;

    private AuctionHouseExpiryTask(FinnishSmp plugin) {
        this.plugin = plugin;
    }

    public static void start(FinnishSmp plugin) {
        long configuredSeconds = plugin.getConfigManager().getAuctionHouse()
                .getLong("SETTINGS.EXPIRE_CHECK_SECONDS", 30L);
        long periodTicks = Math.max(20L, configuredSeconds * 20L);
        plugin.getSpigotScheduler().runGlobalTimer(new AuctionHouseExpiryTask(plugin), periodTicks, periodTicks);
    }

    @Override
    public void run() {
        if (plugin.getAuctionHouseManager() != null && plugin.getAuctionHouseManager().isEnabled()) {
            plugin.getAuctionHouseManager().expireListings().exceptionally(throwable -> {
                plugin.getLogger().warning("Auction House expiry scan failed: " + throwable.getMessage());
                return 0;
            });
        }
    }
}
