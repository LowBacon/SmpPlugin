package com.bx.smpPlugin.managers;

import com.bx.smpPlugin.SmpPlugin;
import java.util.UUID;

public class PlayerLogsManager {

    private final SmpPlugin plugin;

    public PlayerLogsManager(SmpPlugin plugin) {
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
