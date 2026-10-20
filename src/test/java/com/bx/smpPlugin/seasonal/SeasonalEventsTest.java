package com.bx.smpPlugin.seasonal;

import com.bx.smpPlugin.halloween.CalendarRules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeasonalEventsTest {

    private static final ZoneId HELSINKI = ZoneId.of("Europe/Helsinki");
    private static final CalendarRules OCTOBER = CalendarRules.of(10, 1, 31, "DAILY", true, 0);
    /** Advent with ten grace days, so the season runs into January. */
    private static final CalendarRules ADVENT = CalendarRules.of(12, 1, 24, "DAILY", false, 10);
    private static final Logger LOG = Logger.getLogger("SeasonalEventsTest");

    @TempDir
    Path folder;

    private final List<String> broadcasts = new ArrayList<>();

    private File file() {
        return folder.resolve("data/events.yml").toFile();
    }

    private SeasonalEvents events() {
        return new SeasonalEvents(new EventControl(file(), LOG), LOG, broadcasts::add);
    }

    @Test
    void anEventFollowsItsDatesWhileTheSwitchIsAuto() {
        FakeSource halloween = new FakeSource(OCTOBER, LocalDate.of(2026, 9, 30));
        SeasonalEvents events = events();
        events.register(EventId.HALLOWEEN, halloween);

        assertEquals(EventStatus.SCHEDULED, events.status(EventId.HALLOWEEN));
        halloween.today = LocalDate.of(2026, 10, 1);
        assertEquals(EventStatus.ACTIVE, events.status(EventId.HALLOWEEN));
        halloween.today = LocalDate.of(2026, 11, 1);
        assertEquals(EventStatus.COMPLETED, events.status(EventId.HALLOWEEN));
        halloween.today = LocalDate.of(2027, 1, 2);
        assertEquals(EventStatus.SCHEDULED, events.status(EventId.HALLOWEEN), "a new year waits for the next season");
    }

    @Test
    void aDecemberSeasonWithGraceDaysStaysActiveIntoJanuary() {
        FakeSource advent = new FakeSource(ADVENT, LocalDate.of(2027, 1, 3));
        SeasonalEvents events = events();
        events.register(EventId.ADVENT, advent);

        assertEquals(EventStatus.ACTIVE, events.status(EventId.ADVENT));
        advent.today = LocalDate.of(2027, 1, 4);
        assertEquals(EventStatus.SCHEDULED, events.status(EventId.ADVENT));
        advent.today = LocalDate.of(2026, 12, 24);
        assertEquals(EventStatus.ACTIVE, events.status(EventId.ADVENT));
    }

    @Test
    void forcingAnEventOnRunsItOutsideItsDatesAndSurvivesARestart() {
        FakeSource halloween = new FakeSource(OCTOBER, LocalDate.of(2026, 7, 14));
        SeasonalEvents events = events();
        events.register(EventId.HALLOWEEN, halloween);

        assertEquals(SeasonalEvents.Change.CHANGED, events.setMode(EventId.HALLOWEEN, EventMode.ON, "Mod"));
        assertEquals(EventStatus.ACTIVE, events.status(EventId.HALLOWEEN));
        assertEquals(1, broadcasts.size(), broadcasts.toString());
        assertTrue(broadcasts.get(0).contains("Halloween") && broadcasts.get(0).contains("/halloween"), broadcasts.toString());

        SeasonalEvents restarted = events();
        restarted.register(EventId.HALLOWEEN, halloween);
        assertEquals(EventMode.ON, restarted.mode(EventId.HALLOWEEN));
        assertEquals("Mod", restarted.setting(EventId.HALLOWEEN).changedBy());
        assertEquals(EventStatus.ACTIVE, restarted.status(EventId.HALLOWEEN));
    }

    @Test
    void switchingOffStopsAnEventDuringItsDatesAndAutoBringsItBack() {
        FakeSource halloween = new FakeSource(OCTOBER, LocalDate.of(2026, 10, 13));
        SeasonalEvents events = events();
        events.register(EventId.HALLOWEEN, halloween);

        events.setMode(EventId.HALLOWEEN, EventMode.OFF, "Admin");
        assertEquals(EventStatus.DISABLED, events.status(EventId.HALLOWEEN));
        assertTrue(broadcasts.get(0).contains("ended"), broadcasts.toString());

        events.setMode(EventId.HALLOWEEN, EventMode.AUTO, "Admin");
        assertEquals(EventStatus.ACTIVE, events.status(EventId.HALLOWEEN));
        assertEquals(2, broadcasts.size());
        assertEquals(SeasonalEvents.Change.UNCHANGED, events.setMode(EventId.HALLOWEEN, EventMode.AUTO, "Admin"));
        assertEquals(2, broadcasts.size(), "an unchanged switch says nothing to players");
    }

    @Test
    void theConfigFileAndTheFeatureSwitchWinOverForcingOn() {
        FakeSource halloween = new FakeSource(OCTOBER, LocalDate.of(2026, 10, 13));
        SeasonalEvents events = events();
        events.register(EventId.HALLOWEEN, halloween);
        events.setMode(EventId.HALLOWEEN, EventMode.ON, "Mod");

        halloween.configEnabled = false;
        assertEquals(EventStatus.DISABLED, events.status(EventId.HALLOWEEN));
        halloween.configEnabled = true;
        halloween.featureEnabled = false;
        assertEquals(EventStatus.DISABLED, events.status(EventId.HALLOWEEN));
    }

    @Test
    void theMinuteTickAnnouncesEachStartAndStopOnce() {
        FakeSource halloween = new FakeSource(OCTOBER, LocalDate.of(2026, 9, 30));
        SeasonalEvents events = events();
        events.register(EventId.HALLOWEEN, halloween);

        events.tick();
        assertTrue(broadcasts.isEmpty(), "registering and ticking without a change says nothing");
        halloween.today = LocalDate.of(2026, 10, 1);
        events.tick();
        events.tick();
        assertEquals(1, broadcasts.size(), broadcasts.toString());
        halloween.today = LocalDate.of(2026, 11, 1);
        events.tick();
        assertEquals(2, broadcasts.size(), broadcasts.toString());
        assertTrue(broadcasts.get(1).contains("ended"), broadcasts.toString());
    }

    @Test
    void aDamagedSwitchFileIsNeverOverwritten() throws Exception {
        Files.createDirectories(file().toPath().getParent());
        String damaged = "EVENTS:\n  halloween: [unclosed\n";
        Files.writeString(file().toPath(), damaged, StandardCharsets.UTF_8);
        SeasonalEvents events = events();
        events.register(EventId.HALLOWEEN, new FakeSource(OCTOBER, LocalDate.of(2026, 10, 13)));

        assertFalse(events.isStorageReadable());
        assertEquals(EventStatus.ACTIVE, events.status(EventId.HALLOWEEN), "the dates still apply");
        assertEquals(SeasonalEvents.Change.STORAGE_LOCKED, events.setMode(EventId.HALLOWEEN, EventMode.OFF, "Mod"));
        assertEquals(damaged, Files.readString(file().toPath(), StandardCharsets.UTF_8));
        assertEquals(EventStatus.ACTIVE, events.status(EventId.HALLOWEEN));
    }

    @Test
    void anUnknownModeInTheFileFallsBackToTheDates() throws Exception {
        Files.createDirectories(file().toPath().getParent());
        Files.writeString(file().toPath(), "EVENTS:\n  halloween:\n    MODE: SOMETIMES\n", StandardCharsets.UTF_8);
        SeasonalEvents events = events();
        assertEquals(EventMode.AUTO, events.mode(EventId.HALLOWEEN));
    }

    @Test
    void anEventThatDidNotStartIsOffAndCannotBeSwitched() {
        SeasonalEvents events = events();
        assertEquals(EventStatus.DISABLED, events.status(EventId.ADVENT));
        assertEquals(SeasonalEvents.Change.NOT_AVAILABLE, events.setMode(EventId.ADVENT, EventMode.ON, "Mod"));
        assertFalse(file().exists());
    }

    @Test
    void aSourceThatThrowsCountsAsOff() {
        FakeSource broken = new FakeSource(OCTOBER, LocalDate.of(2026, 10, 13));
        broken.broken = true;
        SeasonalEvents events = events();
        events.register(EventId.HALLOWEEN, broken);
        assertEquals(EventStatus.DISABLED, events.status(EventId.HALLOWEEN));
    }

    @Test
    void secondsUntilStartCountsDownOnlyWhileWaiting() {
        FakeSource halloween = new FakeSource(OCTOBER, LocalDate.of(2026, 9, 30));
        SeasonalEvents events = events();
        events.register(EventId.HALLOWEEN, halloween);
        assertEquals(12L * 3600L, events.secondsUntilStart(EventId.HALLOWEEN), "noon on 30 September");
        halloween.today = LocalDate.of(2026, 10, 2);
        assertEquals(0L, events.secondsUntilStart(EventId.HALLOWEEN));
    }

    @Test
    void namesAndModesAreReadForgivingly() {
        assertEquals(EventId.TRICK_OR_TREAT, EventId.parse("trick-or-treat").orElseThrow());
        assertEquals(EventId.TRICK_OR_TREAT, EventId.parse("TOT").orElseThrow());
        assertEquals(EventId.TRICK_OR_TREAT, EventId.parse("trick_or_treat").orElseThrow());
        assertEquals(EventId.ADVENT, EventId.parse(" Advent ").orElseThrow());
        assertTrue(EventId.parse("easter").isEmpty());
        assertEquals(EventMode.ON, EventMode.parse("enable").orElseThrow());
        assertEquals(EventMode.OFF, EventMode.parse("STOP").orElseThrow());
        assertEquals(EventMode.AUTO, EventMode.parse("schedule").orElseThrow());
        assertTrue(EventMode.parse("maybe").isEmpty());
    }

    private static final class FakeSource implements SeasonalEvents.Source {
        final CalendarRules rules;
        LocalDate today;
        boolean configEnabled = true;
        boolean featureEnabled = true;
        boolean broken;

        FakeSource(CalendarRules rules, LocalDate today) {
            this.rules = rules;
            this.today = today;
        }

        @Override
        public boolean configEnabled() {
            if (broken) {
                throw new IllegalStateException("bad config");
            }
            return configEnabled;
        }

        @Override
        public boolean featureEnabled() {
            return featureEnabled;
        }

        @Override
        public CalendarRules rules() {
            return rules;
        }

        @Override
        public ZonedDateTime now() {
            return today.atTime(12, 0).atZone(HELSINKI);
        }

        @Override
        public String command() {
            return "halloween";
        }
    }
}
