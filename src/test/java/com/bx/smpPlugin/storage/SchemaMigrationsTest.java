package com.bx.smpPlugin.storage;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaMigrationsTest {

    private static Connection database() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement statement = connection.createStatement()) {
            // The 3.x schema made by DatabaseManager; migration 1 only records it.
            statement.execute("CREATE TABLE players (uuid TEXT PRIMARY KEY, username TEXT)");
        }
        return connection;
    }

    @Test
    void everyMigrationRunsOnceInOrder() throws Exception {
        try (Connection connection = database()) {
            List<Integer> first = SchemaMigrations.migrate(connection, null);
            assertEquals(List.of(1, 2, 3), first);
            assertEquals(SchemaMigrations.latestVersion(), SchemaMigrations.currentVersion(connection));
            assertTrue(SchemaMigrations.missingTables(connection).isEmpty());

            List<Integer> second = SchemaMigrations.migrate(connection, null);
            assertTrue(second.isEmpty(), "nothing runs twice");
        }
    }

    @Test
    void aFailedMigrationIsRolledBackAndRetriedLater() throws Exception {
        try (Connection connection = database()) {
            List<SchemaMigrations.Migration> broken = List.of(
                    new SchemaMigrations.Migration(1, "ok", List.of("CREATE TABLE a (x INTEGER)"), List.of("a")),
                    new SchemaMigrations.Migration(2, "broken",
                            List.of("CREATE TABLE b (x INTEGER)", "THIS IS NOT SQL"), List.of("b")));
            assertThrows(SQLException.class, () -> SchemaMigrations.migrate(connection, null, broken));
            assertEquals(Set.of(1), SchemaMigrations.appliedVersions(connection));
            // Table b was created inside the failed migration and rolled back with it.
            try (Statement statement = connection.createStatement()) {
                assertThrows(SQLException.class, () -> statement.executeQuery("SELECT * FROM b"));
            }

            List<SchemaMigrations.Migration> fixed = List.of(broken.get(0),
                    new SchemaMigrations.Migration(2, "fixed", List.of("CREATE TABLE b (x INTEGER)"), List.of("b")));
            assertEquals(List.of(2), SchemaMigrations.migrate(connection, null, fixed));
        }
    }

    @Test
    void aMissingTableIsReported() throws Exception {
        try (Connection connection = database()) {
            SchemaMigrations.migrate(connection, null);
            try (Statement statement = connection.createStatement()) {
                statement.execute("DROP TABLE ranked_matches");
            }
            assertEquals(List.of("ranked_matches"), SchemaMigrations.missingTables(connection));
        }
    }
}
