package com.bx.smpPlugin.perks;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerksTest {

    private static final Logger LOG = Logger.getLogger("PerksTest");

    private static YamlConfiguration yaml(String text) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return yaml;
    }

    @Test
    void theBundledFileParsesWithoutProblemsAndVipPlusOutranksVip() {
        YamlConfiguration bundled = YamlConfiguration.loadConfiguration(new File("src/main/resources/perks.yml"));
        Perks perks = new Perks(() -> bundled, () -> true, LOG);
        assertEquals(List.of(), perks.problems());
        assertEquals("vipplus", perks.ranks().get(0).id());
        Set<String> both = Set.of("smpplugin.rank.vip", "smpplugin.rank.vipplus");
        assertEquals("vipplus", perks.rankOf(both::contains).orElseThrow().id(), "only the highest rank counts");
        assertEquals(20, perks.value(both::contains, Perk.CALENDAR_BONUS_PERCENT));
        assertEquals(10, perks.value(Set.of("smpplugin.rank.vip")::contains, Perk.CALENDAR_BONUS_PERCENT));
        assertEquals(0, perks.value(Set.<String>of()::contains, Perk.CALENDAR_BONUS_PERCENT));
    }

    @Test
    void valuesAboveTheCapAreCappedAndReported() throws Exception {
        Perks perks = new Perks(() -> {
            try {
                return yaml("""
                        CAPS:
                          CALENDAR-BONUS-PERCENT: 40
                          DAILY-BONUS-PERCENT: 500
                        RANKS:
                          gold:
                            PERMISSION: rank.gold
                            PRIORITY: 5
                            PERKS:
                              CALENDAR-BONUS-PERCENT: 90
                              DAILY-BONUS-PERCENT: 400
                              TRICK-OR-TREAT-COOLDOWN-CUT-PERCENT: -5
                              FLY: 1
                        """);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }, () -> true, LOG);
        Rank gold = perks.rank("GOLD").orElseThrow();
        assertEquals(40, gold.value(Perk.CALENDAR_BONUS_PERCENT), "the CAPS section lowers the limit");
        assertEquals(100, gold.value(Perk.DAILY_BONUS_PERCENT), "CAPS can never raise the hard limit");
        assertEquals(0, gold.value(Perk.TRICK_OR_TREAT_COOLDOWN_CUT_PERCENT));
        List<String> problems = perks.problems();
        assertTrue(problems.stream().anyMatch(p -> p.contains("FLY")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("negative")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("at most 100")), problems.toString());
    }

    @Test
    void ranksWithoutOrSharingAPermissionAreLeftOut() throws Exception {
        YamlConfiguration file = yaml("""
                RANKS:
                  a:
                    PERMISSION: rank.same
                    PRIORITY: 1
                  b:
                    PERMISSION: RANK.SAME
                    PRIORITY: 2
                  c:
                    PRIORITY: 3
                """);
        List<String> problems = new ArrayList<>();
        List<Rank> ranks = Perks.parse(file, problems);
        assertEquals(1, ranks.size());
        assertEquals("a", ranks.get(0).id());
        assertEquals(2, problems.size(), problems.toString());
    }

    @Test
    void switchingPerksOffMeansNobodyHasARank() throws Exception {
        YamlConfiguration file = yaml("""
                ENABLED: false
                RANKS:
                  vip:
                    PERMISSION: rank.vip
                    PERKS:
                      CALENDAR-BONUS-PERCENT: 10
                """);
        Perks perks = new Perks(() -> file, () -> true, LOG);
        assertTrue(perks.rankOf(node -> true).isEmpty());
        Perks featureOff = new Perks(() -> {
            file.set("ENABLED", true);
            return file;
        }, () -> false, LOG);
        assertTrue(featureOff.rankOf(node -> true).isEmpty());
    }

    @Test
    void percentBonusesRoundDownToWholeCentsAndUnits() {
        assertEquals(825D, Perks.withPercent(750D, 10), 1e-9);
        assertEquals(0.01D, Perks.withPercent(0.01D, 50), 1e-9, "half a cent is not paid");
        assertEquals(3L, Perks.withPercent(3L, 10));
        assertEquals(36L, Perks.withPercent(30L, 20));
        assertEquals(500D, Perks.withPercent(500D, 0), 1e-9);
    }
}
