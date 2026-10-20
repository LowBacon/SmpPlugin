package com.bx.smpPlugin.core;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Small per-player values for the 3.0 features: daily streaks, challenge progress, opened
 * calendar doors, spy toggles.
 *
 * <p>Each value is one short string under a section name, in the table {@code v3_player_data}
 * of the plugin's own database (SQLite, MySQL, or the MongoDB bridge). A player's values are
 * read on the login thread before they join, so every lookup while they are online is a map
 * read. Writes go to the database writer thread in order.
 */
public final class PlayerStore {

    private static final String TABLE = "v3_player_data";

    private final SmpPlugin plugin;
    private final Map<UUID, Map<String, String>> cache = new ConcurrentHashMap<>();
    private volatile boolean ready;

    public PlayerStore(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    /** Creates the table. Safe to call on every start. */
    public void initialize() {
        DatabaseManager database = plugin.getDatabaseManager();
        Connection connection = database == null ? null : database.getConnection();
        if (connection == null) {
            plugin.getLogger().warning("3.0 player data has no database connection; daily rewards and the calendar will not save.");
            return;
        }
        try (Statement statement = connection.createStatement()) {
            database.executeSchema(statement,
                    "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                            + " player_uuid TEXT,"
                            + " section TEXT,"
                            + " data_value TEXT,"
                            + " updated_at INTEGER DEFAULT 0,"
                            + " PRIMARY KEY (player_uuid, section))");
            // 3.0 also made v3_staff_notes for /notes. /notes was removed in 3.1; an existing table
            // is left untouched so no one's data disappears.
            database.executeSchema(statement,
                    "CREATE TABLE IF NOT EXISTS v3_mail ("
                            + " mail_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                            + " player_uuid TEXT,"
                            + " sender_name TEXT,"
                            + " mail_text TEXT,"
                            + " sent_at INTEGER DEFAULT 0,"
                            + " read_flag INTEGER DEFAULT 0)");
            ready = true;
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not create the 3.0 tables", exception);
        }
    }

    public boolean isReady() {
        return ready;
    }

    public Connection connection() {
        return plugin.getDatabaseManager() == null ? null : plugin.getDatabaseManager().getConnection();
    }

    /**
     * Reads every value a player has. Blocks; call it on the login thread or the database thread.
     *
     * <p>When the read fails the player stays not loaded, so features answer "still loading" instead of treating
     * them as a new player: an empty copy would let them claim every calendar door again and would overwrite what
     * the database holds on the next save.
     *
     * @return true when the player's values are in memory
     */
    public boolean loadBlocking(UUID uuid) {
        if (!ready || uuid == null) {
            return false;
        }
        Map<String, String> values = new ConcurrentHashMap<>();
        try (PreparedStatement statement = connection().prepareStatement(
                "SELECT section, data_value FROM " + TABLE + " WHERE player_uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String value = rs.getString(2);
                    if (value != null) {
                        values.put(rs.getString(1), value);
                    }
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not load 3.0 data for " + uuid
                    + "; their calendar, daily rewards and challenges stay unavailable until it loads.", exception);
            return isLoaded(uuid);
        }
        // A value written while the read was running wins over what the read found.
        Map<String, String> existing = cache.putIfAbsent(uuid, values);
        if (existing != null) {
            values.forEach(existing::putIfAbsent);
        }
        return true;
    }

    /** Loads in the background, for players already online when the plugin starts. */
    public void loadAsync(UUID uuid) {
        plugin.getDatabaseManager().executeAsync(() -> loadBlocking(uuid));
    }

    public boolean isLoaded(UUID uuid) {
        return cache.containsKey(uuid);
    }

    public void unload(UUID uuid) {
        flushDeferred(uuid);
        cache.remove(uuid);
    }

    /** The value for an online (loaded) player, or null. */
    public String get(UUID uuid, String section) {
        Map<String, String> values = cache.get(uuid);
        return values == null ? null : values.get(section);
    }

    /** Every loaded value of a player whose section is {@code prefix} or starts with {@code prefix + "."}. */
    public Map<String, String> sections(UUID uuid, String prefix) {
        Map<String, String> values = cache.get(uuid);
        Map<String, String> out = new java.util.TreeMap<>();
        if (values != null) {
            values.forEach((section, value) -> {
                if (section.equals(prefix) || section.startsWith(prefix + ".")) {
                    out.put(section, value);
                }
            });
        }
        return out;
    }

    /** Reads a value for any player, online or not, and answers on the main thread. */
    public void getAnywhere(UUID uuid, String section, Consumer<String> callback) {
        if (isLoaded(uuid)) {
            callback.accept(get(uuid, section));
            return;
        }
        plugin.getDatabaseManager().executeAsync(() -> {
            String value = readBlocking(uuid, section);
            plugin.getSpigotScheduler().runGlobal(() -> callback.accept(value));
        });
    }

    /** Reads one value straight from the database. Blocking: database thread only. */
    public String readBlocking(UUID uuid, String section) {
        if (!ready) {
            return null;
        }
        try (PreparedStatement statement = connection().prepareStatement(
                "SELECT data_value FROM " + TABLE + " WHERE player_uuid = ? AND section = ?")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, section);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not read 3.0 data for " + uuid, exception);
            return null;
        }
    }

    /** Values changed with {@link #setDeferred} and not written yet, by player and section. */
    private final Map<UUID, Map<String, String>> deferred = new ConcurrentHashMap<>();
    private static final String DELETED = "\u0000deleted";

    /**
     * Stores a value in memory now and in the database with the next {@link #flushDeferred()} (every 30 seconds,
     * when the player leaves, and at shutdown). For values that change very often and may lose a few seconds in a
     * crash, such as quest progress; never for anything that pays out.
     */
    public void setDeferred(UUID uuid, String section, String value) {
        if (uuid == null || section == null) {
            return;
        }
        Map<String, String> values = cache.get(uuid);
        if (values != null) {
            if (value == null) {
                values.remove(section);
            } else {
                values.put(section, value);
            }
        }
        String stored = value == null ? DELETED : value;
        // compute() is atomic against the remove() in flushPlayer, so a value can never land in a map that was
        // already taken for writing.
        deferred.compute(uuid, (ignored, waiting) -> {
            Map<String, String> map = waiting == null ? new ConcurrentHashMap<>() : waiting;
            map.put(section, stored);
            return map;
        });
    }

    /** Writes every deferred value (of one player, or of everyone when {@code uuid} is null). */
    public void flushDeferred(UUID uuid) {
        if (uuid != null) {
            flushPlayer(uuid);
            return;
        }
        for (UUID player : List.copyOf(deferred.keySet())) {
            flushPlayer(player);
        }
    }

    private void flushPlayer(UUID uuid) {
        Map<String, String> pending = deferred.remove(uuid);
        if (pending == null || pending.isEmpty() || !ready) {
            return;
        }
        long now = System.currentTimeMillis();
        Map<String, String> copy = new HashMap<>(pending);
        plugin.getDatabaseManager().executeAsync(() -> copy.forEach((section, value) ->
                writeBlocking(uuid, section, DELETED.equals(value) ? null : value, now)));
    }

    /** Stores a value (null deletes it) in memory now and in the database in the background. */
    public void set(UUID uuid, String section, String value) {
        if (uuid == null || section == null) {
            return;
        }
        Map<String, String> waiting = deferred.get(uuid);
        if (waiting != null) {
            // This write is newer than a deferred one for the same section; the deferred one must not follow it.
            waiting.remove(section);
        }
        Map<String, String> values = cache.get(uuid);
        if (values != null) {
            if (value == null) {
                values.remove(section);
            } else {
                values.put(section, value);
            }
        }
        if (!ready) {
            return;
        }
        long now = System.currentTimeMillis();
        plugin.getDatabaseManager().executeAsync(() -> writeBlocking(uuid, section, value, now));
    }

    private void writeBlocking(UUID uuid, String section, String value, long now) {
        // One transaction: a crash between the delete and the insert must not lose the value (a lost calendar
        // value would let every door be opened again).
        try {
            com.bx.smpPlugin.economy.EconomyStore.inTransaction(connection(), connection -> {
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM " + TABLE + " WHERE player_uuid = ? AND section = ?")) {
                    delete.setString(1, uuid.toString());
                    delete.setString(2, section);
                    delete.executeUpdate();
                }
                if (value == null) {
                    return null;
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO " + TABLE + " (player_uuid, section, data_value, updated_at) VALUES (?, ?, ?, ?)")) {
                    insert.setString(1, uuid.toString());
                    insert.setString(2, section);
                    insert.setString(3, value);
                    insert.setLong(4, now);
                    insert.executeUpdate();
                }
                return null;
            });
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not save 3.0 data for " + uuid + " (" + section + ")", exception);
        }
    }

    /** Every stored value of one section, for admin statistics. Blocking: database thread only. */
    public Map<UUID, String> readSectionBlocking(String section) {
        Map<UUID, String> out = new HashMap<>();
        if (!ready) {
            return out;
        }
        try (PreparedStatement statement = connection().prepareStatement(
                "SELECT player_uuid, data_value FROM " + TABLE + " WHERE section = ?")) {
            statement.setString(1, section);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    try {
                        out.put(UUID.fromString(rs.getString(1)), rs.getString(2));
                    } catch (IllegalArgumentException ignored) {
                        // Not a player row.
                    }
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not read 3.0 section " + section, exception);
        }
        return out;
    }

    /** Deletes one section for every player, e.g. a calendar reset. Runs in the background. */
    public void deleteSectionEverywhere(String section) {
        cache.values().forEach(values -> values.remove(section));
        if (!ready) {
            return;
        }
        plugin.getDatabaseManager().executeAsync(() -> {
            try (PreparedStatement statement = connection().prepareStatement(
                    "DELETE FROM " + TABLE + " WHERE section = ?")) {
                statement.setString(1, section);
                statement.executeUpdate();
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not delete 3.0 section " + section, exception);
            }
        });
    }
}
