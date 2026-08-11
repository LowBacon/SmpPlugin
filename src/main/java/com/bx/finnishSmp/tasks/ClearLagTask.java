package com.bx.finnishSmp.tasks;

import com.bx.finnishSmp.FinnishSmp;

/**
 * Runs every N minutes. Broadcasts countdown warnings then clears entities.
 */
public class ClearLagTask implements Runnable {

    private static final int[] WARN_AT = {60, 30, 15, 10, 5, 4, 3, 2, 1};

    private final FinnishSmp plugin;
    private int secondsLeft;

    public ClearLagTask(FinnishSmp plugin) {
        this.plugin = plugin;
        this.secondsLeft = plugin.getClearLagManager().getIntervalMinutes() * 60;
    }

    @Override
    public void run() {
        if (!plugin.getClearLagManager().isEnabled()) return;
        secondsLeft--;
        for (int w : WARN_AT) {
            if (secondsLeft == w) {
                plugin.getClearLagManager().broadcastCountdown(w);
                return;
            }
        }
        if (secondsLeft <= 0) {
            int cleared = plugin.getClearLagManager().clearEntities();
            plugin.getClearLagManager().broadcastSuccess(cleared);
            secondsLeft = plugin.getClearLagManager().getIntervalMinutes() * 60;
        }
    }

    public static void start(FinnishSmp plugin) {
        plugin.getSpigotScheduler().runGlobalTimer(new ClearLagTask(plugin), 20L, 20L); // every second
    }
}
