// SmpPlugin by LowBacon
package com.bx.smpPlugin.managers;

import com.bx.smpPlugin.display.GuideCommandTestAccess;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V31ConfigTest {

    @Test
    void oldDefaultFooterIsReplaced() {
        List<String> lines = new ArrayList<>(List.of(
                "  FOOTER:",
                "  - ''",
                ConfigManager.OLD_FOOTER_STATS_LINE,
                ConfigManager.OLD_FOOTER_SERVER_LINE,
                "  - '%animated_line%'"));
        List<String> migrated = ConfigManager.migrateFooterLines(lines);
        assertEquals(List.of("  FOOTER:", "  - ''", ConfigManager.NEW_FOOTER_SERVER_LINE, "  - '%animated_line%'"), migrated);
        for (String line : migrated) {
            assertFalse(line.contains("%money%") || line.contains("%ping") || line.contains("%tps"), line);
        }
    }

    @Test
    void customisedFooterIsLeftAlone() {
        List<String> custom = List.of(
                "  FOOTER:",
                "  - '&a$ &f%money%'",
                ConfigManager.OLD_FOOTER_SERVER_LINE);
        assertSame(custom, ConfigManager.migrateFooterLines(custom));
        List<String> reordered = List.of(ConfigManager.OLD_FOOTER_SERVER_LINE, ConfigManager.OLD_FOOTER_STATS_LINE);
        assertSame(reordered, ConfigManager.migrateFooterLines(reordered));
    }

    @Test
    void bundledTablistShowsNoMoneyPingTpsOrStats() throws Exception {
        YamlConfiguration config = bundled("config.yml");
        for (String path : List.of("TABLIST.HEADER", "TABLIST.FOOTER")) {
            for (String line : config.getStringList(path)) {
                for (String hidden : List.of("%money%", "%ping", "%tps", "%kills%", "%shards%", "%playtime%", "%deaths%")) {
                    assertFalse(line.contains(hidden), path + " still shows " + hidden + ": " + line);
                }
            }
        }
    }

    @Test
    void guidePagesOnlyNameRealCommands() throws Exception {
        YamlConfiguration guide = bundled("guide.yml");
        YamlConfiguration plugin = bundled("plugin.yml");
        List<String> known = new ArrayList<>();
        var commands = plugin.getConfigurationSection("commands");
        assertNotNull(commands);
        for (String name : commands.getKeys(false)) {
            known.add(name);
            known.addAll(commands.getStringList(name + ".aliases"));
        }
        // Commands other plugins usually provide are allowed; the guide hides them when they are missing.
        List<String> external = List.of("discord");
        var pages = guide.getConfigurationSection("PAGES");
        assertNotNull(pages);
        int commandLines = 0;
        for (String key : pages.getKeys(false)) {
            for (Object raw : pages.getList(key + ".LINES", List.of())) {
                String command = GuideCommandTestAccess.command(raw);
                if (command == null) {
                    continue;
                }
                commandLines++;
                String root = command.substring(1).split(" ")[0];
                assertTrue(known.contains(root) || external.contains(root), "guide page " + key + " names unknown /" + root);
            }
        }
        assertTrue(commandLines > 20);
        for (String removed : List.of("hug", "8ball", "hat", "trash", "notes")) {
            assertFalse(known.contains(removed), "/" + removed + " should be gone from plugin.yml");
        }
    }

    @Test
    void newSettingsFilesParse() throws Exception {
        YamlConfiguration borders = bundled("borders.yml");
        assertFalse(borders.getBoolean("SPAWN.ENABLED"));
        assertEquals("PLAYER", borders.getString("LOBBY.MODE"));
        YamlConfiguration extras = bundled("extras.yml");
        assertEquals(1.9, extras.getDouble("DICE.PAYOUTS.HIGH-LOW"), 1e-9);
        assertEquals(4, extras.getList("ROULETTE.QUICK-BETS").size());
        assertEquals(List.of("manager", "developer", "coowner", "owner"), extras.getStringList("GIVEAWAYS.MANAGER-GROUPS"));
        assertFalse(extras.contains("EMOTES.LIST.hug"));
        YamlConfiguration scoreboard = bundled("scoreboard.yml");
        assertEquals(10, scoreboard.getInt("SCOREBOARD.LINE-UPDATE-TICKS"));
    }

    @Test
    void guideLinesParse() {
        assertEquals("/spawn", GuideCommandTestAccess.command(Map.of("COMMAND", "spawn", "TEXT", "Go")));
        assertEquals(null, GuideCommandTestAccess.command("just text"));
        assertTrue(GuideCommandTestAccess.suggest(Map.of("COMMAND", "/pay", "SUGGEST", true)));
    }

    private static YamlConfiguration bundled(String name) throws Exception {
        try (Reader reader = new InputStreamReader(V31ConfigTest.class.getClassLoader().getResourceAsStream(name), StandardCharsets.UTF_8)) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(reader);
            return yaml;
        }
    }
}
