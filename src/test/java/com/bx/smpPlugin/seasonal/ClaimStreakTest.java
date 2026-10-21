package com.bx.smpPlugin.seasonal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimStreakTest {

    @Test
    void daysInARowBuildTheStreakAndASecondDoorTheSameDayDoesNot() {
        ClaimStreak streak = ClaimStreak.NEW.claimOn(100, 0);
        assertEquals(1, streak.streak());
        streak = streak.claimOn(100, 0);
        assertEquals(1, streak.streak(), "two doors on one day are one day");
        streak = streak.claimOn(101, 0).claimOn(102, 0);
        assertEquals(3, streak.streak());
        assertEquals(3, streak.best());
    }

    @Test
    void aMissedDayStartsAgainUnlessTheRankGivesGraceDays() {
        ClaimStreak streak = ClaimStreak.NEW.claimOn(100, 0).claimOn(101, 0);
        assertEquals(1, streak.claimOn(103, 0).streak());
        assertEquals(3, streak.claimOn(103, 1).streak(), "one grace day bridges one missed day");
        assertEquals(1, streak.claimOn(104, 1).streak());
        assertEquals(2, streak.claimOn(104, 1).best(), "the best run is kept");
    }

    @Test
    void theCurrentStreakDropsToZeroOnceBroken() {
        ClaimStreak streak = ClaimStreak.NEW.claimOn(100, 0).claimOn(101, 0);
        assertEquals(2, streak.current(101, 0));
        assertEquals(2, streak.current(102, 0), "still alive the next day");
        assertEquals(0, streak.current(103, 0));
        assertEquals(0, ClaimStreak.NEW.current(103, 0));
    }

    @Test
    void milestonesAndCompletionAreRememberedThroughSerialisation() {
        ClaimStreak streak = ClaimStreak.NEW.claimOn(5, 0).withMilestone(3).withMilestone(24).withCompleted();
        ClaimStreak read = ClaimStreak.parse(streak.serialize());
        assertEquals(streak, read);
        assertTrue(read.hasMilestone(3));
        assertTrue(read.hasMilestone(24));
        assertFalse(read.hasMilestone(7));
        assertTrue(read.completed());
        assertEquals(ClaimStreak.NEW, ClaimStreak.parse("broken;value"));
        assertEquals(ClaimStreak.NEW, ClaimStreak.parse(null));
    }

    @Test
    void aTestDateBeforeTheLastClaimLeavesTheStreakAlone() {
        ClaimStreak streak = ClaimStreak.NEW.claimOn(100, 0).claimOn(101, 0);
        assertEquals(streak, streak.claimOn(90, 0));
    }
}
