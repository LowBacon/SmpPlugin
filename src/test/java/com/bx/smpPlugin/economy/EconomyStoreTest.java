package com.bx.smpPlugin.economy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyStoreTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    void anOlderBalanceNeverReplacesANewerOne() throws Exception {
        try (Connection connection = memory()) {
            insertPlayer(connection, ALICE, 10D, 0L);
            assertTrue(EconomyStore.writeSnapshot(connection, snapshot(ALICE, 5000L, 3L)));
            // A slower writer (e.g. CoinFlip's connection) arrives later with version 2.
            assertFalse(EconomyStore.writeSnapshot(connection, snapshot(ALICE, 2000L, 2L)));
            // The same version again is ignored too.
            assertFalse(EconomyStore.writeSnapshot(connection, snapshot(ALICE, 1L, 3L)));

            EconomyStore.StoredBalance stored = EconomyStore.readBalance(connection, ALICE);
            assertEquals(5000L, stored.cents());
            assertEquals(3L, stored.version());
            assertTrue(EconomyStore.writeSnapshot(connection, snapshot(ALICE, 7000L, 4L)));
            assertEquals(7000L, EconomyStore.readBalance(connection, ALICE).cents());
        }
    }

    @Test
    void balancesFarAboveThirtyFiveTrillionAreStoredToTheCent() throws Exception {
        try (Connection connection = memory()) {
            insertPlayer(connection, ALICE, 10D, 0L);
            assertEquals(1_000L, EconomyStore.readBalance(connection, ALICE).cents(), "a row from before 4.1.0");
            long huge = 1_234_567_890_123_456_789L;
            assertTrue(EconomyStore.writeSnapshot(connection, snapshot(ALICE, huge, 1L)));
            assertEquals(huge, EconomyStore.readBalance(connection, ALICE).cents());
            assertTrue(EconomyStore.compareAndSet(connection, ALICE, 1L, Long.MAX_VALUE));
            assertEquals(Long.MAX_VALUE, EconomyStore.readBalance(connection, ALICE).cents());
        }
    }

    @Test
    void aBalanceChangedOnlyInTheMoneyColumnIsReadFromThere() throws Exception {
        try (Connection connection = memory()) {
            insertPlayer(connection, ALICE, 10D, 0L);
            assertTrue(EconomyStore.writeSnapshot(connection, snapshot(ALICE, 48_000L, 1L)));
            try (Statement statement = connection.createStatement()) {
                // A wipe, an older plugin version or a hand-written SQL change sets money without money_cents.
                statement.executeUpdate("UPDATE players SET money = 25 WHERE uuid = '" + ALICE + "'");
            }
            assertEquals(2_500L, EconomyStore.readBalance(connection, ALICE).cents());
        }
    }

    @Test
    void theExactColumnIsTrustedOnlyWhenItMatchesTheMoneyColumn() {
        assertEquals(48_000L, EconomyStore.resolveCents(480D, 48_000L));
        assertEquals(48_000L, EconomyStore.resolveCents(480D, null), "no exact value yet");
        assertEquals(2_500L, EconomyStore.resolveCents(25D, 48_000L), "money was changed alone");
        long huge = 1_234_567_890_123_456_789L;
        assertEquals(huge, EconomyStore.resolveCents(Money.toUnits(huge), huge));
        assertEquals(0L, EconomyStore.resolveCents(0D, -5L), "a negative exact value is never trusted");
    }

    @Test
    void compareAndSetFailsWhenTheRowMovedOn() throws Exception {
        try (Connection connection = memory()) {
            insertPlayer(connection, ALICE, 10D, 7L);
            assertFalse(EconomyStore.compareAndSet(connection, ALICE, 6L, 1L));
            assertTrue(EconomyStore.compareAndSet(connection, ALICE, 7L, 1234L));
            EconomyStore.StoredBalance stored = EconomyStore.readBalance(connection, ALICE);
            assertEquals(1234L, stored.cents());
            assertEquals(8L, stored.version());
            assertNull(EconomyStore.readBalance(connection, BOB));
        }
    }

    @Test
    void aFailedTransactionLeavesNothingBehind() throws Exception {
        try (Connection connection = memory()) {
            insertPlayer(connection, ALICE, 10D, 0L);
            assertThrows(SQLException.class, () -> EconomyStore.inTransaction(connection, c -> {
                EconomyStore.compareAndSet(c, ALICE, 0L, 99_999L);
                EconomyStore.insertLedger(c, entry("tx-1", ALICE, 98_999L));
                throw new SQLException("simulated failure after the balance write");
            }));
            assertEquals(1000L, EconomyStore.readBalance(connection, ALICE).cents());
            assertEquals(0, EconomyStore.countHistory(connection, ALICE));
            assertTrue(connection.getAutoCommit());
        }
    }

    @Test
    void anInnerTransactionJoinsTheOuterOne() throws Exception {
        try (Connection connection = memory()) {
            insertPlayer(connection, ALICE, 10D, 0L);
            assertThrows(SQLException.class, () -> EconomyStore.inTransaction(connection, outer -> {
                EconomyStore.inTransaction(outer, inner -> EconomyStore.compareAndSet(inner, ALICE, 0L, 1L));
                throw new SQLException("outer fails after the inner part");
            }));
            // The inner part did not commit on its own.
            assertEquals(1000L, EconomyStore.readBalance(connection, ALICE).cents());
        }
    }

    @Test
    void ledgerHistoryTotalsAndPruning() throws Exception {
        try (Connection connection = memory()) {
            insertPlayer(connection, ALICE, 10D, 0L);
            insertPlayer(connection, BOB, 5D, 0L);
            EconomyStore.insertLedger(connection, new LedgerEntry("a", "g1", 1_000L, ALICE, BOB, "PLAYER_PAY", -250L,
                    750L, 1L, null, "tax 0.10"));
            EconomyStore.insertLedger(connection, new LedgerEntry("b", "g1", 1_000L, BOB, ALICE, "PLAYER_PAY", 240L,
                    740L, 1L, null, null));
            EconomyStore.insertLedger(connection, new LedgerEntry("c", "c", 5_000L, ALICE, null, "ADMIN_ADD", 100L,
                    850L, 2L, "Console", "x".repeat(400)));

            List<LedgerEntry> history = EconomyStore.history(connection, ALICE, 10, 0);
            assertEquals(2, history.size());
            assertEquals("c", history.get(0).txId(), "newest first");
            assertEquals(LedgerEntry.MAX_NOTE, history.get(0).note().length(), "long notes are cut");
            assertEquals(BOB, history.get(1).counterparty());
            assertEquals(2, EconomyStore.countHistory(connection, ALICE));

            List<EconomyStore.ReasonTotal> totals = EconomyStore.totalsSince(connection, 0L);
            assertEquals(2, totals.size());
            EconomyStore.ReasonTotal pay = totals.stream().filter(t -> t.reason().equals("PLAYER_PAY")).findFirst().orElseThrow();
            assertEquals(2L, pay.count());
            assertEquals(240L, pay.creditedCents());
            assertEquals(250L, pay.debitedCents());

            EconomyStore.MoneySupply supply = EconomyStore.moneySupply(connection);
            assertEquals(2L, supply.accounts());
            assertEquals(15D, supply.total(), 1e-9);

            assertEquals(2, EconomyStore.pruneLedger(connection, 2_000L));
            assertEquals(1, EconomyStore.countHistory(connection, ALICE));
        }
    }

    @Test
    void ledgerIdsAreUnique() throws Exception {
        try (Connection connection = memory()) {
            EconomyStore.insertLedger(connection, entry("same", ALICE, 1L));
            assertThrows(SQLException.class, () -> EconomyStore.insertLedger(connection, entry("same", ALICE, 1L)));
        }
    }

    @Test
    void holdsRoundTrip() throws Exception {
        try (Connection connection = memory()) {
            EconomyStore.insertHold(connection, new EconomyStore.Hold("h1", ALICE, "Alice", 500L, "dice", 10L));
            EconomyStore.insertHold(connection, new EconomyStore.Hold("h2", BOB, "Bob", 700L, "roulette", 20L));
            assertTrue(EconomyStore.updateHold(connection, "h1", 950L));
            assertFalse(EconomyStore.updateHold(connection, "missing", 1L));
            List<EconomyStore.Hold> holds = EconomyStore.loadHolds(connection);
            assertEquals(2, holds.size());
            assertEquals(950L, holds.get(0).cents());
            assertTrue(EconomyStore.deleteHold(connection, "h1"));
            assertFalse(EconomyStore.deleteHold(connection, "h1"), "a hold can only be closed once");
            assertEquals(1, EconomyStore.loadHolds(connection).size());
        }
    }

    /**
     * Many writers with their own connections (like several servers sharing one database) add to one balance with
     * read / compare-and-set / retry. Not a single change may be lost or applied twice.
     */
    @Test
    void concurrentCompareAndSetLosesNoUpdates(@TempDir Path folder) throws Exception {
        String url = "jdbc:sqlite:" + folder.resolve("economy.db");
        try (Connection setup = DriverManager.getConnection(url)) {
            try (Statement statement = setup.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
            }
            createSchema(setup);
            insertPlayer(setup, ALICE, 0D, 0L);
        }

        int writers = 6;
        int changesEach = 50;
        ExecutorService pool = Executors.newFixedThreadPool(writers);
        List<Future<Integer>> results = new ArrayList<>();
        for (int w = 0; w < writers; w++) {
            int writer = w;
            results.add(pool.submit(() -> {
                int retries = 0;
                try (Connection connection = DriverManager.getConnection(url)) {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("PRAGMA busy_timeout=10000");
                    }
                    for (int i = 0; i < changesEach; i++) {
                        String tx = "w" + writer + "-" + i;
                        while (true) {
                            boolean applied;
                            try {
                                applied = EconomyStore.inTransaction(connection, c -> {
                                    EconomyStore.StoredBalance stored = EconomyStore.readBalance(c, ALICE);
                                    if (!EconomyStore.compareAndSet(c, ALICE, stored.version(), stored.cents() + 100L)) {
                                        return false;
                                    }
                                    EconomyStore.insertLedger(c, entry(tx, ALICE, 100L));
                                    return true;
                                });
                            } catch (SQLException busy) {
                                if (!EconomyStore.isRetryable(busy)) {
                                    throw busy;
                                }
                                applied = false;
                            }
                            if (applied) {
                                break;
                            }
                            retries++;
                        }
                    }
                }
                return retries;
            }));
        }
        for (Future<Integer> result : results) {
            result.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        try (Connection check = DriverManager.getConnection(url)) {
            EconomyStore.StoredBalance stored = EconomyStore.readBalance(check, ALICE);
            assertEquals(writers * changesEach * 100L, stored.cents(), "every change applied exactly once");
            assertEquals(writers * changesEach, stored.version());
            assertEquals(writers * changesEach, EconomyStore.countHistory(check, ALICE));
        }
    }

    private static Connection memory() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        createSchema(connection);
        return connection;
    }

    private static void createSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS players (uuid TEXT PRIMARY KEY, username TEXT,"
                    + " money REAL DEFAULT 0, money_cents BIGINT, money_spent REAL DEFAULT 0, money_made REAL DEFAULT 0,"
                    + " money_version BIGINT DEFAULT 0)");
        }
        EconomyStore.createSchema(connection, Statement::execute);
    }

    private static void insertPlayer(Connection connection, UUID uuid, double money, long version) throws SQLException {
        try (var statement = connection.prepareStatement(
                "INSERT INTO players (uuid, username, money, money_version) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, uuid.toString().substring(0, 8));
            statement.setDouble(3, money);
            statement.setLong(4, version);
            statement.executeUpdate();
        }
    }

    private static EconomyStore.BalanceSnapshot snapshot(UUID uuid, long cents, long version) {
        return new EconomyStore.BalanceSnapshot(uuid, cents, 0D, 0D, version);
    }

    private static LedgerEntry entry(String tx, UUID player, long cents) {
        return new LedgerEntry(tx, tx, System.currentTimeMillis(), player, null, "TEST", cents, 0L, 0L, null, null);
    }
}
