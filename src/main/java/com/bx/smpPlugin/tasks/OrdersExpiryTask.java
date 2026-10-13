package com.bx.smpPlugin.tasks;

import com.bx.smpPlugin.SmpPlugin;

public class OrdersExpiryTask implements Runnable {

    private final SmpPlugin plugin;

    private OrdersExpiryTask(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    public static void start(SmpPlugin plugin) {
        long configuredSeconds = plugin.getConfigManager().getOrders()
                .getLong("SETTINGS.EXPIRE_CHECK_SECONDS", 30L);
        long periodTicks = Math.max(20L, configuredSeconds * 20L);
        plugin.getSpigotScheduler().runGlobalTimer(new OrdersExpiryTask(plugin), periodTicks, periodTicks);
    }

    @Override
    public void run() {
        if (plugin.getOrdersManager() != null && plugin.getOrdersManager().isEnabled()) {
            plugin.getOrdersManager().expireOrders();
        }
    }
}
