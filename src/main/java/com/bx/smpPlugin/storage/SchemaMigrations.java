package com.bx.smpPlugin.storage;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Numbered, run-once database changes.
 *
 * <p>Each migration has a version number and runs once; the versions that ran are kept in the
 * table {@code schema_migrations}, so a server that skipped a release still runs every step in
 * order. A migration either finishes completely or is not recorded, and is then tried again on
 * the next start.
 *
 * <p>Version 1 is the schema SmpPlugin 3.x builds by itself on start-up (players, homes, auctions
 * and the rest); it is recorded so the history starts there. The 4.0 tables are versions 2 and 3.
 */
public final class SchemaMigrations {

    public static final String TABLE = "schema_migrations";

    /** Runs one SQL statement; the database manager's version adapts SQLite syntax for MySQL. */
    @FunctionalInterface
    public interface SqlRunner {
        void run(Statement statement, String sql) throws SQLException;
    }

    public record Migration(int version, String description, List<String> statements, List<String> tables) {
    }

    private SchemaMigrations() {
    }

    /** Every migration, oldest first. */
    public static List<Migration> all() {
        return List.of(
                new Migration(1, "3.x schema (created by SmpPlugin on start-up)", List.of(), List.of("players")),
                new Migration(2, "4.0 economy ledger", List.of(
                        "CREATE TABLE IF NOT EXISTS economy_ledger ("
                                + " ledger_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                                + " tx_uuid TEXT NOT NULL,"
                                + " created_at INTEGER NOT NULL,"
                                + " currency TEXT NOT NULL,"
                                + " kind TEXT NOT NULL,"
                                + " reason TEXT,"
                                + " player_uuid TEXT NOT NULL,"
                                + " player_name TEXT,"
                                + " counterparty_uuid TEXT,"
                                + " counterparty_name TEXT,"
                                + " actor_uuid TEXT,"
                                + " actor_name TEXT,"
                                + " amount_minor INTEGER NOT NULL,"
                                + " balance_before_minor INTEGER NOT NULL,"
                                + " balance_after_minor INTEGER NOT NULL,"
                                + " note TEXT)",
                        "CREATE INDEX IF NOT EXISTS idx_economy_ledger_player ON economy_ledger (player_uuid, created_at)",
                        "CREATE INDEX IF NOT EXISTS idx_economy_ledger_tx ON economy_ledger (tx_uuid)",
                        "CREATE INDEX IF NOT EXISTS idx_economy_ledger_time ON economy_ledger (created_at)"),
                        List.of("economy_ledger")),
                new Migration(3, "4.0 ranked PvP", List.of(
                        "CREATE TABLE IF NOT EXISTS ranked_ratings ("
                                + " player_uuid TEXT PRIMARY KEY,"
                                + " player_name TEXT,"
                                + " rating INTEGER NOT NULL,"
                                + " peak_rating INTEGER NOT NULL,"
                                + " wins INTEGER NOT NULL DEFAULT 0,"
                                + " losses INTEGER NOT NULL DEFAULT 0,"
                                + " draws INTEGER NOT NULL DEFAULT 0,"
                                + " games INTEGER NOT NULL DEFAULT 0,"
                                + " updated_at INTEGER NOT NULL DEFAULT 0)",
                        "CREATE INDEX IF NOT EXISTS idx_ranked_ratings_rating ON ranked_ratings (rating)",
                        "CREATE TABLE IF NOT EXISTS ranked_matches ("
                                + " ranked_match_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                                + " duel_match_id INTEGER,"
                                + " player_one_uuid TEXT NOT NULL,"
                                + " player_one_name TEXT,"
                                + " player_two_uuid TEXT NOT NULL,"
                                + " player_two_name TEXT,"
                                + " winner_uuid TEXT,"
                                + " end_reason TEXT,"
                                + " rated INTEGER NOT NULL,"
                                + " unrated_reason TEXT,"
                                + " player_one_before INTEGER NOT NULL,"
                                + " player_one_after INTEGER NOT NULL,"
                                + " player_two_before INTEGER NOT NULL,"
                                + " player_two_after INTEGER NOT NULL,"
                                + " duration_seconds INTEGER NOT NULL,"
                                + " ended_at INTEGER NOT NULL)",
                        "CREATE INDEX IF NOT EXISTS idx_ranked_matches_one ON ranked_matches (player_one_uuid, ended_at)",
                        "CREATE INDEX IF NOT EXISTS idx_ranked_matches_two ON ranked_matches (player_two_uuid, ended_at)"),
                        List.of("ranked_ratings", "ranked_matches")));
    }

    /** The highest version in {@link #all()}. */
    public static int latestVersion() {
        List<Migration> migrations = all();
        return migrations.get(migrations.size() - 1).version();
    }

    /**
     * Runs every migration that has not run yet, in version order, and returns the versions it ran.
     * Throws when one fails; the ones before it stay recorded.
     */
    public static List<Integer> migrate(Connection connection, SqlRunner runner) throws SQLException {
        return migrate(connection, runner, all());
    }

    static List<Integer> migrate(Connection connection, SqlRunner runner, List<Migration> migrations) throws SQLException {
        SqlRunner sql = runner == null ? Statement::execute : runner;
        try (Statement statement = connection.createStatement()) {
            sql.run(statement, "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                    + " version INTEGER PRIMARY KEY,"
                    + " description TEXT,"
                    + " applied_at INTEGER NOT NULL)");
        }
        Set<Integer> applied = appliedVersions(connection);
        List<Integer> ran = new ArrayList<>();
        List<Migration> ordered = new ArrayList<>(migrations);
        ordered.sort(java.util.Comparator.comparingInt(Migration::version));
        for (Migration migration : ordered) {
            if (applied.contains(migration.version())) {
                continue;
            }
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (Statement statement = connection.createStatement()) {
                    for (String step : migration.statements()) {
                        sql.run(statement, step);
                    }
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO " + TABLE + " (version, description, applied_at) VALUES (?, ?, ?)")) {
                    insert.setInt(1, migration.version());
                    insert.setString(2, migration.description());
                    insert.setLong(3, System.currentTimeMillis());
                    insert.executeUpdate();
                }
                connection.commit();
                ran.add(migration.version());
            } catch (SQLException exception) {
                connection.rollback();
                throw new SQLException("Database migration " + migration.version() + " (" + migration.description()
                        + ") failed: " + exception.getMessage(), exception);
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
        return ran;
    }

    public static Set<Integer> appliedVersions(Connection connection) throws SQLException {
        Set<Integer> versions = new TreeSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT version FROM " + TABLE)) {
            while (rows.next()) {
                versions.add(rows.getInt(1));
            }
        }
        return versions;
    }

    /** The highest version that ran, or 0 before the first migration. */
    public static int currentVersion(Connection connection) {
        try {
            return appliedVersions(connection).stream().mapToInt(Integer::intValue).max().orElse(0);
        } catch (SQLException exception) {
            return 0;
        }
    }

    /** Tables the recorded migrations should have made but that do not exist (empty when healthy). */
    public static List<String> missingTables(Connection connection) throws SQLException {
        Set<Integer> applied = appliedVersions(connection);
        Set<String> existing = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet tables = metaData.getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                existing.add(tables.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
            }
        }
        List<String> missing = new ArrayList<>();
        for (Migration migration : all()) {
            if (!applied.contains(migration.version())) {
                continue;
            }
            for (String table : migration.tables()) {
                if (!existing.contains(table)) {
                    missing.add(table);
                }
            }
        }
        return missing;
    }
}
