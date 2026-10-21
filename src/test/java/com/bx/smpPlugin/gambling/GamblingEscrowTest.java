package com.bx.smpPlugin.gambling;

import com.bx.smpPlugin.economy.EconomyStore;
import com.bx.smpPlugin.models.EconomyReason;
import com.bx.smpPlugin.models.PlayerData;
import com.bx.smpPlugin.testsupport.EconomyHarness;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The escrow guarantees: a hold exists exactly while a bet is taken and unpaid, a crash refunds it once, and a
 * payout can never happen without its hold closing in the same transaction.
 */
class GamblingEscrowTest {

    private static final UUID ALICE = UUID.fromString("30000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("30000000-0000-0000-0000-00000000000b");

    private EconomyHarness harness;
    private GamblingEscrow escrow;
    private Wallet wallet;

    @BeforeEach
    void setUp() throws Exception {
        harness = new EconomyHarness();
        escrow = new GamblingEscrow(harness.plugin);
        wallet = new Wallet(harness.plugin, escrow);
    }

    @AfterEach
    void tearDown() throws Exception {
        harness.close();
    }

    @Test
    void takingABetOpensItsHoldInTheSameTransaction() throws Exception {
        PlayerData alice = harness.addOnline(ALICE, "Alice", 100D);

        assertTrue(wallet.take(harness.online.get(ALICE), hold("dice-1", ALICE, 2500L)));
        assertEquals(75D, alice.getMoney(), 1e-9);
        harness.drainWrites();
        assertEquals(75D, harness.storedMoney(ALICE), 1e-9);
        assertEquals(List.of("dice-1"), holdIds());
        assertEquals(1, escrow.size());
    }

    @Test
    void aBetThatCannotBeAffordedOpensNothing() throws Exception {
        harness.addOnline(ALICE, "Alice", 10D);
        assertFalse(wallet.take(harness.online.get(ALICE), hold("dice-2", ALICE, 1001L)));
        harness.drainWrites();
        assertTrue(holdIds().isEmpty());
        assertEquals(0, escrow.size());
    }

    @Test
    void aWinIsPaidAndItsHoldClosedTogether() throws Exception {
        PlayerData alice = harness.addOnline(ALICE, "Alice", 100D);
        assertTrue(wallet.take(harness.online.get(ALICE), hold("dice-3", ALICE, 1000L)));

        assertTrue(wallet.pay(ALICE, "Alice", 1900L, EconomyReason.GAMBLE_PAYOUT, "Dice win:", "dice-3"));
        assertEquals(109D, alice.getMoney(), 1e-9);
        harness.drainWrites();
        assertTrue(holdIds().isEmpty());
        assertEquals(109D, harness.storedMoney(ALICE), 1e-9);
        assertEquals(900L, harness.ledgerSum(ALICE), "-10.00 stake, +19.00 payout");
    }

    @Test
    void aLostBetClosesItsHoldAndPaysNothing() throws Exception {
        PlayerData alice = harness.addOnline(ALICE, "Alice", 100D);
        assertTrue(wallet.take(harness.online.get(ALICE), hold("dice-4", ALICE, 1000L)));
        escrow.closeWithoutPayment(List.of("dice-4"));
        harness.drainWrites();
        assertTrue(holdIds().isEmpty());
        assertEquals(90D, alice.getMoney(), 1e-9);
    }

    @Test
    void anOfflineWinnerIsPaidAndEveryHoldOfTheRoundClosedInOneTransaction() throws Exception {
        harness.addOnline(ALICE, "Alice", 100D);
        harness.addOnline(BOB, "Bob", 100D);
        assertTrue(wallet.take(harness.online.get(ALICE), hold("r-1-a", ALICE, 3000L)));
        assertTrue(wallet.take(harness.online.get(BOB), hold("r-1-b", BOB, 1000L)));
        harness.drainWrites();

        // Bob logs off before the draw and wins the 40.00 pot.
        harness.online.remove(BOB);
        removeFromCache(BOB);
        assertTrue(wallet.pay(BOB, "Bob", 4000L, EconomyReason.GAMBLE_PAYOUT, "Roulette win:", List.of("r-1-a", "r-1-b")));
        assertEquals(130D, harness.storedMoney(BOB), 1e-9, "paid straight into the stored balance");
        assertTrue(holdIds().isEmpty(), "both bets settled with the payout");
    }

    @Test
    void aRefusedPayoutStaysHeldAsThePayout() throws Exception {
        harness.config.set("ECONOMY.MAX-BALANCE", 100D);
        harness.addOnline(ALICE, "Alice", 90D);
        harness.addOnline(BOB, "Bob", 50D);
        assertTrue(wallet.take(harness.online.get(ALICE), hold("r-2-a", ALICE, 1000L)));
        assertTrue(wallet.take(harness.online.get(BOB), hold("r-2-b", BOB, 3000L)));

        // Alice (80.00 left) wins 40.00, which would put her above the 100.00 limit.
        assertFalse(wallet.pay(ALICE, "Alice", 4000L, EconomyReason.GAMBLE_PAYOUT, "Roulette win:",
                List.of("r-2-a", "r-2-b")));
        escrow.keepAsPayout("r-2-a", 4000L, List.of("r-2-b"));
        harness.drainWrites();
        List<EconomyStore.Hold> holds = EconomyStore.loadHolds(harness.connection());
        assertEquals(1, holds.size());
        assertEquals("r-2-a", holds.get(0).id());
        assertEquals(4000L, holds.get(0).cents(), "the whole pot waits for the next start");
    }

    @Test
    void holdsACrashLeftBehindAreRefundedOnceAtStart() throws Exception {
        harness.addOffline(ALICE, "Alice", 10D);
        EconomyStore.insertHold(harness.connection(), new EconomyStore.Hold("dice-crash", ALICE, "Alice", 500L,
                "dice roll", 1L));

        escrow.recover();
        harness.drainWrites();
        assertEquals(15D, harness.storedMoney(ALICE), 1e-9);
        assertTrue(holdIds().isEmpty());

        new GamblingEscrow(harness.plugin).recover();
        harness.drainWrites();
        assertEquals(15D, harness.storedMoney(ALICE), 1e-9, "a second start refunds nothing");
    }

    @Test
    void aFailedTakeLeavesNoHoldAndNoDebitForAnOfflineTransaction() throws Exception {
        harness.addOffline(ALICE, "Alice", 50D);
        // A hold id that already exists makes the transaction fail: neither the debit nor the hold may stay.
        EconomyStore.insertHold(harness.connection(), new EconomyStore.Hold("dup", BOB, "Bob", 1L, "x", 1L));
        var result = harness.economy.withdraw(org.bukkit.Bukkit.getOfflinePlayer(ALICE), 10D, EconomyReason.GAMBLE_STAKE,
                com.bx.smpPlugin.economy.EconomyContext.NONE.withStep(escrow.openStep(hold("dup", ALICE, 1000L))));
        assertFalse(result.success());
        assertEquals(50D, harness.storedMoney(ALICE), 1e-9);
        assertEquals(List.of("dup"), holdIds());
    }

    @Test
    void aThreePointOneEscrowFileIsRefundedOnceAndRenamed() throws Exception {
        harness.addOffline(ALICE, "Alice", 0D);
        File data = new File(harness.dataFolder, "data");
        assertTrue(data.mkdirs());
        File legacy = new File(data, "gambling-escrow.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("HOLDS.old-1.PLAYER", ALICE.toString());
        yaml.set("HOLDS.old-1.NAME", "Alice");
        yaml.set("HOLDS.old-1.CENTS", 1234L);
        yaml.set("HOLDS.old-1.GAME", "dice roll");
        yaml.save(legacy);

        escrow.recover();
        assertEquals(12.34D, harness.storedMoney(ALICE), 1e-9);
        assertFalse(legacy.exists());
        assertTrue(new File(data, "gambling-escrow.yml.recovered").exists());

        new GamblingEscrow(harness.plugin).recover();
        assertEquals(12.34D, harness.storedMoney(ALICE), 1e-9, "not refunded twice");
    }

    private List<String> holdIds() throws Exception {
        return EconomyStore.loadHolds(harness.connection()).stream().map(EconomyStore.Hold::id).toList();
    }

    private static GamblingEscrow.Hold hold(String id, UUID player, long cents) {
        return new GamblingEscrow.Hold(id, player, player.toString().substring(0, 8), cents, "test game");
    }

    @SuppressWarnings("unchecked")
    private void removeFromCache(UUID uuid) throws Exception {
        var field = com.bx.smpPlugin.managers.PlayerDataManager.class.getDeclaredField("cache");
        field.setAccessible(true);
        PlayerData data = ((java.util.Map<UUID, PlayerData>) field.get(harness.players)).remove(uuid);
        harness.database.savePlayer(data);
    }
}
