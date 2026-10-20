package com.bx.smpPlugin.halloween;

import com.bx.smpPlugin.halloween.CalendarRules.DoorState;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalendarRulesTest {

    private static final CalendarRules OCTOBER = CalendarRules.of(10, 1, 31, "DAILY", true, 0);

    @Test
    void dailyDoorsOpenOnTheirOwnDayAndStayOpenWhileCatchingUp() {
        LocalDate the13th = LocalDate.of(2026, 10, 13);
        assertEquals(DoorState.OPEN, OCTOBER.state(13, the13th, false));
        assertEquals(DoorState.LOCKED, OCTOBER.state(14, the13th, false));
        assertEquals(DoorState.OPEN, OCTOBER.state(2, the13th, false), "catch-up keeps earlier doors open");
        assertEquals(DoorState.CLAIMED, OCTOBER.state(2, the13th, true));
    }

    @Test
    void withoutCatchUpAMissedDoorTurnsIntoACobweb() {
        CalendarRules strict = CalendarRules.of(10, 1, 31, "DAILY", false, 0);
        LocalDate the13th = LocalDate.of(2026, 10, 13);
        assertEquals(DoorState.MISSED, strict.state(12, the13th, false));
        assertEquals(DoorState.OPEN, strict.state(13, the13th, false));
    }

    @Test
    void theSeasonEndsAfterTheLastDayUnlessThereAreGraceDays() {
        LocalDate november1st = LocalDate.of(2026, 11, 1);
        assertFalse(OCTOBER.inSeason(november1st));
        assertEquals(DoorState.MISSED, OCTOBER.state(31, november1st, false));

        CalendarRules grace = CalendarRules.of(10, 1, 31, "DAILY", false, 2);
        assertTrue(grace.inSeason(november1st));
        assertEquals(2026, grace.seasonYear(november1st));
        assertEquals(DoorState.OPEN, grace.state(31, november1st, false), "the Halloween door stays open in the grace days");
        assertEquals(DoorState.MISSED, grace.state(30, november1st, false));
        assertFalse(grace.inSeason(LocalDate.of(2026, 11, 3)));
    }

    @Test
    void allOnTheLastDayOpensEveryDoorOnHalloweenOnly() {
        CalendarRules halloween = CalendarRules.of(10, 1, 31, "ALL-ON-LAST-DAY", false, 0);
        assertEquals(DoorState.LOCKED, halloween.state(1, LocalDate.of(2026, 10, 30), false));
        assertEquals(DoorState.OPEN, halloween.state(1, LocalDate.of(2026, 10, 31), false));
        assertEquals(DoorState.OPEN, halloween.state(17, LocalDate.of(2026, 10, 31), false));
    }

    @Test
    void lastDayIsClampedToTheLengthOfTheMonth() {
        CalendarRules november = CalendarRules.of(11, 1, 31, "DAILY", true, 0);
        assertEquals(30, november.lastDayIn(2026));
        assertEquals(30, november.doorCount(2026));
        assertFalse(november.isDoor(31, 2026));
    }

    @Test
    void secondsUntilADoorOpensCountsToMidnight() {
        ZonedDateTime evening = ZonedDateTime.of(2026, 10, 12, 23, 0, 0, 0, ZoneOffset.UTC);
        assertEquals(3600L, OCTOBER.secondsUntilOpen(13, evening));
        assertEquals(0L, OCTOBER.secondsUntilOpen(12, evening));
        assertEquals(0L, OCTOBER.secondsUntilSeason(evening));
        ZonedDateTime september30th = ZonedDateTime.of(2026, 9, 30, 0, 0, 0, 0, ZoneOffset.UTC);
        assertEquals(86_400L, OCTOBER.secondsUntilSeason(september30th));
    }

    @Test
    void claimedDoorsAreBitsInOneNumber() {
        long mask = CalendarRules.withClaimed(0L, 1);
        mask = CalendarRules.withClaimed(mask, 31);
        assertTrue(CalendarRules.isClaimed(mask, 1));
        assertTrue(CalendarRules.isClaimed(mask, 31));
        assertFalse(CalendarRules.isClaimed(mask, 13));
        assertEquals(mask, CalendarRules.parseMask(Long.toString(mask)));
        assertFalse(CalendarRules.isClaimed(CalendarRules.withoutClaimed(mask, 31), 31));
        assertEquals(0L, CalendarRules.parseMask("not a number"));
    }
}
