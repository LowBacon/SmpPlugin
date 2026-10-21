package com.bx.smpPlugin.managers;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The 4.1 configuration changes: no balance limit unless one is set. */
class V41ConfigTest {

    private static final List<String> OLD_ECONOMY = List.of(
            "ECONOMY:",
            ConfigManager.OLD_MAX_BALANCE_COMMENT.get(0),
            ConfigManager.OLD_MAX_BALANCE_COMMENT.get(1),
            ConfigManager.OLD_MAX_BALANCE_LINE,
            "  # Configuration section for /pay.",
            "  PAY:",
            "    MIN-AMOUNT: 0.01");

    @Test
    void theOldDefaultLimitBecomesNoLimit() throws Exception {
        List<String> migrated = ConfigManager.migrateMaxBalanceLines(OLD_ECONOMY);
        assertEquals(List.of(
                "ECONOMY:",
                ConfigManager.NEW_MAX_BALANCE_COMMENT.get(0),
                ConfigManager.NEW_MAX_BALANCE_COMMENT.get(1),
                ConfigManager.NEW_MAX_BALANCE_LINE,
                "  # Configuration section for /pay.",
                "  PAY:",
                "    MIN-AMOUNT: 0.01"), migrated);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(String.join("\n", migrated));
        assertEquals(0D, yaml.getDouble("ECONOMY.MAX-BALANCE", -1D));
        assertEquals(0.01D, yaml.getDouble("ECONOMY.PAY.MIN-AMOUNT"), "the rest of the section is untouched");
    }

    @Test
    void aLimitSomeoneChoseIsKept() {
        List<String> custom = new ArrayList<>(OLD_ECONOMY);
        custom.set(3, "  MAX-BALANCE: 5000000");
        List<String> migrated = ConfigManager.migrateMaxBalanceLines(custom);
        assertEquals("  MAX-BALANCE: 5000000", migrated.get(3));
        assertEquals(ConfigManager.NEW_MAX_BALANCE_COMMENT, migrated.subList(1, 3), "the comment describes 0 = no limit");
    }

    @Test
    void anEditedCommentLeavesTheFileAlone() {
        List<String> edited = List.of("ECONOMY:", "  # Keep the economy small.", ConfigManager.OLD_MAX_BALANCE_LINE);
        assertSame(edited, ConfigManager.migrateMaxBalanceLines(edited));
        List<String> commentOnly = List.of("ECONOMY:", ConfigManager.OLD_MAX_BALANCE_COMMENT.get(0),
                ConfigManager.OLD_MAX_BALANCE_COMMENT.get(1), "  PAY:");
        assertSame(commentOnly, ConfigManager.migrateMaxBalanceLines(commentOnly), "no MAX-BALANCE line follows");
    }

    @Test
    void theBundledConfigHasNoLimitWithTheLinesAMigratedFileGets() throws Exception {
        List<String> bundled = bundledLines("config.yml");
        int comment = Collections.indexOfSubList(bundled, ConfigManager.NEW_MAX_BALANCE_COMMENT);
        assertTrue(comment > 0, "config.yml carries the new comment");
        assertEquals(ConfigManager.NEW_MAX_BALANCE_LINE, bundled.get(comment + ConfigManager.NEW_MAX_BALANCE_COMMENT.size()));
        assertSame(bundled, ConfigManager.migrateMaxBalanceLines(bundled), "a current file is not migrated again");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(String.join("\n", bundled));
        assertEquals(0D, yaml.getDouble("ECONOMY.MAX-BALANCE", -1D));
    }

    private static List<String> bundledLines(String name) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                V41ConfigTest.class.getClassLoader().getResourceAsStream(name), StandardCharsets.UTF_8))) {
            return reader.lines().toList();
        }
    }
}
