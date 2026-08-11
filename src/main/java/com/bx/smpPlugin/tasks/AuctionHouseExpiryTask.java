package com.bx.smpPlugin.tasks;

import com.bx.smpPlugin.SmpPlugin;

public class AuctionHouseExpiryTask implements Runnable {

    private final SmpPlugin plugin;

    private AuctionHouseExpiryTask(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    public static void start(SmpPlugin plugin) {
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
