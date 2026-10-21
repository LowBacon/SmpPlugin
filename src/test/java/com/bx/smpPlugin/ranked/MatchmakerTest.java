package com.bx.smpPlugin.ranked;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchmakerTest {

    private static final Matchmaker.Tolerance TOLERANCE = new Matchmaker.Tolerance(100, 25, 300);
    private static final long NOW = 1_000_000L;

    private static Matchmaker.Ticket ticket(int rating, long waitedSeconds) {
        return new Matchmaker.Ticket(UUID.randomUUID(), rating, NOW - waitedSeconds * 1000L);
    }

    @Test
    void closeRatingsAreMatchedAtOnce() {
        Matchmaker.Ticket a = ticket(1000, 0);
        Matchmaker.Ticket b = ticket(1080, 0);
        List<Matchmaker.Pair> pairs = Matchmaker.pair(List.of(a, b), TOLERANCE, NOW, null);
        assertEquals(1, pairs.size());
    }

    @Test
    void farRatingsWaitUntilTheGapIsAllowed() {
        Matchmaker.Ticket a = ticket(1000, 0);
        Matchmaker.Ticket b = ticket(1200, 0);
        assertTrue(Matchmaker.pair(List.of(a, b), TOLERANCE, NOW, null).isEmpty());
        // After 40 seconds both accept 100 + 4 * 25 = 200.
        Matchmaker.Ticket oldA = ticket(1000, 40);
        Matchmaker.Ticket oldB = ticket(1200, 40);
        assertEquals(1, Matchmaker.pair(List.of(oldA, oldB), TOLERANCE, NOW, null).size());
        // Both sides must accept: one patient player is not enough.
        Matchmaker.Ticket fresh = ticket(1200, 0);
        assertTrue(Matchmaker.pair(List.of(oldA, fresh), TOLERANCE, NOW, null).isEmpty());
    }

    @Test
    void toleranceStopsGrowingAtTheMaximum() {
        assertEquals(300, TOLERANCE.forTicket(ticket(1000, 3_600), NOW));
        assertEquals(100, TOLERANCE.forTicket(ticket(1000, 5), NOW));
    }

    @Test
    void theLongestWaitingPlayerGetsTheClosestOpponent() {
        Matchmaker.Ticket oldest = ticket(1000, 30);
        Matchmaker.Ticket far = ticket(1090, 10);
        Matchmaker.Ticket close = ticket(1010, 5);
        List<Matchmaker.Pair> pairs = Matchmaker.pair(List.of(far, close, oldest), TOLERANCE, NOW, null);
        assertEquals(1, pairs.size());
        assertEquals(oldest, pairs.get(0).first());
        assertEquals(close, pairs.get(0).second());
    }

    @Test
    void everyPlayerIsInOnePairAtMost() {
        List<Matchmaker.Ticket> queue = List.of(ticket(1000, 9), ticket(1005, 8), ticket(1010, 7), ticket(1015, 6), ticket(1020, 5));
        List<Matchmaker.Pair> pairs = Matchmaker.pair(queue, TOLERANCE, NOW, null);
        assertEquals(2, pairs.size());
        Set<UUID> seen = new HashSet<>();
        for (Matchmaker.Pair pair : pairs) {
            assertTrue(seen.add(pair.first().player()));
            assertTrue(seen.add(pair.second().player()));
        }
    }

    @Test
    void forbiddenPairsAreNeverFormed() {
        Matchmaker.Ticket a = ticket(1000, 10);
        Matchmaker.Ticket b = ticket(1001, 10);
        Matchmaker.Ticket c = ticket(1050, 5);
        UUID blockedA = a.player();
        UUID blockedB = b.player();
        List<Matchmaker.Pair> pairs = Matchmaker.pair(List.of(a, b, c), TOLERANCE, NOW,
                (x, y) -> !(Set.of(x, y).equals(Set.of(blockedA, blockedB))));
        assertEquals(1, pairs.size());
        assertEquals(Set.of(a.player(), c.player()), Set.of(pairs.get(0).first().player(), pairs.get(0).second().player()));
    }

    @Test
    void pairHistoryCountsOnlyTheWindow() {
        PairHistory history = new PairHistory(10_000L);
        UUID x = UUID.randomUUID();
        UUID y = UUID.randomUUID();
        history.record(x, y, 0L);
        history.record(y, x, 5_000L);
        assertEquals(2, history.count(x, y, 9_000L), "order of the two players does not matter");
        assertEquals(1, history.count(x, y, 12_000L));
        assertEquals(0, history.count(x, y, 20_000L));
        assertEquals(0, history.size(), "an empty pair is dropped");
        history.record(x, y, 100_000L);
        history.prune(200_000L);
        assertEquals(0, history.size());
    }
}
