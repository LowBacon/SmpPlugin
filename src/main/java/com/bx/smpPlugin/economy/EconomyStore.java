// SmpPlugin by LowBacon
package com.bx.smpPlugin.economy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class EconomyStore {

    public static final String LEDGER_TABLE = "economy_ledger";
    public static final String HOLDS_TABLE = "economy_holds";

    public record BalanceSnapshot(UUID player, long cents, double moneySpent, double moneyMade, long version) {
    }

    public record StoredBalance(long cents, long version) {
    }

    public record Hold(String id, UUID player, String name, long cents, String game, long createdAt) {
    }

    public record ReasonTotal(String reason, long count, long creditedCents, long debitedCents) {
    }

    public record MoneySupply(long accounts, double total) {
    }

    @FunctionalInterface
    public interface Work<T> {
        T run(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface SchemaExecutor {
        void execute(Statement statement, String sql) throws SQLException;
    }

    private EconomyStore() {
    }

    public static void createSchema(Connection connection, SchemaExecutor executor) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            executor.execute(statement, "CREATE TABLE IF NOT EXISTS " + LEDGER_TABLE + " ("
                    + " entry_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + " tx_id VARCHAR(40) NOT NULL UNIQUE,"
                    + " group_id VARCHAR(40),"
                    + " created_at BIGINT NOT NULL,"
                    + " player_uuid VARCHAR(36) NOT NULL,"
                    + " counterparty_uuid VARCHAR(36),"
                    + " reason VARCHAR(40) NOT NULL,"
                    + " amount_cents BIGINT NOT NULL,"
                    + " balance_after_cents BIGINT NOT NULL,"
                    + " balance_version BIGINT NOT NULL,"
                    + " actor VARCHAR(64),"
                    + " note VARCHAR(255))");
            executor.execute(statement, "CREATE INDEX IF NOT EXISTS idx_economy_ledger_player ON " + LEDGER_TABLE
                    + " (player_uuid, entry_id)");
            executor.execute(statement, "CREATE INDEX IF NOT EXISTS idx_economy_ledger_time ON " + LEDGER_TABLE
                    + " (created_at)");
            executor.execute(statement, "CREATE TABLE IF NOT EXISTS " + HOLDS_TABLE + " ("
                    + " hold_id VARCHAR(64) PRIMARY KEY,"
                    + " player_uuid VARCHAR(36) NOT NULL,"
                    + " player_name VARCHAR(64),"
                    + " cents BIGINT NOT NULL,"
                    + " game VARCHAR(64),"
                    + " created_at BIGINT NOT NULL)");
        }
    }

    public static <T> T inTransaction(Connection connection, Work<T> work) throws SQLException {
        if (!connection.getAutoCommit()) {
            return work.run(connection);
        }
        connection.setAutoCommit(false);
        try {
            T result = work.run(connection);
            connection.commit();
            return result;
        } catch (SQLException | RuntimeException failure) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public static boolean isRetryable(SQLException exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof SQLException sql) {
                int code = sql.getErrorCode();
                String state = sql.getSQLState();
                String message = sql.getMessage() == null ? "" : sql.getMessage();
                if (code == 5 || code == 6 || code == 1205 || code == 1213 || "40001".equals(state)
                        || message.contains("SQLITE_BUSY") || message.contains("SQLITE_LOCKED")) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean writeSnapshot(Connection connection, BalanceSnapshot snapshot) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE players SET money = ?, money_cents = ?, money_spent = ?, money_made = ?, money_version = ?"
                        + " WHERE uuid = ? AND money_version < ?")) {
            statement.setDouble(1, Money.toUnits(snapshot.cents()));
            statement.setLong(2, snapshot.cents());
            statement.setDouble(3, Math.max(0D, snapshot.moneySpent()));
            statement.setDouble(4, Math.max(0D, snapshot.moneyMade()));
            statement.setLong(5, snapshot.version());
            statement.setString(6, snapshot.player().toString());
            statement.setLong(7, snapshot.version());
            return statement.executeUpdate() > 0;
        }
    }

    public static StoredBalance readBalance(Connection connection, UUID player) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT money, money_cents, money_version FROM players WHERE uuid = ?")) {
            statement.setString(1, player.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                long cents = rs.getLong(2);
                return new StoredBalance(resolveCents(rs.getDouble(1), rs.wasNull() ? null : cents), rs.getLong(3));
            }
        }
    }

    public static long resolveCents(double money, Long moneyCents) {
        if (moneyCents != null && moneyCents >= 0L && Double.compare(Money.toUnits(moneyCents), money) == 0) {
            return moneyCents;
        }
        return Money.storedToCents(money);
    }

    public static boolean compareAndSet(Connection connection, UUID player, long expectedVersion, long newCents)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE players SET money = ?, money_cents = ?, money_version = ? WHERE uuid = ? AND money_version = ?")) {
            statement.setDouble(1, Money.toUnits(newCents));
            statement.setLong(2, newCents);
            statement.setLong(3, expectedVersion + 1L);
            statement.setString(4, player.toString());
            statement.setLong(5, expectedVersion);
            return statement.executeUpdate() > 0;
        }
    }

    public static void insertLedger(Connection connection, LedgerEntry entry) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + LEDGER_TABLE + " (tx_id, group_id, created_at, player_uuid, counterparty_uuid, reason,"
                        + " amount_cents, balance_after_cents, balance_version, actor, note)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, entry.txId());
            statement.setString(2, entry.groupId());
            statement.setLong(3, entry.createdAt());
            statement.setString(4, entry.player().toString());
            statement.setString(5, entry.counterparty() == null ? null : entry.counterparty().toString());
            statement.setString(6, entry.reason());
            statement.setLong(7, entry.amountCents());
            statement.setLong(8, entry.balanceAfterCents());
            statement.setLong(9, entry.balanceVersion());
            statement.setString(10, entry.actor());
            statement.setString(11, entry.note());
            statement.executeUpdate();
        }
    }

    public static List<LedgerEntry> history(Connection connection, UUID player, int limit, int offset)
            throws SQLException {
        List<LedgerEntry> entries = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT tx_id, group_id, created_at, player_uuid, counterparty_uuid, reason, amount_cents,"
                        + " balance_after_cents, balance_version, actor, note FROM " + LEDGER_TABLE
                        + " WHERE player_uuid = ? ORDER BY entry_id DESC LIMIT ? OFFSET ?")) {
            statement.setString(1, player.toString());
            statement.setInt(2, Math.max(1, limit));
            statement.setInt(3, Math.max(0, offset));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String counterparty = rs.getString(5);
                    entries.add(new LedgerEntry(rs.getString(1), rs.getString(2), rs.getLong(3),
                            UUID.fromString(rs.getString(4)), counterparty == null ? null : UUID.fromString(counterparty),
                            rs.getString(6), rs.getLong(7), rs.getLong(8), rs.getLong(9), rs.getString(10),
                            rs.getString(11)));
                }
            }
        }
        return entries;
    }

    public static int countHistory(Connection connection, UUID player) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM " + LEDGER_TABLE + " WHERE player_uuid = ?")) {
            statement.setString(1, player.toString());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public static List<ReasonTotal> totalsSince(Connection connection, long sinceMillis) throws SQLException {
        List<ReasonTotal> totals = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT reason, COUNT(*),"
                        + " COALESCE(SUM(CASE WHEN amount_cents > 0 THEN amount_cents ELSE 0 END), 0),"
                        + " COALESCE(SUM(CASE WHEN amount_cents < 0 THEN -amount_cents ELSE 0 END), 0)"
                        + " FROM " + LEDGER_TABLE + " WHERE created_at >= ? GROUP BY reason ORDER BY reason")) {
            statement.setLong(1, sinceMillis);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    totals.add(new ReasonTotal(rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)));
                }
            }
        }
        return totals;
    }

    public static MoneySupply moneySupply(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*), COALESCE(SUM(money), 0) FROM players")) {
            return rs.next() ? new MoneySupply(rs.getLong(1), rs.getDouble(2)) : new MoneySupply(0L, 0D);
        }
    }

    public static int pruneLedger(Connection connection, long beforeMillis) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM " + LEDGER_TABLE + " WHERE created_at < ?")) {
            statement.setLong(1, beforeMillis);
            return statement.executeUpdate();
        }
    }

    public static void insertHold(Connection connection, Hold hold) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + HOLDS_TABLE + " (hold_id, player_uuid, player_name, cents, game, created_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, hold.id());
            statement.setString(2, hold.player().toString());
            statement.setString(3, hold.name());
            statement.setLong(4, hold.cents());
            statement.setString(5, hold.game());
            statement.setLong(6, hold.createdAt());
            statement.executeUpdate();
        }
    }

    public static boolean updateHold(Connection connection, String id, long cents) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE " + HOLDS_TABLE + " SET cents = ? WHERE hold_id = ?")) {
            statement.setLong(1, cents);
            statement.setString(2, id);
            return statement.executeUpdate() > 0;
        }
    }

    public static boolean deleteHold(Connection connection, String id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM " + HOLDS_TABLE + " WHERE hold_id = ?")) {
            statement.setString(1, id);
            return statement.executeUpdate() > 0;
        }
    }

    public static List<Hold> loadHolds(Connection connection) throws SQLException {
        List<Hold> holds = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT hold_id, player_uuid, player_name, cents, game, created_at FROM "
                     + HOLDS_TABLE + " ORDER BY created_at")) {
            while (rs.next()) {
                try {
                    holds.add(new Hold(rs.getString(1), UUID.fromString(rs.getString(2)), rs.getString(3),
                            rs.getLong(4), rs.getString(5), rs.getLong(6)));
                } catch (IllegalArgumentException unreadable) {
                    // A row whose player id is not a UUID is left in place for staff to look at.
                }
            }
        }
        return holds;
    }
}
