package com.bx.smpPlugin.menus;

import com.bx.smpPlugin.managers.HomeManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatsAndChatLogMenuTest {

    @Test
    void killDeathRatioHandlesNoDeaths() {
        assertEquals("5.00", StatsMenu.kd(5, 0));
        assertEquals("2.50", StatsMenu.kd(5, 2));
        assertEquals("0.00", StatsMenu.kd(0, 3));
    }

    @Test
    void podiumPlacesGetTheirOwnColour() {
        assertTrue(StatsMenu.placeLine(1).startsWith("&6#1"));
        assertTrue(StatsMenu.placeLine(12).startsWith("&7#12"));
    }

    @Test
    void longChatMessagesWrapWithoutLosingWords() {
        List<String> lines = ChatLogsMenu.wrap("the quick brown fox jumps over the lazy dog and keeps on running far away");
        assertTrue(lines.size() > 1);
        assertEquals("the quick brown fox jumps over the lazy dog and keeps on running far away", String.join(" ", lines));
        lines.forEach(line -> assertTrue(line.length() <= 40));
        assertEquals(List.of(""), ChatLogsMenu.wrap("  "));
    }

    @Test
    void homeNamesAreShortAndCommandSafe() {
        assertTrue(HomeManager.isValidHomeName("base_2"));
        assertTrue(HomeManager.isValidHomeName("farm-1"));
        assertFalse(HomeManager.isValidHomeName("my base"));
        assertFalse(HomeManager.isValidHomeName("&cred"));
        assertFalse(HomeManager.isValidHomeName("abcdefghijklmnopq"));
        assertFalse(HomeManager.isValidHomeName(""));
    }
}
