package com.bx.smpPlugin.managers;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The 4.0 config files and the one-time branding migration. */
class V4ConfigTest {

    private static YamlConfiguration bundled(String name) throws Exception {
        try (Reader reader = new InputStreamReader(Objects.requireNonNull(
                V4ConfigTest.class.getClassLoader().getResourceAsStream(name), name), StandardCharsets.UTF_8)) {
            YamlConfiguration config = new YamlConfiguration();
            config.load(reader);
            return config;
        }
    }

    @Test
    void oldDefaultTitlesFollowTheBrandAfterTheMigration() {
        List<String> lines = List.of("  ANIMATION:", ConfigManager.OLD_TABLIST_TITLE_LINE, "  TITLE:",
                ConfigManager.OLD_SCOREBOARD_TITLE_LINE);
        List<String> migrated = ConfigManager.migrateBrandingLines(lines);
        assertEquals(List.of("  ANIMATION:", ConfigManager.NEW_TABLIST_TITLE_LINE, "  TITLE:",
                ConfigManager.NEW_SCOREBOARD_TITLE_LINE), migrated);
    }

    @Test
    void aTitleSomeoneChangedIsLeftAlone() {
        List<String> custom = List.of("    TEXT: MyServer", "  - '&aMy Server'");
        assertEquals(custom, ConfigManager.migrateBrandingLines(custom));
    }

    @Test
    void the4FilesAreValidAndComplete() throws Exception {
        YamlConfiguration events = bundled("events.yml");
        for (String id : List.of("ADVENT", "CHRISTMAS", "EASTER", "SUMMER")) {
            assertNotNull(events.getConfigurationSection("EVENTS." + id), id);
            assertNotNull(events.getString("EVENTS." + id + ".START"), id + " has dates");
        }
        assertFalse(events.getBoolean("CUSTOM-EVENTS.double-weekend.ENABLED"), "the example is off");

        YamlConfiguration advent = bundled("advent.yml");
        assertEquals(12, advent.getInt("MONTH"));
        assertEquals(24, advent.getInt("LAST-DAY"));
        assertNotNull(advent.getConfigurationSection("DOORS.24"));

        YamlConfiguration ranked = bundled("ranked.yml");
        assertEquals(1000, ranked.getInt("RATING.START"));
        assertTrue(ranked.getInt("ANTI-BOOST.MAX-GAMES-PER-PAIR") > 0);
        assertFalse(ranked.getMapList("TIERS").isEmpty());

        YamlConfiguration config = bundled("config.yml");
        assertEquals(0D, config.getDouble("ECONOMY.MAX-BALANCE"));
        assertTrue(config.getBoolean("ECONOMY.LEDGER.ENABLED"));
    }

    @Test
    void newCommandsAreDeclared() throws Exception {
        YamlConfiguration plugin = bundled("plugin.yml");
        var commands = plugin.getConfigurationSection("commands");
        assertNotNull(commands);
        for (String command : List.of("economy", "ranked", "event", "advent", "easter")) {
            assertTrue(commands.contains(command), command);
        }
        assertTrue(commands.getStringList("smpplugin.aliases").contains("smp"));
        assertTrue(commands.getStringList("leaderboard.aliases").contains("baltop"));
        assertTrue(commands.getStringList("balance.aliases").contains("money"));
    }

    @Test
    void noPlayerFacingTextNamesTheReferenceProjects() throws Exception {
        for (String file : List.of("scoreboard.yml", "config.yml", "menus.yml", "shop.yml", "messages.yml",
                "lobby.yml", "hide.yml", "dialog.yml", "events.yml", "advent.yml", "ranked.yml", "languages/en_US.yml")) {
            String text;
            try (var stream = Objects.requireNonNull(V4ConfigTest.class.getClassLoader().getResourceAsStream(file), file)) {
                text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            for (String line : text.split("\n")) {
                if (line.trim().startsWith("#")) {
                    continue;
                }
                // Internal names players never see: setting keys, permission nodes and placeholder names.
                String shown = line.replaceAll("[A-Za-z_\\-]*(?i:donut)[A-Za-z_\\-]*\\s*:", "")
                        .replaceAll("%[^%]*%", "")
                        .replaceAll("(?i)smpplugin\\.donut\\w*|donutauction\\.\\w+|donutplus\\w*", "");
                String lower = shown.toLowerCase(java.util.Locale.ROOT);
                assertFalse(lower.contains("donut") || lower.contains("beestoxd"),
                        file + " shows a reference project's name: " + line.trim());
            }
        }
    }
}
