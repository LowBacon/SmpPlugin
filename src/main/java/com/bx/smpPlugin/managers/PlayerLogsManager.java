package com.bx.smpPlugin.managers;

import com.bx.smpPlugin.SmpPlugin;

import java.util.Collection;
import java.util.UUID;

public class PlayerLogsManager {

    /** Category shared by every chat entry, so the logs menu can pick an icon for them. */
    public static final String CHAT_CATEGORY = "chat";
    /** Log type written for a public chat message, and the key the chat log browser reads back. */
    public static final String PUBLIC_CHAT_TYPE = "CHAT_PUBLIC";
    /** Log type written for a team chat message. */
    public static final String TEAM_CHAT_TYPE = "CHAT_TEAM";

    private final SmpPlugin plugin;

    public PlayerLogsManager(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Queues one entry on the database thread. Every write shares that one thread, so a burst of
     * chat cannot open a pile of connections or race the save that runs on quit.
     */
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
