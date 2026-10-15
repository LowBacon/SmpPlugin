package com.bx.smpPlugin.managers;

import com.bx.smpPlugin.models.PunishmentRecord;
import com.bx.smpPlugin.models.PunishmentScope;
import com.bx.smpPlugin.models.PunishmentType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OffenseManagerTest {

    private static final long DAY = 86_400_000L;

    @Test
    void parsesDurations() {
        assertEquals(30L * DAY, OffenseManager.parseDurationToMillis("1mo"), "1mo is thirty days, not one minute");
        assertEquals(60_000L, OffenseManager.parseDurationToMillis("1m"));
        assertEquals(DAY + 12L * 3_600_000L, OffenseManager.parseDurationToMillis("1d12h"));
        assertEquals(24L * 3_600_000L, OffenseManager.parseDurationToMillis("24h"));
        assertEquals(365L * DAY, OffenseManager.parseDurationToMillis("1y"));
        assertEquals(7L * DAY, OffenseManager.parseDurationToMillis("7"), "a bare number is days");
        assertEquals(0L, OffenseManager.parseDurationToMillis("0s"));
        assertNull(OffenseManager.parseDurationToMillis("perm"));
        assertNull(OffenseManager.parseDurationToMillis("permanent"));
        assertNull(OffenseManager.parseDurationToMillis("nonsense"));
        assertNull(OffenseManager.parseDurationToMillis(""));
    }

    @Test
    void ladderRepeatsItsLastRung() {
        OffenseManager.OffenseRule rule = new OffenseManager.OffenseRule(
                "cheating", "Cheating", PunishmentType.BAN, List.of("14d", "30d", "perm"));
        assertEquals("14d", rule.getDurationForTier(0));
        assertEquals("30d", rule.getDurationForTier(1));
        assertEquals("perm", rule.getDurationForTier(2));
        assertEquals("perm", rule.getDurationForTier(9));
        assertEquals("perm", new OffenseManager.OffenseRule("x", "X", PunishmentType.BAN, List.of()).getDurationForTier(0));
        assertEquals("Cheating (cheating)", rule.reasonText());
    }

    @Test
    void parsesRulesCaseInsensitivelyWithFallbackType() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                offenses:
                  Chat-Spam:
                    name: "Chat Spam"
                    type: mute
                    durations: ["0s", "3d"]
                  weird:
                    type: NOT_A_TYPE
                    durations: "7d"
                """);
        Map<String, OffenseManager.OffenseRule> rules = OffenseManager.parse(yaml);
        assertEquals(PunishmentType.MUTE, rules.get("chat-spam").type());
        assertEquals(List.of("0s", "3d"), rules.get("chat-spam").durations());
        assertEquals(PunishmentType.BAN, rules.get("weird").type());
        assertEquals(List.of("7d"), rules.get("weird").durations());
        assertEquals("weird", rules.get("weird").name());
    }

    @Test
    void bundledOffensesAreValid() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStream input = OffenseManagerTest.class.getClassLoader().getResourceAsStream("offenses.yml")) {
            assertNotNull(input, "offenses.yml is bundled");
            yaml.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
        Map<String, OffenseManager.OffenseRule> rules = OffenseManager.parse(yaml);
        assertTrue(rules.size() >= 50, "the v2 offense list is carried over");
        for (OffenseManager.OffenseRule rule : rules.values()) {
            assertFalse(rule.durations().isEmpty(), rule.key() + " has durations");
            for (String duration : rule.durations()) {
                Long millis = OffenseManager.parseDurationToMillis(duration);
                assertTrue(duration.equalsIgnoreCase("perm") || millis != null,
                        rule.key() + " duration " + duration + " parses");
            }
        }
    }

    @Test
    void escalationCountsOnlyThisOffenseAndSkipsRemovedRecords() {
        OffenseManager.OffenseRule rule = new OffenseManager.OffenseRule(
                "cheating", "Cheating", PunishmentType.BAN, List.of("14d", "30d"));
        UUID target = UUID.randomUUID();
        List<PunishmentRecord> history = List.of(
                record(1, target, "Cheating (cheating)", null),
                record(2, target, "Cheating (cheating)", 5L),
                record(3, target, "Chat Spam (chat-spam)", null),
                record(4, target, "manual ban for cheating", null),
                record(5, target, "Old Name (cheating)", null)
        );
        assertEquals(2, OffenseManager.countMatching(history, rule));
    }

    private static PunishmentRecord record(long id, UUID target, String reason, Long removedAt) {
        return new PunishmentRecord(id, target, "Steve", PunishmentType.BAN, reason, null, "console",
                1L, null, null, removedAt == null ? null : "staff", removedAt, null, "local", PunishmentScope.SERVER);
    }
}
