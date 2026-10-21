package com.bx.smpPlugin.halloween;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HalloweenModelTest {

    private static final YamlConfiguration BUNDLED =
            YamlConfiguration.loadConfiguration(new File("src/main/resources/halloween.yml"));

    @Test
    void candyIsSpentFromTheBalanceButNeverFromWhatWasEarned() {
        CandyAccount account = CandyAccount.EMPTY.earn(30);
        CandyAccount spent = account.spend(20).orElseThrow();
        assertEquals(10L, spent.balance());
        assertEquals(30L, spent.earned());
        assertTrue(spent.spend(11).isEmpty(), "never below zero");
        assertTrue(spent.spend(-1).isEmpty());
        assertEquals(spent, spent.earn(0).earn(-5));
    }

    @Test
    void candyStopsAtTheLimitInsteadOfOverflowing() {
        CandyAccount near = CandyAccount.EMPTY.earn(CandyAccount.MAX - 1);
        assertEquals(CandyAccount.MAX, near.earn(Long.MAX_VALUE).balance());
        assertEquals(CandyAccount.MAX, near.earn(10).earned());
    }

    @Test
    void candySurvivesSerialisationWithItsMilestones() {
        CandyAccount account = CandyAccount.EMPTY.earn(500).withMilestone(150).withMilestone(50);
        CandyAccount read = CandyAccount.parse(account.serialize());
        assertEquals(account, read);
        assertTrue(read.hasMilestone(50));
        assertFalse(read.hasMilestone(300));
        assertEquals("500;500;50,150", account.serialize());
        assertEquals(CandyAccount.EMPTY, CandyAccount.parse("x;y"));
        assertEquals(40L, CandyAccount.EMPTY.withBalance(40).earned(), "staff-set candy counts as earned");
    }

    @Test
    void questProgressFinishesOnceAndStaysFinished() {
        QuestProgress progress = QuestProgress.EMPTY.add("hunter", 3, 5);
        assertEquals(3, progress.count("hunter"));
        progress = progress.add("hunter", 4, 5);
        assertTrue(progress.isDone("hunter"));
        assertEquals(progress, progress.add("hunter", 1, 5));
        QuestProgress read = QuestProgress.parse(progress.add("pumpkins", 2, 32).serialize());
        assertTrue(read.isDone("hunter"));
        assertEquals(2, read.count("pumpkins"));
        assertEquals(0, QuestProgress.parse("broken;x=y").count("x"));
    }

    @Test
    void theBundledQuestsAndTreatsAreAllValid() {
        List<String> problems = new ArrayList<>();
        List<Quest> quests = Quest.readAll(BUNDLED.getConfigurationSection("QUESTS"), problems);
        assertEquals(List.of(), problems);
        assertEquals(6, quests.size());
        // Item treats need a server to read, so the bundled weights are checked straight from the file.
        var treats = BUNDLED.getConfigurationSection("TRICK-OR-TREAT.TREATS");
        long total = 0L;
        for (String id : treats.getKeys(false)) {
            int weight = treats.getInt(id + ".WEIGHT");
            assertTrue(weight > 0, id);
            total += weight;
        }
        assertEquals(100L, total);
        assertEquals(3, treats.getInt("jackpot.WEIGHT"), "the jackpot is 3 in 100");
    }

    @Test
    void badQuestsAndTreatsAreLeftOutWithAReason() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                QUESTS:
                  a: {TYPE: FLY, AMOUNT: 3}
                  b: {TYPE: KILL_MOB, AMOUNT: 3}
                  c: {TYPE: KILL_MONSTERS, AMOUNT: 0}
                  d: {TYPE: break-block, TARGET: pumpkin, AMOUNT: 2}
                TREATS:
                  zero: {WEIGHT: 0, MONEY: 5}
                  empty: {WEIGHT: 5}
                  good: {WEIGHT: 5, CANDY: 1}
                """);
        List<String> problems = new ArrayList<>();
        List<Quest> quests = Quest.readAll(yaml.getConfigurationSection("QUESTS"), problems);
        assertEquals(1, quests.size());
        assertTrue(quests.get(0).counts(QuestType.BREAK_BLOCK, "PUMPKIN"));
        assertFalse(quests.get(0).counts(QuestType.BREAK_BLOCK, "MELON"));
        assertEquals(3, problems.size(), problems.toString());
        TreatTable table = TreatTable.read(yaml.getConfigurationSection("TREATS"));
        assertEquals(1, table.treats().size());
        assertEquals(2, table.problems().size(), table.problems().toString());
    }

    @Test
    void treatsArePickedByWeight() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                TREATS:
                  common: {WEIGHT: 9, MONEY: 1}
                  rare: {WEIGHT: 1, MONEY: 100}
                """);
        TreatTable table = TreatTable.read(yaml.getConfigurationSection("TREATS"));
        Random random = new Random(42);
        int rare = 0;
        for (int i = 0; i < 10_000; i++) {
            if (table.pick(random).id().equals("rare")) {
                rare++;
            }
        }
        assertTrue(rare > 850 && rare < 1150, "about one in ten, got " + rare);
        assertEquals(TreatTable.FALLBACK, TreatTable.read(null).pick(random), "an empty table still gives something");
    }

    @Test
    void theDailyKnockLimitResetsEachDay() {
        KnockLog log = KnockLog.NEW.knock(10).knock(10);
        assertFalse(log.canKnock(10, 2));
        assertTrue(log.canKnock(11, 2));
        assertTrue(log.canKnock(10, 0), "0 means no limit");
        KnockLog read = KnockLog.parse(log.knock(11).serialize());
        assertEquals(1, read.usedOn(11));
        assertEquals(3, read.total());
    }

    @Test
    void houseVisitsAreReadBackExactly() {
        var visits = TrickOrTreatCommand.parseVisits("witch=1700000000000;npc:ghost=5");
        assertEquals(1700000000000L, visits.get("witch"));
        assertEquals("npc:ghost=5;witch=1700000000000", TrickOrTreatCommand.writeVisits(visits));
    }
}
