package com.bx.smpPlugin.dialogs.screens;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatsDialogTest {

    /** Every token StatsDialog fills on the sheet. */
    private static final Set<String> SHEET_TOKENS = Set.of(
            "player", "player_uuid", "status", "team", "money", "shards", "kills", "deaths", "kd", "playtime",
            "blocks_broken", "blocks_placed", "mobs_killed", "kill_streak", "highest_kill_streak");

    @Test
    void killDeathRatioNeverDividesByZero() {
        assertEquals("7.00", StatsDialog.kd(7, 0));
        assertEquals("0.50", StatsDialog.kd(1, 2));
        assertEquals("0.00", StatsDialog.kd(0, 0));
    }

    @Test
    void bundledSheetIsShortAndOnlyUsesTokensTheDialogFills() throws Exception {
        YamlConfiguration dialog = bundledDialog();
        assertTrue(dialog.getBoolean("SCREENS.STATS"));
        List<String> lines = dialog.getStringList("PLAYER_STATS_DIALOG.STATS-FORMAT");
        assertFalse(lines.isEmpty());
        assertTrue(lines.size() <= 4, "the stats sheet should stay simple");

        Pattern token = Pattern.compile("%([a-z_]+)%");
        Matcher matcher = token.matcher(String.join("\n", lines)
                + dialog.getString("PLAYER_STATS_DIALOG.HEADER") + dialog.getString("PLAYER_STATS_DIALOG.TITLE"));
        while (matcher.find()) {
            assertTrue(SHEET_TOKENS.contains(matcher.group(1)), "unknown token %" + matcher.group(1) + "%");
        }
    }

    @Test
    void bundledScreensHaveTheirButtonsAndInput() throws Exception {
        YamlConfiguration dialog = bundledDialog();
        assertEquals(2, dialog.getList("STATS_SEARCH_DIALOG.BUTTONS").size());
        assertEquals(1, dialog.getList("STATS_SEARCH_DIALOG.INPUTS").size());
        assertEquals(2, dialog.getList("PLAYER_STATS_DIALOG.BUTTONS").size());
        assertEquals("CLOSE", dialog.getString("PLAYER_STATS_DIALOG.AFTER-ACTION"));
    }

    private static YamlConfiguration bundledDialog() throws Exception {
        try (InputStream input = StatsDialogTest.class.getClassLoader().getResourceAsStream("dialog.yml")) {
            assertNotNull(input, "dialog.yml is bundled");
            YamlConfiguration configuration = new YamlConfiguration();
            configuration.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            return configuration;
        }
    }
}
