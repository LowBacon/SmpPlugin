// SmpPlugin by LowBacon
package com.bx.smpPlugin.managers;

import com.bx.smpPlugin.economy.Money;
import com.bx.smpPlugin.models.PlayerData;
import com.bx.smpPlugin.testsupport.EconomyHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseManagerPlayerSaveTest {

    private static final UUID DAVE = UUID.fromString("20000000-0000-0000-0000-00000000000d");

    private EconomyHarness harness;

    @BeforeEach
    void setUp() throws Exception {
        harness = new EconomyHarness();
    }

    @AfterEach
    void tearDown() throws Exception {
        harness.close();
    }

    @Test
    void savingInsertsThenUpdatesTheRow() throws Exception {
        PlayerData data = new PlayerData(DAVE, "Dave");
        data.setMoney(12.5D);
        data.setKills(3);
        harness.database.savePlayer(data);
        assertFalse(data.isDirty());

        data.setKills(4);
        data.setMoney(20D);
        harness.database.savePlayer(data);

        PlayerData loaded = harness.database.loadPlayer(DAVE);
        assertEquals(20D, loaded.getMoney(), 1e-9);
        assertEquals(4, loaded.getKills());
        assertEquals(data.getMoneyVersion(), loaded.getMoneyVersion(), "the balance version is stored and read back");
    }

    @Test
    void anOlderCopyUpdatesStatsButNotTheBalance() throws Exception {
        PlayerData live = new PlayerData(DAVE, "Dave");
        live.setMoney(100D);
        harness.database.savePlayer(live);

        PlayerData olderCopy = harness.database.loadPlayer(DAVE);
        live.setMoney(250D);
        harness.database.savePlayer(live);

        olderCopy.setKills(9);
        harness.database.savePlayer(olderCopy);

        PlayerData loaded = harness.database.loadPlayer(DAVE);
        assertEquals(250D, loaded.getMoney(), 1e-9, "the newer balance stays");
        assertEquals(25_000L, harness.storedCents(DAVE), "in the exact column too");
        assertEquals(9, loaded.getKills(), "the other columns are saved as before");
    }

    @Test
    void aBalanceFarAboveThirtyFiveTrillionIsSavedAndLoadedToTheCent() throws Exception {
        PlayerData data = new PlayerData(DAVE, "Dave");
        data.setMoneyCents(1_234_567_890_123_456_789L);
        harness.database.savePlayer(data);
        assertEquals(1_234_567_890_123_456_789L, harness.database.loadPlayer(DAVE).getMoneyCents());

        data.setMoneyCents(Money.MAX_CENTS);
        harness.database.savePlayer(data);
        assertEquals(Money.MAX_CENTS, harness.database.loadPlayer(DAVE).getMoneyCents());
    }

    @Test
    void aBalanceChangedOnlyInTheMoneyColumnIsLoadedFromThere() throws Exception {
        PlayerData data = new PlayerData(DAVE, "Dave");
        data.setMoneyCents(1_234_567_890_123_456_789L);
        harness.database.savePlayer(data);
        try (Statement statement = harness.connection().createStatement()) {
            // What a wipe, an older plugin version or a hand-written SQL change does.
            statement.executeUpdate("UPDATE players SET money = 250 WHERE uuid = '" + DAVE + "'");
        }
        assertEquals(25_000L, harness.database.loadPlayer(DAVE).getMoneyCents());

        try (Statement statement = harness.connection().createStatement()) {
            // A row no 4.1.0 or later save has written yet.
            statement.executeUpdate("UPDATE players SET money = 12.34, money_cents = NULL WHERE uuid = '" + DAVE + "'");
        }
        assertEquals(1_234L, harness.database.loadPlayer(DAVE).getMoneyCents());
    }

    @Test
    void columnsTheSaveDoesNotListAreKept() throws Exception {
        harness.database.savePlayer(new PlayerData(DAVE, "Dave"));
        try (Statement statement = harness.connection().createStatement()) {
            statement.execute("ALTER TABLE players ADD COLUMN added_later INTEGER DEFAULT 5");
            statement.execute("UPDATE players SET added_later = 9 WHERE uuid = '" + DAVE + "'");
        }
        harness.database.savePlayer(harness.database.loadPlayer(DAVE));
        try (PreparedStatement statement = harness.connection().prepareStatement(
                "SELECT added_later FROM players WHERE uuid = ?")) {
            statement.setString(1, DAVE.toString());
            try (ResultSet rs = statement.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(9, rs.getInt(1), "REPLACE INTO used to reset this to its default");
            }
        }
    }

    @Test
    void theMySqlStatementComparesBeforeMovingTheVersion() {
        String sql = DatabaseManager.playerUpsertSql(true);
        assertTrue(sql.startsWith("INSERT INTO players ("));
        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE"));
        int money = sql.indexOf("money = IF(VALUES(money_version) >= money_version");
        int cents = sql.indexOf("money_cents = IF(VALUES(money_version) >= money_version");
        int version = sql.indexOf("money_version = GREATEST(");
        assertTrue(money > 0 && cents > 0 && version > Math.max(money, cents), "MySQL applies assignments in order");
        assertTrue(sql.endsWith("money_version = GREATEST(money_version, VALUES(money_version))"));
    }
}
