package com.bx.smpPlugin.events;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventScheduleTest {

    private static EventSchedule schedule(String start, String end) {
        return EventSchedule.parse(start, end).orElseThrow();
    }

    private static LocalDateTime at(String text) {
        return LocalDateTime.parse(text);
    }

    @Test
    void aYearlyWindowRunsEveryYear() {
        EventSchedule christmas = schedule("12-20", "12-27");
        assertTrue(christmas.contains(at("2026-12-20T00:00")));
        assertTrue(christmas.contains(at("2031-12-26T23:59")));
        assertFalse(christmas.contains(at("2026-12-27T00:00")), "END is not included");
        assertFalse(christmas.contains(at("2026-12-19T23:59")));
        assertFalse(christmas.contains(at("2026-07-01T12:00")));
    }

    @Test
    void aWindowCanRunPastNewYear() {
        EventSchedule winter = schedule("12-28", "01-03");
        assertTrue(winter.contains(at("2026-12-31T12:00")));
        assertTrue(winter.contains(at("2027-01-02T12:00")));
        assertFalse(winter.contains(at("2027-01-03T00:00")));
        assertFalse(winter.contains(at("2027-06-01T00:00")));
        assertEquals(2026, winter.seasonYear(at("2027-01-02T12:00")), "progress belongs to the season that started in 2026");
    }

    @Test
    void timesAndOneOffDatesAreSupported() {
        EventSchedule weekend = schedule("2027-01-08 18:00", "2027-01-11 00:00");
        assertFalse(weekend.contains(at("2027-01-08T17:59")));
        assertTrue(weekend.contains(at("2027-01-08T18:00")));
        assertFalse(weekend.contains(at("2028-01-09T12:00")), "a dated window does not repeat");
        assertEquals(-1L, weekend.secondsUntilStart(at("2027-02-01T00:00")), "over");
        assertEquals(3_600L, weekend.secondsUntilStart(at("2027-01-08T17:00")));
        assertEquals(6L * 3_600L, weekend.secondsUntilEnd(at("2027-01-10T18:00")));
    }

    @Test
    void countdownsPointAtTheNextWindow() {
        EventSchedule summer = schedule("06-21", "08-31");
        assertEquals(0L, summer.secondsUntilStart(at("2026-07-01T00:00")));
        assertEquals(-1L, summer.secondsUntilEnd(at("2026-09-15T00:00")));
        assertTrue(summer.secondsUntilStart(at("2026-09-15T00:00")) > 0, "next year's summer");
        assertEquals(24L * 3_600L, summer.secondsUntilStart(at("2026-06-20T00:00")));
    }

    @Test
    void badDatesAreRefused() {
        for (String bad : new String[]{"", "13-01", "02-30", "2026-02-29", "12/24", "tomorrow", "12-24 25:00", null}) {
            assertTrue(EventSchedule.parsePoint(bad).isEmpty(), "should refuse " + bad);
        }
        assertTrue(EventSchedule.parse("12-01", null).isEmpty());
        assertTrue(EventSchedule.parsePoint("2028-02-29").isPresent(), "a real leap day");
        assertTrue(EventSchedule.parsePoint("02-29").isPresent(), "a yearly leap day falls back to the 28th");
    }
}
