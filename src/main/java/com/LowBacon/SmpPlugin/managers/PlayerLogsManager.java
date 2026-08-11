package com.bx.finnishSmp.managers;

import com.bx.finnishSmp.FinnishSmp;
import java.util.UUID;

public class PlayerLogsManager {

    private final FinnishSmp plugin;

    public PlayerLogsManager(FinnishSmp plugin) {
        this.plugin = plugin;
    }

    public void log(UUID uuid, String name, String category, String type, String details) {
        if (uuid == null) {
            return;
        }
        long timestamp = System.currentTimeMillis();
        plugin.getSpigotScheduler().runAsync(() -> {
            plugin.getDatabaseManager().addPlayerLog(uuid, name, category, type, details, timestamp);
        });
    }
}
