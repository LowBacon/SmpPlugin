// SmpPlugin by LowBacon
package com.bx.smpPlugin.coinflip;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.bx.smpPlugin.coinflip.CoinFlipTestHarness.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoinFlipServiceTest {

    @TempDir
    Path folder;

    private static CoinFlipService.CreateRequest publicMatch(UUID player, String name, long cents, CoinSide side) {
        return new CoinFlipService.CreateRequest(player, name, cents, side, null, null);
    }

    @Test
    void createJoinSettlePaysTheWinnerExactlyOnceAndRecordsEverything() throws Exception {
        try (CoinFlipTestHarness h = new CoinFlipTestHarness(folder, yaml -> { })) {
            UUID alice = h.player("Alice", 100_000);
            UUID bob = h.player("Bob", 100_000);
            h.random.next.add(CoinSide.HEADS);

            CoinFlipService.Outcome created = await(h.main(() -> h.service.create(publicMatch(alice, "Alice", 10_000, CoinSide.HEADS))));
            assertTrue(created.success(), created.messageKey());
            assertEquals(90_000L, h.economy.balanceCents(alice));
            assertEquals(900D, h.storedMoney(alice), 1e-9, "the stake is durable with its journal row");

            String id = created.match().id();
            CoinFlipService.Outcome joined = await(h.main(() -> h.service.join(bob, "Bob", id)));
            assertTrue(joined.success(), joined.messageKey());
            h.settleQueues();

            assertEquals(110_000L, h.economy.balanceCents(alice), "heads won: alice gets the 200.00 pot");
            assertEquals(90_000L, h.economy.balanceCents(bob));
            assertEquals(1_100D, h.storedMoney(alice), 1e-9);
            assertEquals(900D, h.storedMoney(bob), 1e-9);
            assertEquals(List.of("COMPLETED"), h.rows("SELECT state FROM coinflip_matches WHERE id = '" + id + "'"));
            assertEquals(List.of("PAYOUT|APPLIED", "STAKE|APPLIED", "STAKE|APPLIED"),
                    h.rows("SELECT kind, state FROM coinflip_transactions WHERE match_id = '" + id + "' ORDER BY kind, state"));
            assertEquals(List.of("1|1|0|10000|20000|10000"),
                    h.rows("SELECT played, wins, losses, staked_cents, gross_payout_cents, net_cents FROM coinflip_stats WHERE player_uuid = '" + alice + "'"));
            assertEquals(List.of("1|0|1|-10000"),
                    h.rows("SELECT played, wins, losses, net_cents FROM coinflip_stats WHERE player_uuid = '" + bob + "'"));
            assertEquals(1, h.notifier.completed.size());
            assertTrue(h.notifier.achievements.contains(Achievement.FIRST_WIN));

            // A second settlement attempt changes nothing.
            CoinFlipMatch match = created.match();
            h.main(() -> {
                h.service.settle(match);
                return null;
            });
            h.settleQueues();
            assertEquals(110_000L, h.economy.balanceCents(alice));
            assertEquals(1, h.rows("SELECT id FROM coinflip_transactions WHERE kind = 'PAYOUT'").size());
        }
    }

    @Test
    void feeIsTakenFromThePotRoundedDownAndRecorded() throws Exception {
        try (CoinFlipTestHarness h = new CoinFlipTestHarness(folder, yaml -> {
            yaml.set("ECONOMY.FEE-PERCENT", 2.5);
            yaml.set("ECONOMY.ALLOW-DECIMALS", true);
            yaml.set("ECONOMY.MIN-STAKE", 0.01);
        })) {
            UUID alice = h.player("Alice", 100_000);
            UUID bob = h.player("Bob", 100_000);
            h.random.next.add(CoinSide.TAILS);
            String id = await(h.main(() -> h.service.create(publicMatch(alice, "Alice", 3_333, CoinSide.HEADS)))).match().id();
            await(h.main(() -> h.service.join(bob, "Bob", id)));
            h.settleQueues();
            // pot 66.66, fee floor(6666 * 250 / 10000) = 166 cents, payout 65.00
            assertEquals(100_000L - 3_333L + 6_500L, h.economy.balanceCents(bob));
            assertEquals(List.of("FEE|166"), h.rows("SELECT kind, amount_cents FROM coinflip_transactions WHERE kind = 'FEE'"));
        }
    }

    @Test
    void onlyOneOfTwoSimultaneousJoinsWinsAndCancelCannotRaceAJoin() throws Exception {
        try (CoinFlipTestHarness h = new CoinFlipTestHarness(folder, yaml -> { })) {
            UUID alice = h.player("Alice", 100_000);
            UUID bob = h.player("Bob", 100_000);
            UUID carol = h.player("Carol", 100_000);
            String id = await(h.main(() -> h.service.create(publicMatch(alice, "Alice", 5_000, CoinSide.HEADS)))).match().id();

            List<CompletableFuture<CoinFlipService.Outcome>> attempts = h.main(() -> List.of(
                    h.service.join(bob, "Bob", id),
                    h.service.join(carol, "Carol", id),
                    h.service.cancel(alice, id)));
            int successes = 0;
            for (CompletableFuture<CoinFlipService.Outcome> attempt : attempts) {
                if (await(attempt).success()) {
                    successes++;
                }
            }
            h.settleQueues();
            assertEquals(1, successes, "exactly one of join, join, cancel succeeds");
            long total = h.economy.balanceCents(alice) + h.economy.balanceCents(bob) + h.economy.balanceCents(carol);
            assertEquals(300_000L, total, "no currency is created or destroyed without a fee");
            assertEquals(100_000L, h.economy.balanceCents(carol));
        }
    }

    @Test
    void expiredMatchesAreRefundedOnceAndOfflineCreatorsGetAClaim() throws Exception {
        try (CoinFlipTestHarness h = new CoinFlipTestHarness(folder, yaml -> yaml.set("MATCHES.EXPIRY-SECONDS", 60))) {
            UUID alice = h.player("Alice", 50_000);
            UUID dave = h.player("Dave", 50_000);
            await(h.main(() -> h.service.create(publicMatch(alice, "Alice", 10_000, CoinSide.HEADS))));
            await(h.main(() -> h.service.create(publicMatch(dave, "Dave", 20_000, CoinSide.TAILS))));
            h.economy.online.remove(dave);

            h.clock.addAndGet(61_000L);
            h.main(() -> {
                h.service.sweepExpired();
                h.service.sweepExpired();
                return null;
            });
            h.settleQueues();
            assertEquals(50_000L, h.economy.balanceCents(alice), "online creator refunded at once");
            assertEquals(30_000L, h.economy.balanceCents(dave), "offline creator not credited yet");
            assertEquals(List.of("PENDING_CLAIM"), h.rows("SELECT state FROM coinflip_transactions WHERE kind = 'REFUND' AND player_uuid = '" + dave + "'"));

            h.economy.online.add(dave);
            h.main(() -> {
                h.service.playerJoined(dave);
                h.service.deliverClaims(dave);
                return null;
            });
            h.settleQueues();
            assertEquals(50_000L, h.economy.balanceCents(dave), "claim paid exactly once");
            assertEquals(500D, h.storedMoney(dave), 1e-9);
            assertEquals(2, h.rows("SELECT id FROM coinflip_transactions WHERE kind = 'REFUND' AND state = 'APPLIED'").size());
        }
    }

    @Test
    void offlineWinnerIsPaidAsAClaimWhenTheyReturn() throws Exception {
        try (CoinFlipTestHarness h = new CoinFlipTestHarness(folder, yaml -> { })) {
            UUID alice = h.player("Alice", 100_000);
            UUID bob = h.player("Bob", 100_000);
            h.random.next.add(CoinSide.HEADS);
            String id = await(h.main(() -> h.service.create(publicMatch(alice, "Alice", 10_000, CoinSide.HEADS)))).match().id();
            h.main(() -> {
                h.service.join(bob, "Bob", id);
                return null;
            });
            // Alice disconnects during the reveal.
            h.economy.online.remove(alice);
            h.settleQueues();
            assertEquals(90_000L, h.economy.balanceCents(alice));
            assertEquals(List.of("PENDING_CLAIM"), h.rows("SELECT state FROM coinflip_transactions WHERE kind = 'PAYOUT'"));
            assertEquals(List.of("COMPLETED"), h.rows("SELECT state FROM coinflip_matches"));

            h.economy.online.add(alice);
            h.main(() -> {
                h.service.deliverClaims(alice);
                h.service.deliverClaims(alice);
                return null;
            });
            h.settleQueues();
            assertEquals(110_000L, h.economy.balanceCents(alice));
        }
    }

    @Test
    void restartRecoveryRefundsWaitingPaysLockedAndIsIdempotent() throws Exception {
        try (CoinFlipTestHarness h = new CoinFlipTestHarness(folder, yaml -> { })) {
            UUID alice = h.player("Alice", 100_000);
            UUID bob = h.player("Bob", 100_000);
            UUID carol = h.player("Carol", 100_000);
            await(h.main(() -> h.service.create(publicMatch(carol, "Carol", 7_000, CoinSide.TAILS))));
            String id = await(h.main(() -> h.service.create(publicMatch(alice, "Alice", 10_000, CoinSide.HEADS)))).match().id();
            // Lock alice's match by hand, as if the server died during the reveal.
            h.execute("UPDATE coinflip_matches SET state='LOCKED', opponent_uuid='" + bob + "', opponent_name='Bob', "
                    + "outcome='TAILS', outcome_id='x', winner_uuid='" + bob + "', joined_at=1 WHERE id='" + id + "'");
            h.execute("INSERT INTO coinflip_transactions VALUES ('t-bob','" + id + "','" + bob + "','Bob','STAKE',10000,'APPLIED','opponent stake',1,1,null)");
            // And an intent that never finished.
            h.execute("INSERT INTO coinflip_matches (id,state,creator_uuid,creator_name,creator_side,private_match,stake_cents,fee_bps,created_at,expires_at) "
                    + "VALUES ('ZZZZ2222','CREATING','" + bob + "','Bob','HEADS',0,500,0,1,2)");
            h.execute("INSERT INTO coinflip_transactions VALUES ('t-intent','ZZZZ2222','" + bob + "','Bob','STAKE',500,'INTENT','creator stake',1,1,null)");

            h.crash();
            h.start();
            h.start();

            assertEquals(List.of("COMPLETED"), h.rows("SELECT state FROM coinflip_matches WHERE id='" + id + "'"));
            assertEquals(List.of("PAYOUT|PENDING_CLAIM|20000"), h.rows("SELECT kind, state, amount_cents FROM coinflip_transactions WHERE kind='PAYOUT'"));
            assertEquals(List.of("EXPIRED"), h.rows("SELECT state FROM coinflip_matches WHERE creator_uuid='" + carol + "'"));
            assertEquals(1, h.rows("SELECT id FROM coinflip_transactions WHERE kind='REFUND' AND player_uuid='" + carol + "'").size(),
                    "recovery twice refunds once");
            assertEquals(List.of("RECOVERY_REQUIRED"), h.rows("SELECT state FROM coinflip_matches WHERE id='ZZZZ2222'"));
            assertEquals(List.of("UNKNOWN"), h.rows("SELECT state FROM coinflip_transactions WHERE id='t-intent'"));
            assertEquals(List.of("1|0"), h.rows("SELECT played, wins FROM coinflip_stats WHERE player_uuid='" + alice + "'"),
                    "statistics counted once");

            // Staff confirm the unknown stake was taken: it is refunded and the match closed.
            CoinFlipService.Outcome resolved = await(h.main(() -> h.service.resolve("Admin", "t-intent", true, "checked balance")));
            assertTrue(resolved.success(), resolved.messageKey());
            h.settleQueues();
            assertEquals(List.of("CANCELLED"), h.rows("SELECT state FROM coinflip_matches WHERE id='ZZZZ2222'"));
            assertEquals(List.of("REFUND|APPLIED|500"), h.rows("SELECT kind, state, amount_cents FROM coinflip_transactions WHERE match_id='ZZZZ2222' AND kind='REFUND'"));
            assertFalse(h.rows("SELECT action FROM coinflip_audit WHERE action='RESOLVED'").isEmpty());
        }
    }

    @Test
    void invalidRequestsMoveNoCurrency() throws Exception {
        try (CoinFlipTestHarness h = new CoinFlipTestHarness(folder, yaml -> yaml.set("ECONOMY.MIN-STAKE", 10))) {
            UUID alice = h.player("Alice", 5_000);
            assertEquals("stake-below-min", await(h.main(() -> h.service.create(publicMatch(alice, "Alice", 500, CoinSide.HEADS)))).messageKey());
            assertEquals("insufficient-funds", await(h.main(() -> h.service.create(publicMatch(alice, "Alice", 10_000, CoinSide.HEADS)))).messageKey());
            assertEquals("whole-numbers-only", await(h.main(() -> h.service.create(publicMatch(alice, "Alice", 1_050, CoinSide.HEADS)))).messageKey());
            String id = await(h.main(() -> h.service.create(publicMatch(alice, "Alice", 1_000, CoinSide.HEADS)))).match().id();
            assertEquals("match-own", await(h.main(() -> h.service.join(alice, "Alice", id))).messageKey());
            assertEquals("match-not-found", await(h.main(() -> h.service.join(alice, "Alice", "NOPE"))).messageKey());
            assertEquals(4_000L, h.economy.balanceCents(alice));

            h.economy.failWithdrawals = true;
            UUID bob = h.player("Bob", 5_000);
            CoinFlipService.Outcome failed = await(h.main(() -> h.service.join(bob, "Bob", id)));
            h.settleQueues();
            assertFalse(failed.success());
            assertEquals(5_000L, h.economy.balanceCents(bob));
            assertEquals(List.of("VOID"), h.rows("SELECT state FROM coinflip_transactions WHERE player_uuid='" + bob + "'"));
            assertEquals(MatchState.WAITING, h.main(() -> h.service.findActive(id).orElseThrow().state()), "match listed again");
        }
    }

    @Test
    void aFailedBalanceWriteReturnsTheStakeAndRelistsTheMatch() throws Exception {
        try (CoinFlipTestHarness h = new CoinFlipTestHarness(folder, yaml -> { })) {
            UUID alice = h.player("Alice", 50_000);
            UUID ghost = h.player("Ghost", 50_000);
            h.execute("DELETE FROM players WHERE uuid='" + ghost + "'");
            String id = await(h.main(() -> h.service.create(publicMatch(alice, "Alice", 10_000, CoinSide.HEADS)))).match().id();
            CoinFlipService.Outcome outcome = await(h.main(() -> h.service.join(ghost, "Ghost", id)));
            h.settleQueues();
            assertFalse(outcome.success());
            assertEquals(50_000L, h.economy.balanceCents(ghost), "rolled-back debit is reversed in memory");
            assertEquals(MatchState.WAITING, h.main(() -> h.service.findActive(id).orElseThrow().state()));
            assertEquals(List.of("WAITING"), h.rows("SELECT state FROM coinflip_matches WHERE id='" + id + "'"));
        }
    }

    @Test
    void dailyWagerCapIsEnforcedOnTheServer() throws Exception {
        try (CoinFlipTestHarness h = new CoinFlipTestHarness(folder, yaml -> {
            yaml.set("LIMITS.DAILY-WAGER-CAP", 150);
            yaml.set("MATCHES.MAX-ACTIVE-PER-PLAYER", 5);
        })) {
            UUID alice = h.player("Alice", 100_000);
            assertTrue(await(h.main(() -> h.service.create(publicMatch(alice, "Alice", 10_000, CoinSide.HEADS)))).success());
            CoinFlipService.Outcome second = await(h.main(() -> h.service.create(publicMatch(alice, "Alice", 10_000, CoinSide.HEADS))));
            assertEquals("daily-wager-cap", second.messageKey());
            assertEquals(90_000L, h.economy.balanceCents(alice));
        }
    }

    @Test
    void privateChallengesOnlyAcceptTheInviteeAndDeclineRefunds() throws Exception {
        try (CoinFlipTestHarness h = new CoinFlipTestHarness(folder, yaml -> { })) {
            UUID alice = h.player("Alice", 50_000);
            UUID bob = h.player("Bob", 50_000);
            UUID carol = h.player("Carol", 50_000);
            assertEquals("challenge-self", await(h.main(() -> h.service.create(
                    new CoinFlipService.CreateRequest(alice, "Alice", 1_000, CoinSide.HEADS, alice, "Alice")))).messageKey());
            CoinFlipService.Outcome challenge = await(h.main(() -> h.service.create(
                    new CoinFlipService.CreateRequest(alice, "Alice", 1_000, CoinSide.HEADS, bob, "Bob"))));
            assertTrue(challenge.success());
            String id = challenge.match().id();
            assertTrue(h.main(() -> h.service.publicListings().isEmpty()), "challenges are not listed publicly");
            assertEquals("not-your-challenge", await(h.main(() -> h.service.join(carol, "Carol", id))).messageKey());
            assertTrue(await(h.main(() -> h.service.decline(bob, id))).success());
            h.settleQueues();
            assertEquals(50_000L, h.economy.balanceCents(alice));
        }
    }

    @Test
    void practiceNeverMovesCurrency() throws Exception {
        try (CoinFlipTestHarness h = new CoinFlipTestHarness(folder, yaml -> { })) {
            UUID alice = h.player("Alice", 1_000);
            for (int i = 0; i < 5; i++) {
                h.main(() -> h.service.practiceFlip(alice, "Alice"));
            }
            h.settleQueues();
            assertEquals(1_000L, h.economy.balanceCents(alice));
            assertEquals(List.of("5|0"), h.rows("SELECT practice_flips, played FROM coinflip_stats"));
            assertTrue(h.rows("SELECT id FROM coinflip_transactions").isEmpty());
            assertTrue(h.notifier.achievements.contains(Achievement.FIRST_PRACTICE));
        }
    }

    @Test
    void storageRefusesASecondPayoutForTheSameMatch() throws Exception {
        try (CoinFlipTestHarness h = new CoinFlipTestHarness(folder, yaml -> { })) {
            UUID alice = h.player("Alice", 100_000);
            UUID bob = h.player("Bob", 100_000);
            String id = await(h.main(() -> h.service.create(publicMatch(alice, "Alice", 1_000, CoinSide.HEADS)))).match().id();
            await(h.main(() -> h.service.join(bob, "Bob", id)));
            h.settleQueues();
            UUID winner = UUID.fromString(h.rows("SELECT winner_uuid FROM coinflip_matches").getFirst());
            Exception refused = org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> h.execute(
                    "INSERT INTO coinflip_transactions VALUES ('dup','" + id + "','" + winner + "','X','PAYOUT',2000,'APPLIED','dup',1,1,null)"));
            assertTrue(refused.getMessage().toUpperCase().contains("UNIQUE"));
        }
    }
}
