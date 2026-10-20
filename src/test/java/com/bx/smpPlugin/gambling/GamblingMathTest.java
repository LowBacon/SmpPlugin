package com.bx.smpPlugin.gambling;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GamblingMathTest {

    private static final double EPSILON = 1e-9;

    // ------------------------------------------------------------------ dice

    @Test
    void parsesEveryKindOfBet() {
        assertEquals(DiceOdds.Mode.DUEL, DiceOdds.parse(null).orElseThrow().mode());
        assertEquals(DiceOdds.Mode.DUEL, DiceOdds.parse("vs").orElseThrow().mode());
        assertEquals(DiceOdds.Mode.HIGH, DiceOdds.parse("HIGH").orElseThrow().mode());
        assertEquals(DiceOdds.Mode.LOW, DiceOdds.parse("under").orElseThrow().mode());
        assertEquals(4, DiceOdds.parse("4").orElseThrow().number());
        assertTrue(DiceOdds.parse("7").isEmpty());
        assertTrue(DiceOdds.parse("0").isEmpty());
        assertTrue(DiceOdds.parse("banana").isEmpty());
    }

    @Test
    void chancesMatchCountingEveryOutcome() {
        for (DiceOdds.Bet bet : List.of(DiceOdds.Bet.duel(), new DiceOdds.Bet(DiceOdds.Mode.HIGH, 0),
                new DiceOdds.Bet(DiceOdds.Mode.LOW, 0), new DiceOdds.Bet(DiceOdds.Mode.NUMBER, 3))) {
            int wins = 0;
            int pushes = 0;
            int total = 0;
            for (int a = 1; a <= 6; a++) {
                for (int b = 1; b <= 6; b++) {
                    DiceOdds.Roll roll = new DiceOdds.Roll(a, bet.mode() == DiceOdds.Mode.DUEL ? b : 0);
                    DiceOdds.Result result = DiceOdds.judge(bet, roll, true);
                    wins += result == DiceOdds.Result.WIN ? 1 : 0;
                    pushes += result == DiceOdds.Result.PUSH ? 1 : 0;
                    total++;
                }
            }
            assertEquals(DiceOdds.winChance(bet), wins / (double) total, EPSILON, bet.describe());
            assertEquals(DiceOdds.pushChance(bet, true), pushes / (double) total, EPSILON, bet.describe());
        }
    }

    @Test
    void fairMultiplierBreaksEvenAndConfiguredOnesCannotBeatTheHouse() {
        DiceOdds.Bet duel = DiceOdds.Bet.duel();
        assertEquals(2.0, DiceOdds.fairMultiplier(duel, true), EPSILON);
        assertEquals(2.4, DiceOdds.fairMultiplier(duel, false), EPSILON);
        assertEquals(6.0, DiceOdds.fairMultiplier(new DiceOdds.Bet(DiceOdds.Mode.NUMBER, 1), true), EPSILON);
        assertEquals(1.0, DiceOdds.returnToPlayer(duel, 2.0, true), EPSILON);
        // Asking for more than fair is lowered to fair; less than 1x is raised to 1x.
        assertEquals(2.0, DiceOdds.clampMultiplier(5.0, duel, true), EPSILON);
        assertEquals(1.0, DiceOdds.clampMultiplier(0.2, duel, true), EPSILON);
        assertEquals(1.0, DiceOdds.clampMultiplier(Double.NaN, duel, true), EPSILON);
        assertEquals(0.95, DiceOdds.returnToPlayer(new DiceOdds.Bet(DiceOdds.Mode.HIGH, 0), 1.9, true), EPSILON);
    }

    @Test
    void payoutsAreExactCents() {
        assertEquals(19_000L, DiceOdds.payoutCents(10_000L, DiceOdds.Result.WIN, 1.9));
        assertEquals(10_000L, DiceOdds.payoutCents(10_000L, DiceOdds.Result.PUSH, 1.9));
        assertEquals(0L, DiceOdds.payoutCents(10_000L, DiceOdds.Result.LOSE, 1.9));
        // 1.9 is not exact in binary; the payout must still not lose a cent to rounding.
        assertEquals(190L, DiceOdds.payoutCents(100L, DiceOdds.Result.WIN, 1.9));
        assertEquals(1_900_000_000_000L, DiceOdds.payoutCents(1_000_000_000_000L, DiceOdds.Result.WIN, 1.9));
    }

    @Test
    void tieLosesWhenRefundsAreOff() {
        DiceOdds.Roll tie = new DiceOdds.Roll(4, 4);
        assertEquals(DiceOdds.Result.PUSH, DiceOdds.judge(DiceOdds.Bet.duel(), tie, true));
        assertEquals(DiceOdds.Result.LOSE, DiceOdds.judge(DiceOdds.Bet.duel(), tie, false));
    }

    @Test
    void facesAreTheUnicodeDice() {
        assertEquals("⚀", DiceOdds.face(1));
        assertEquals("⚅", DiceOdds.face(6));
        assertEquals("?", DiceOdds.face(7));
    }

    // ------------------------------------------------------------------ roulette

    @Test
    void everyCentIsOneTicket() {
        long[] stakes = {300L, 700L};
        assertEquals(0, RouletteMath.pick(stakes, 0L));
        assertEquals(0, RouletteMath.pick(stakes, 299L));
        assertEquals(1, RouletteMath.pick(stakes, 300L));
        assertEquals(1, RouletteMath.pick(stakes, 999L));
        assertThrows(IllegalArgumentException.class, () -> RouletteMath.pick(stakes, 1_000L));
        assertThrows(IllegalArgumentException.class, () -> RouletteMath.pick(stakes, -1L));
    }

    @Test
    void drawFollowsTheBetWeights() {
        long[] stakes = {100L, 300L, 600L};
        int[] wins = new int[3];
        Random random = new Random(42L);
        int draws = 200_000;
        for (int i = 0; i < draws; i++) {
            wins[RouletteMath.pick(stakes, random.nextLong(1_000L))]++;
        }
        assertEquals(0.1, wins[0] / (double) draws, 0.005);
        assertEquals(0.3, wins[1] / (double) draws, 0.005);
        assertEquals(0.6, wins[2] / (double) draws, 0.005);
    }

    @Test
    void feeRoundsDownAndPayoutAddsUp() {
        assertEquals(0L, RouletteMath.fee(12_345L, 0));
        assertEquals(617L, RouletteMath.fee(12_345L, 500));
        assertEquals(12_345L - 617L, RouletteMath.payout(12_345L, 500));
        assertEquals(6_172L, RouletteMath.fee(12_345L, 99_999));
        assertEquals("2.5%", RouletteMath.percent(250));
        assertEquals("33.3%", RouletteMath.chance(1L, 3L));
    }

    @Test
    void reelAlwaysStopsOnTheWinner() {
        Random random = new Random(7L);
        long[] stakes = {500L, 100L, 400L};
        for (int round = 0; round < 500; round++) {
            int winner = round % 3;
            int steps = 48 + random.nextInt(12);
            int[] reel = RouletteMath.reel(stakes, winner, steps + 9, 9, steps, random::nextLong);
            assertEquals(steps + 9, reel.length);
            assertEquals(winner, reel[steps + 4]);
            assertFalse(reel[steps + 3] == winner || reel[steps + 5] == winner, "winner beside the middle");
        }
    }

    @Test
    void reelDelaysSlowDown() {
        List<Integer> delays = RouletteMath.delays(50, 1, 7);
        assertEquals(50, delays.size());
        assertEquals(1, delays.getFirst());
        assertEquals(7, delays.getLast());
        for (int i = 1; i < delays.size(); i++) {
            assertTrue(delays.get(i) >= delays.get(i - 1));
        }
    }
}
