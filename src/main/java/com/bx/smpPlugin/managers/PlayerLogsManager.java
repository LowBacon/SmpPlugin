// SmpPlugin by LowBacon
package com.bx.smpPlugin.managers;

import com.bx.smpPlugin.SmpPlugin;

import java.util.Collection;
import java.util.UUID;

public class PlayerLogsManager {

    public static final String CHAT_CATEGORY = "chat";
    public static final String PUBLIC_CHAT_TYPE = "CHAT_PUBLIC";
    public static final String TEAM_CHAT_TYPE = "CHAT_TEAM";

    private final SmpPlugin plugin;

    public PlayerLogsManager(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    public void log(UUID uuid, String name, String category, String type, String details) {
        if (uuid == null) {
            return;
        }
        long timestamp = System.currentTimeMillis();
        plugin.getDatabaseManager().executeAsync(() ->
                plugin.getDatabaseManager().addPlayerLog(uuid, name, category, type, details, timestamp));
    }

    public void logBatch(Collection<DatabaseManager.PlayerLogRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        plugin.getDatabaseManager().executeAsync(() -> plugin.getDatabaseManager().addPlayerLogBatch(records));
    }
}
