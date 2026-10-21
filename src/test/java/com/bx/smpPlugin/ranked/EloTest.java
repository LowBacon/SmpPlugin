package com.bx.smpPlugin.ranked;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EloTest {

    private static final Elo.Settings SETTINGS = Elo.Settings.defaults();

    @Test
    void expectedScoresAreSymmetric() {
        assertEquals(0.5D, Elo.expected(1200, 1200), 1e-9);
        double strong = Elo.expected(1400, 1000);
        double weak = Elo.expected(1000, 1400);
        assertEquals(1D, strong + weak, 1e-9);
        assertEquals(0.909D, strong, 0.001D);
    }

    @Test
    void equalPlayersMoveByHalfTheKFactor() {
        Elo.Result result = Elo.rate(1000, 50, 1000, 50, Elo.Outcome.PLAYER_ONE_WINS, SETTINGS);
        assertEquals(1012, result.playerOneAfter());
        assertEquals(988, result.playerTwoAfter());
    }

    @Test
    void anUpsetIsWorthMore() {
        Elo.Result upset = Elo.rate(1000, 50, 1400, 50, Elo.Outcome.PLAYER_ONE_WINS, SETTINGS);
        Elo.Result expected = Elo.rate(1400, 50, 1000, 50, Elo.Outcome.PLAYER_ONE_WINS, SETTINGS);
        assertTrue(upset.playerOneAfter() - 1000 > expected.playerOneAfter() - 1400);
        assertEquals(1022, upset.playerOneAfter());
        assertEquals(1402, expected.playerOneAfter());
    }

    @Test
    void aDrawBetweenEqualsChangesNothing() {
        Elo.Result result = Elo.rate(1500, 50, 1500, 50, Elo.Outcome.DRAW, SETTINGS);
        assertEquals(1500, result.playerOneAfter());
        assertEquals(1500, result.playerTwoAfter());
    }

    @Test
    void newPlayersUseTheProvisionalKFactor() {
        assertEquals(40, SETTINGS.kFor(1000, 0));
        assertEquals(24, SETTINGS.kFor(1000, 10));
        assertEquals(16, SETTINGS.kFor(2100, 50));
        Elo.Result result = Elo.rate(1000, 0, 1000, 50, Elo.Outcome.PLAYER_ONE_WINS, SETTINGS);
        assertEquals(1020, result.playerOneAfter());
        assertEquals(988, result.playerTwoAfter());
    }

    @Test
    void ratingsNeverDropBelowTheFloor() {
        Elo.Result result = Elo.rate(100, 50, 100, 50, Elo.Outcome.PLAYER_TWO_WINS, SETTINGS);
        assertEquals(100, result.playerOneAfter());
        assertEquals(112, result.playerTwoAfter());
    }

    @Test
    void equalKFactorsKeepThePoolTheSameSize() {
        // No high-rating K-factor here, so both players always use the same K.
        Elo.Settings flat = new Elo.Settings(24, 40, 10, 16, Integer.MAX_VALUE, 100);
        for (int gap = -800; gap <= 800; gap += 37) {
            for (Elo.Outcome outcome : Elo.Outcome.values()) {
                Elo.Result result = Elo.rate(1500, 50, 1500 + gap, 50, outcome, flat);
                int before = 1500 + 1500 + gap;
                int after = result.playerOneAfter() + result.playerTwoAfter();
                assertTrue(Math.abs(before - after) <= 1, "rounding only, gap " + gap + " " + outcome);
            }
        }
    }
}
