package com.bx.smpPlugin.economy;

import com.bx.smpPlugin.storage.SchemaMigrations;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EconomyLedgerTest {

    private static final String INSERT = "INSERT INTO economy_ledger"
            + " (tx_uuid, created_at, currency, kind, reason, player_uuid, player_name, counterparty_uuid,"
            + " counterparty_name, actor_uuid, actor_name, amount_minor, balance_before_minor,"
            + " balance_after_minor, note) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    @Test
    void rowsComeBackNewestFirstWithEveryField() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE players (uuid TEXT PRIMARY KEY)");
            }
            SchemaMigrations.migrate(connection, null);
            String payment = EconomyLedger.newTransactionId();
            insert(connection, new EconomyLedger.Entry(payment, 1_000L, EconomyLedger.Currency.MONEY,
                    EconomyLedger.Kind.TRANSFER_OUT, "PLAYER_PAY", alice, "Alice", bob, "Bob", null, null,
                    2_500L, 10_000L, 7_500L, null));
            insert(connection, new EconomyLedger.Entry(payment, 1_000L, EconomyLedger.Currency.MONEY,
                    EconomyLedger.Kind.TRANSFER_IN, "PLAYER_PAY", bob, "Bob", alice, "Alice", null, null,
                    2_500L, 0L, 2_500L, null));
            insert(connection, new EconomyLedger.Entry(EconomyLedger.newTransactionId(), 2_000L,
                    EconomyLedger.Currency.SHARDS, EconomyLedger.Kind.DEPOSIT, "ADMIN_ADD", alice, "Alice", null, null,
                    admin, "Admin", 5L, 0L, 5L, "event prize"));

            List<EconomyLedger.Entry> history = EconomyLedger.readHistory(connection, alice, 10, 0);
            assertEquals(2, history.size());
            EconomyLedger.Entry newest = history.get(0);
            assertEquals(EconomyLedger.Currency.SHARDS, newest.currency());
            assertEquals("Admin", newest.actorName());
            assertEquals(admin, newest.actor());
            assertEquals("event prize", newest.note());
            EconomyLedger.Entry paid = history.get(1);
            assertEquals(payment, paid.transactionId());
            assertEquals(EconomyLedger.Kind.TRANSFER_OUT, paid.kind());
            assertEquals(bob, paid.counterparty());
            assertEquals(10_000L, paid.beforeMinor());
            assertEquals(7_500L, paid.afterMinor());
            assertNull(paid.actor());

            // Both rows of the payment share one transaction id.
            assertEquals(payment, EconomyLedger.readHistory(connection, bob, 10, 0).get(0).transactionId());
            assertEquals(1, EconomyLedger.readHistory(connection, alice, 1, 1).size(), "paging");
        }
    }

    private static void insert(Connection connection, EconomyLedger.Entry entry) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
            EconomyLedger.write(statement, entry);
            statement.executeUpdate();
        }
    }
}
