package com.bx.smpPlugin.managers;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.economy.EconomyContext;
import com.bx.smpPlugin.economy.EconomyStore;
import com.bx.smpPlugin.economy.LedgerEntry;
import com.bx.smpPlugin.economy.Money;
import com.bx.smpPlugin.listeners.JoinPreloadListener;
import com.bx.smpPlugin.models.EconomyFailureReason;
import com.bx.smpPlugin.models.EconomyReason;
import com.bx.smpPlugin.models.EconomyTransactionResult;
import com.bx.smpPlugin.models.EconomyTransferResult;
import com.bx.smpPlugin.models.PlayerData;
import com.bx.smpPlugin.testsupport.EconomyHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyManagerTest {

    private static final UUID ALICE = UUID.fromString("10000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("10000000-0000-0000-0000-00000000000b");
    private static final UUID CAROL = UUID.fromString("10000000-0000-0000-0000-00000000000c");

    private EconomyHarness harness;
    private EconomyManager economy;

    @BeforeEach
    void setUp() throws Exception {
        harness = new EconomyHarness();
        economy = harness.economy;
    }

    @AfterEach
    void tearDown() throws Exception {
        harness.close();
    }

    @Test
    void anOnlineDepositIsSavedWithItsLedgerRowWithoutWaitingForAutosave() throws Exception {
        PlayerData alice = harness.addOnline(ALICE, "Alice", 100D);

        EconomyTransactionResult result = economy.deposit(account(ALICE, "Alice"), 25.10D, EconomyReason.REWARD);
        assertTrue(result.success());
        assertEquals(125.10D, alice.getMoney(), 1e-9, "memory changes at once");

        harness.drainWrites();
        assertEquals(125.10D, harness.storedMoney(ALICE), 1e-9, "stored without a player save");
        assertEquals(alice.getMoneyVersion(), harness.storedVersion(ALICE));
        assertEquals(1, harness.ledgerRows(ALICE));
        assertEquals(2510L, harness.ledgerSum(ALICE));
    }

    @Test
    void aWithdrawalThatCannotBeAffordedChangesNothing() throws Exception {
        PlayerData alice = harness.addOnline(ALICE, "Alice", 10D);

        EconomyTransactionResult result = economy.withdraw(account(ALICE, "Alice"), 10.01D, EconomyReason.SHOP_PURCHASE);
        assertFalse(result.success());
        assertEquals(EconomyFailureReason.INSUFFICIENT_FUNDS, result.failureReason());
        assertEquals(10D, alice.getMoney(), 1e-9);
        harness.drainWrites();
        assertEquals(0, harness.ledgerRows(ALICE));
    }

    @Test
    void amountsAreExactCents() throws Exception {
        PlayerData alice = harness.addOnline(ALICE, "Alice", 0D);
        for (int i = 0; i < 1000; i++) {
            assertTrue(economy.deposit(account(ALICE, "Alice"), 0.1D, EconomyReason.REWARD).success());
        }
        assertEquals(100D, alice.getMoney(), 0D, "1000 x 0.10 is exactly 100.00");
        assertTrue(economy.withdraw(account(ALICE, "Alice"), 99.99D, EconomyReason.SHOP_PURCHASE).success());
        assertEquals(1L, economy.getBalanceCents(ALICE));
        assertFalse(economy.deposit(account(ALICE, "Alice"), Double.NaN, EconomyReason.REWARD).success());
        assertFalse(economy.deposit(account(ALICE, "Alice"), 0.004D, EconomyReason.REWARD).success(),
                "rounds to zero cents");
    }

    @Test
    void anOnlineTransferMovesBothBalancesAndRecordsTheTax() throws Exception {
        PlayerData alice = harness.addOnline(ALICE, "Alice", 100D);
        PlayerData bob = harness.addOnline(BOB, "Bob", 5D);

        EconomyTransferResult result = economy.transfer(ALICE, "Alice", BOB, "Bob", 40D, 200L,
                EconomyReason.PLAYER_PAY, EconomyContext.NONE);
        assertTrue(result.success());
        assertEquals(60D, alice.getMoney(), 1e-9);
        assertEquals(43D, bob.getMoney(), 1e-9, "40 minus 2.00 tax");

        harness.drainWrites();
        assertEquals(60D, harness.storedMoney(ALICE), 1e-9);
        assertEquals(43D, harness.storedMoney(BOB), 1e-9);
        List<LedgerEntry> aliceRows = EconomyStore.history(harness.connection(), ALICE, 10, 0);
        List<LedgerEntry> bobRows = EconomyStore.history(harness.connection(), BOB, 10, 0);
        assertEquals(-4000L, aliceRows.get(0).amountCents());
        assertEquals(3800L, bobRows.get(0).amountCents());
        assertEquals(aliceRows.get(0).groupId(), bobRows.get(0).groupId(), "one transaction");
        assertEquals(BOB, aliceRows.get(0).counterparty());
        assertTrue(aliceRows.get(0).note().contains("tax"));
    }

    @Test
    void aTransferThatFailsLeavesBothSidesUnchanged() throws Exception {
        PlayerData alice = harness.addOnline(ALICE, "Alice", 30D);
        PlayerData bob = harness.addOnline(BOB, "Bob", 5D);

        EconomyTransferResult result = economy.transfer(ALICE, "Alice", BOB, "Bob", 30.01D, EconomyReason.PLAYER_PAY);
        assertFalse(result.success());
        assertEquals(EconomyFailureReason.INSUFFICIENT_FUNDS, result.failureReason());
        assertEquals(30D, alice.getMoney(), 1e-9);
        assertEquals(5D, bob.getMoney(), 1e-9);

        assertEquals(EconomyFailureReason.SAME_ACCOUNT,
                economy.transfer(ALICE, "Alice", ALICE, "Alice", 1D, EconomyReason.PLAYER_PAY).failureReason());
    }

    @Test
    void offlineChangesAreAtomicDatabaseTransactions() throws Exception {
        harness.addOffline(CAROL, "Carol", 50D);
        long before = harness.storedVersion(CAROL);

        assertTrue(economy.deposit(account(CAROL, "Carol"), 12.34D, EconomyReason.AUCTION_CLAIM).success());
        assertEquals(62.34D, harness.storedMoney(CAROL), 1e-9, "written before the call returns");
        assertEquals(before + 1L, harness.storedVersion(CAROL));
        assertEquals(before + 1L, economy.offlineVersion(CAROL), "a login that read the row earlier re-reads it");
        assertEquals(1, harness.ledgerRows(CAROL));

        // Two changes in a row both count (no lost update through a stale copy).
        assertTrue(economy.withdraw(account(CAROL, "Carol"), 2.34D, EconomyReason.VAULT_WITHDRAW).success());
        assertTrue(economy.deposit(account(CAROL, "Carol"), 1D, EconomyReason.VAULT_DEPOSIT).success());
        assertEquals(61D, harness.storedMoney(CAROL), 1e-9);
        assertEquals(EconomyFailureReason.INSUFFICIENT_FUNDS,
                economy.withdraw(account(CAROL, "Carol"), 61.01D, EconomyReason.VAULT_WITHDRAW).failureReason());
        assertEquals(EconomyFailureReason.NO_PLAYER_DATA,
                economy.deposit(account(UUID.randomUUID(), "Nobody"), 1D, EconomyReason.VAULT_DEPOSIT).failureReason());
    }

    @Test
    void anOnlineToOfflineTransferIsOneTransaction() throws Exception {
        PlayerData alice = harness.addOnline(ALICE, "Alice", 100D);
        harness.addOffline(CAROL, "Carol", 1D);

        assertTrue(economy.transfer(ALICE, "Alice", CAROL, "Carol", 25D, EconomyReason.PLAYER_PAY).success());
        assertEquals(75D, alice.getMoney(), 1e-9);
        // Both sides are already stored: the in-memory side is written inside the same transaction.
        assertEquals(75D, harness.storedMoney(ALICE), 1e-9);
        assertEquals(26D, harness.storedMoney(CAROL), 1e-9);
        assertEquals(-2500L, harness.ledgerSum(ALICE));
        assertEquals(2500L, harness.ledgerSum(CAROL));
    }

    @Test
    void theBalanceLimitStopsDepositsButNotWithdrawals() throws Exception {
        harness.config.set("ECONOMY.MAX-BALANCE", 1000D);
        PlayerData alice = harness.addOnline(ALICE, "Alice", 999.99D);

        EconomyTransactionResult tooMuch = economy.deposit(account(ALICE, "Alice"), 0.02D, EconomyReason.REWARD);
        assertEquals(EconomyFailureReason.BALANCE_LIMIT, tooMuch.failureReason());
        assertTrue(economy.deposit(account(ALICE, "Alice"), 0.01D, EconomyReason.REWARD).success());

        // A balance that is already above a lowered limit is kept and can still be spent.
        harness.config.set("ECONOMY.MAX-BALANCE", 500D);
        assertEquals(EconomyFailureReason.BALANCE_LIMIT,
                economy.deposit(account(ALICE, "Alice"), 1D, EconomyReason.REWARD).failureReason());
        assertTrue(economy.withdraw(account(ALICE, "Alice"), 100D, EconomyReason.SHOP_PURCHASE).success());
        assertEquals(900D, alice.getMoney(), 1e-9);
    }

    @Test
    void thereIsNoBalanceLimitUnlessOneIsSet() throws Exception {
        PlayerData alice = harness.addOnline(ALICE, "Alice", 0D);
        assertEquals(Money.MAX_CENTS, economy.maxBalanceCents());

        // Far past the 10 trillion 4.0 stopped at, and past the 35 trillion a double carries to the cent.
        assertTrue(economy.deposit(account(ALICE, "Alice"), 50_000_000_000_000D, EconomyReason.REWARD).success());
        assertTrue(economy.deposit(account(ALICE, "Alice"), 0.01D, EconomyReason.REWARD).success());
        assertEquals(5_000_000_000_000_001L, alice.getMoneyCents());

        harness.drainWrites();
        assertEquals(5_000_000_000_000_001L, harness.storedCents(ALICE), "stored to the cent");
        assertEquals(5_000_000_000_000_001L, harness.database.loadPlayer(ALICE).getMoneyCents(), "and read back");
        assertEquals(5_000_000_000_000_001L, harness.ledgerSum(ALICE));
    }

    @Test
    void balancesUpToTheLargestThereIsStayExactOffline() throws Exception {
        harness.addOffline(BOB, "Bob", 0D);

        // 90 quadrillion and one cent, each change a database transaction on the stored row.
        assertTrue(economy.deposit(account(BOB, "Bob"), 90_000_000_000_000_000D, EconomyReason.REWARD).success());
        assertTrue(economy.deposit(account(BOB, "Bob"), 0.01D, EconomyReason.REWARD).success());
        assertEquals(9_000_000_000_000_000_001L, economy.getBalanceCents(BOB));
        assertEquals(9_000_000_000_000_000_001L, harness.storedCents(BOB));

        // Past the largest balance there is (about 92 quadrillion) a deposit is refused, never wrapped around.
        EconomyTransactionResult tooMuch = economy.deposit(account(BOB, "Bob"), 3_000_000_000_000_000D,
                EconomyReason.REWARD);
        assertEquals(EconomyFailureReason.BALANCE_LIMIT, tooMuch.failureReason());
        assertEquals(9_000_000_000_000_000_001L, economy.getBalanceCents(BOB));

        // Read as a double (Vault, placeholders) the balance is never more than Bob has, so taking all of it works.
        double shown = economy.getBalance(BOB);
        assertTrue(economy.has(BOB, shown));
        assertTrue(economy.withdraw(account(BOB, "Bob"), shown, EconomyReason.SHOP_PURCHASE).success());
        assertEquals(1L, economy.getBalanceCents(BOB), "the cent the double could not show is still there");
    }

    @Test
    void noMoneyMovesWithoutADatabase() throws Exception {
        PlayerData alice = harness.addOnline(ALICE, "Alice", 100D);
        EconomyHarness.set(DatabaseManager.class, harness.database, "ready", false);

        EconomyTransactionResult result = economy.deposit(account(ALICE, "Alice"), 5D, EconomyReason.REWARD);
        assertEquals(EconomyFailureReason.STORAGE_UNAVAILABLE, result.failureReason());
        assertEquals(100D, alice.getMoney(), 1e-9);
    }

    @Test
    void anOlderSaveArrivingLateDoesNotUndoAPayment() throws Exception {
        PlayerData alice = harness.addOnline(ALICE, "Alice", 100D);
        PlayerData.MoneyState old = alice.moneyState();

        assertTrue(economy.deposit(account(ALICE, "Alice"), 50D, EconomyReason.REWARD).success());
        harness.drainWrites();
        // A writer that captured the balance before the deposit (e.g. CoinFlip's own connection) lands now.
        assertFalse(EconomyStore.writeSnapshot(harness.connection(),
                new EconomyStore.BalanceSnapshot(ALICE, 10_000L, 0D, 0D, old.version())));
        assertEquals(150D, harness.storedMoney(ALICE), 1e-9);

        // A full player save with an older copy keeps the newer stored balance too.
        PlayerData staleCopy = new PlayerData(ALICE, "Alice");
        staleCopy.setMoney(100D);
        staleCopy.setMoneyVersion(old.version());
        staleCopy.setKills(7);
        harness.database.savePlayer(staleCopy);
        assertEquals(150D, harness.storedMoney(ALICE), 1e-9);
    }

    @Test
    void aNewerStoredBalanceFromElsewhereIsDetectedAndTheLiveBalanceKept() throws Exception {
        PlayerData alice = harness.addOnline(ALICE, "Alice", 100D);
        // Another server sharing the database moved the stored version far ahead.
        try (PreparedStatement statement = harness.connection().prepareStatement(
                "UPDATE players SET money = 1, money_version = 500 WHERE uuid = ?")) {
            statement.setString(1, ALICE.toString());
            statement.executeUpdate();
        }

        assertTrue(economy.deposit(account(ALICE, "Alice"), 1D, EconomyReason.REWARD).success());
        harness.drainWrites();
        assertEquals(1L, economy.versionConflicts());
        assertEquals(101D, harness.storedMoney(ALICE), 1e-9, "the balance the player sees is stored");
        assertTrue(alice.getMoneyVersion() > 500L);
        assertTrue(economy.deposit(account(ALICE, "Alice"), 1D, EconomyReason.REWARD).success());
        harness.drainWrites();
        assertEquals(102D, harness.storedMoney(ALICE), 1e-9, "later changes are saved normally again");
    }

    @Test
    void aPlayerWhoJustQuitIsChangedThroughTheirQueuedCopy() throws Exception {
        PlayerData alice = harness.addOnline(ALICE, "Alice", 100D);
        harness.online.remove(ALICE);
        moveToSavingCopy(alice);

        assertTrue(economy.deposit(account(ALICE, "Alice"), 10D, EconomyReason.AUCTION_CLAIM).success());
        assertEquals(110D, alice.getMoney(), 1e-9, "the copy the quit save and a rejoin use");
        harness.drainWrites();
        assertEquals(110D, harness.storedMoney(ALICE), 1e-9);
    }

    @Test
    void aConfigurableTaxNeverTakesTheWholePayment() {
        harness.config.set("ECONOMY.PAY.TAX-PERCENT", 50D);
        EconomyManager.PayRules rules = economy.payRules();
        assertEquals(1L, rules.taxFor(2L));
        assertEquals(0L, rules.taxFor(1L), "the recipient always gets at least a cent");
        harness.config.set("ECONOMY.PAY.TAX-PERCENT", 90D);
        assertEquals(50D, economy.payRules().taxPercent(), 1e-9, "capped at 50%");
    }

    @Test
    void thePaymentCooldownSurvivesARelog() {
        harness.config.set("ECONOMY.PAY.COOLDOWN-SECONDS", 30);
        assertEquals(0L, economy.payCooldownRemaining(ALICE));
        economy.markPayment(ALICE);
        assertTrue(economy.payCooldownRemaining(ALICE) > 29_000L);
    }

    @Test
    void aLoginThatReadTheRowBeforeAnOfflineChangeReadsItAgain() throws Exception {
        harness.addOffline(CAROL, "Carol", 50D);
        // The login thread reads the row ...
        PlayerData readAtLogin = harness.database.loadPlayer(CAROL);
        // ... then an auction sale pays Carol before her join completes.
        assertTrue(economy.deposit(account(CAROL, "Carol"), 5D, EconomyReason.AUCTION_CLAIM).success());

        JoinPreloadListener preloader = new JoinPreloadListener(harness.plugin);
        EconomyHarness.set(SmpPlugin.class, harness.plugin, "joinPreloadListener", preloader);
        injectPreloaded(preloader, CAROL, readAtLogin);

        PlayerData joined = harness.players.loadOrCreate(EconomyHarness.player(CAROL, "Carol"));
        assertEquals(55D, joined.getMoney(), 1e-9, "the stale preloaded copy is replaced");
        assertEquals(0L, economy.offlineVersion(CAROL));
        assertTrue(economy.deposit(account(CAROL, "Carol"), 1D, EconomyReason.REWARD).success());
        harness.drainWrites();
        assertEquals(56D, harness.storedMoney(CAROL), 1e-9);
    }

    @SuppressWarnings("unchecked")
    private static void injectPreloaded(JoinPreloadListener preloader, UUID uuid, PlayerData data) throws Exception {
        Class<?> recordType = Class.forName(JoinPreloadListener.class.getName() + "$Preloaded");
        var constructor = recordType.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object entry = constructor.newInstance(System.currentTimeMillis(), data, List.of());
        Field map = JoinPreloadListener.class.getDeclaredField("preloaded");
        map.setAccessible(true);
        ((Map<UUID, Object>) map.get(preloader)).put(uuid, entry);
    }

    private EconomyManager.AccountReference account(UUID uuid, String name) {
        return new EconomyManager.AccountReference(uuid, name, harness.online.get(uuid));
    }

    @SuppressWarnings("unchecked")
    private void moveToSavingCopy(PlayerData data) throws Exception {
        Field cache = PlayerDataManager.class.getDeclaredField("cache");
        cache.setAccessible(true);
        ((Map<UUID, PlayerData>) cache.get(harness.players)).remove(data.getUuid());
        Field saving = PlayerDataManager.class.getDeclaredField("savingAfterQuit");
        saving.setAccessible(true);
        ((Map<UUID, PlayerData>) saving.get(harness.players)).put(data.getUuid(), data);
    }
}
