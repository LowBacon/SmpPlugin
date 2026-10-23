// SmpPlugin by LowBacon
package com.bx.smpPlugin.engagement;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngagementLogicTest {

    @Test
    void dailyStreakGrowsOnConsecutiveDaysAndRestartsAfterTheGrace() {
        DailyStreak first = DailyStreak.NEW.claim(100L, 1);
        assertEquals(1, first.streak());
        assertFalse(first.canClaim(100L));
        assertTrue(first.canClaim(101L));

        DailyStreak second = first.claim(101L, 1);
        assertEquals(2, second.streak());
        DailyStreak afterGrace = second.claim(103L, 1);
        assertEquals(3, afterGrace.streak(), "one skipped day is forgiven");
        DailyStreak broken = afterGrace.claim(106L, 1);
        assertEquals(1, broken.streak(), "two skipped days restart the streak");
        assertEquals(4, broken.total());

        assertEquals(0, second.currentStreak(110L, 1));
        assertEquals(second, DailyStreak.parse(second.serialize()));
        assertEquals(DailyStreak.NEW, DailyStreak.parse("garbage"));
    }

    @Test
    void dailyRewardDaysRepeatEveryWeek() {
        assertEquals(1, DailyStreak.rewardDay(1));
        assertEquals(7, DailyStreak.rewardDay(7));
        assertEquals(1, DailyStreak.rewardDay(8));
        assertEquals(0, DailyStreak.weeksDone(7));
        assertEquals(1, DailyStreak.weeksDone(8));
    }

    @Test
    void challengesAreTheSameForEveryoneOnADayAndChangeBetweenDays() {
        List<String> pool = List.of("a", "b", "c", "d", "e", "f", "g", "h");
        List<String> today = Challenges.pick(pool, 20_000L, 3);
        assertEquals(today, Challenges.pick(pool, 20_000L, 3));
        assertEquals(3, new HashSet<>(today).size());
        boolean changed = false;
        for (long day = 20_001L; day < 20_010L && !changed; day++) {
            changed = !Challenges.pick(pool, day, 3).equals(today);
        }
        assertTrue(changed, "the selection changes from day to day");
        assertEquals(2, Challenges.pick(List.of("x", "y"), 5L, 3).size());
    }

    @Test
    void challengeDayStateSurvivesSaving() {
        Map<Challenges.Type, Long> baseline = new EnumMap<>(Challenges.Type.class);
        baseline.put(Challenges.Type.BREAK_BLOCKS, 1500L);
        baseline.put(Challenges.Type.KILL_MOBS, 12L);
        Set<String> done = new LinkedHashSet<>(List.of("miner"));
        Challenges.DayState state = new Challenges.DayState(20_000L, baseline, done, true);
        Challenges.DayState read = Challenges.DayState.parse(state.serialize());
        assertEquals(20_000L, read.day());
        assertEquals(1500L, read.baseline().get(Challenges.Type.BREAK_BLOCKS));
        assertEquals(Set.of("miner"), read.done());
        assertTrue(read.bonusPaid());
        assertNull(Challenges.DayState.parse("nonsense|x"));
    }

    @Test
    void scrambledWordsKeepTheirLettersButMove() {
        String scrambled = ChatGames.scramble("netherite");
        assertNotEquals("netherite", scrambled);
        char[] a = "netherite".toCharArray();
        char[] b = scrambled.toCharArray();
        java.util.Arrays.sort(a);
        java.util.Arrays.sort(b);
        assertEquals(new String(a), new String(b));
        assertEquals("ancient debris", ChatGames.normalize("  Ancient   DEBRIS "));
    }

    @Test
    void pendingItemsTextIsEmptyForNothing() {
        assertTrue(PendingItems.read(null).isEmpty());
        assertTrue(PendingItems.read("").isEmpty());
    }
}
