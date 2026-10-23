// SmpPlugin by LowBacon
package com.bx.smpPlugin.coinflip;

import com.bx.smpPlugin.coinflip.storage.CoinFlipDao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static com.bx.smpPlugin.coinflip.CoinFlipTestHarness.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoinFlipStorageTest {

    @TempDir
    Path folder;

    @Test
    void leaderboardsBreakTiesDeterministicallyAndHidePrivateProfiles() throws Exception {
        try (CoinFlipTestHarness h = new CoinFlipTestHarness(folder, yaml -> { })) {
            UUID a = UUID.fromString("00000000-0000-0000-0000-00000000000a");
            UUID b = UUID.fromString("00000000-0000-0000-0000-00000000000b");
            UUID c = UUID.fromString("00000000-0000-0000-0000-00000000000c");
            await(h.repository.transaction(dao -> {
                dao.saveStats(stats(a, "A", 10, 5));
                dao.saveStats(stats(b, "B", 8, 5));
                dao.saveStats(stats(c, "C", 30, 9).withHidden(true));
                return null;
            }));
            List<CoinFlipDao.RankRow> wins = await(h.repository.transaction(dao -> dao.leaderboard(CoinFlipDao.Metric.WINS, null, 10, 1)));
            assertEquals(List.of("B", "A"), wins.stream().map(CoinFlipDao.RankRow::playerName).toList(),
                    "equal wins: fewer matches ranks first; the hidden profile is excluded");
            List<CoinFlipDao.RankRow> rate = await(h.repository.transaction(dao -> dao.leaderboard(CoinFlipDao.Metric.WIN_RATE, null, 10, 9)));
            assertEquals(List.of("A"), rate.stream().map(CoinFlipDao.RankRow::playerName).toList(), "win rate needs the minimum sample");
            assertEquals(5_000L, rate.getFirst().value(), "50.00% stored as basis points");
        }
    }

    @Test
    void aBalanceWriteNeverReplacesANewerStoredBalanceAndCountsAnEqualOneAsSaved() throws Exception {
        try (CoinFlipTestHarness h = new CoinFlipTestHarness(folder, yaml -> { })) {
            UUID alice = h.player("Alice", 50_000);
            // The economy's own writer stored version 7 before CoinFlip's transaction ran.
            await(h.repository.transaction(dao -> {
                dao.writeBalance(alice, new EconomyGateway.StoredBalance(48_000L, 7L));
                return null;
            }));
            // Same version: already durable, not an error (the stake must not be reversed).
            await(h.repository.transaction(dao -> {
                dao.writeBalance(alice, new EconomyGateway.StoredBalance(48_000L, 7L));
                return null;
            }));
            // An older version arriving late is ignored.
            await(h.repository.transaction(dao -> {
                dao.writeBalance(alice, new EconomyGateway.StoredBalance(50_000L, 6L));
                return null;
            }));
            assertEquals(480D, h.storedMoney(alice), 1e-9);
            UUID nobody = UUID.randomUUID();
            org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> await(h.repository.transaction(dao -> {
                dao.writeBalance(nobody, new EconomyGateway.StoredBalance(100L, 1L));
                return null;
            })), "a missing account is still a failure");
        }
    }

    @Test
    void historyIsPagedNewestFirstWithoutLoadingEverything() throws Exception {
        try (CoinFlipTestHarness h = new CoinFlipTestHarness(folder, yaml -> yaml.set("MATCHES.MAX-ACTIVE-PER-PLAYER", 10))) {
            UUID alice = h.player("Alice", 1_000_000);
            for (int i = 0; i < 5; i++) {
                String id = await(h.main(() -> h.service.create(new CoinFlipService.CreateRequest(alice, "Alice", 100, CoinSide.HEADS, null, null)))).match().id();
                h.clock.addAndGet(1_000L);
                await(h.main(() -> h.service.cancel(alice, id)));
            }
            h.settleQueues();
            List<CoinFlipMatch> first = await(h.repository.transaction(dao -> dao.loadHistory(alice, CoinFlipDao.HistoryFilter.ALL, 2, 0)));
            List<CoinFlipMatch> third = await(h.repository.transaction(dao -> dao.loadHistory(alice, CoinFlipDao.HistoryFilter.ALL, 2, 4)));
            assertEquals(2, first.size());
            assertEquals(1, third.size());
            assertTrue(first.get(0).settledAtMillis() >= first.get(1).settledAtMillis());
            assertEquals(5, await(h.repository.transaction(dao -> dao.loadHistory(alice, CoinFlipDao.HistoryFilter.CANCELLED, 10, 0))).size());
            assertEquals(0, await(h.repository.transaction(dao -> dao.loadHistory(alice, CoinFlipDao.HistoryFilter.WINS, 10, 0))).size(),
                    "cancelled matches are never wins");
            assertEquals(1_000_000L, h.economy.balanceCents(alice));
        }
    }

    @Test
    void cooldownsBlockRepeatsUntilTheyExpire() {
        RateLimiter limiter = new RateLimiter();
        UUID player = UUID.randomUUID();
        assertEquals(0L, limiter.tryAcquire(player, 1_000L, 500L));
        assertEquals(400L, limiter.tryAcquire(player, 1_100L, 500L));
        assertEquals(0L, limiter.tryAcquire(player, 1_500L, 500L));
        limiter.forget(player);
        assertEquals(0L, limiter.remaining(player, 1_501L, 500L));
    }

    private static CoinFlipStats stats(UUID id, String name, long played, long wins) {
        return new CoinFlipStats(id, name, played, wins, played - wins, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, "", false);
    }
}
