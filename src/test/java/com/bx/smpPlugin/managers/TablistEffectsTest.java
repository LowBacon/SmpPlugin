// SmpPlugin by LowBacon
package com.bx.smpPlugin.managers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TablistEffectsTest {

    @Test
    void shimmerColoursEveryLetterAndMoves() {
        String frameA = TablistEffects.shimmer("Smp", 0x00ADFC, 0xFFFFFF, 0L, false);
        assertEquals(3, frameA.split("&#").length - 1, "one colour per letter");
        assertTrue(frameA.endsWith("p"));
        String frameB = TablistEffects.shimmer("Smp", 0x00ADFC, 0xFFFFFF, 3L, false);
        assertTrue(!frameA.equals(frameB), "the shine moves between frames");
        assertTrue(TablistEffects.shimmer("Smp", 0x00ADFC, 0xFFFFFF, 0L, true).contains("&l"));
        assertEquals("", TablistEffects.shimmer("", 0, 0, 0L, true));
    }

    @Test
    void shimmerRestsBetweenSweeps() {
        // Far past the text, every letter is back to the base colour.
        String resting = TablistEffects.shimmer("Smp", 0x00ADFC, 0xFFFFFF, 14L, false);
        assertEquals("&#00ADFCS&#00ADFCm&#00ADFCp", resting);
    }

    @Test
    void coloursBlend() {
        assertEquals(0x000000, TablistEffects.mix(0x000000, 0xFFFFFF, 0D));
        assertEquals(0xFFFFFF, TablistEffects.mix(0x000000, 0xFFFFFF, 1D));
        assertEquals(0x808080, TablistEffects.mix(0x000000, 0xFFFFFF, 0.5D));
    }

    @Test
    void activePlayersSortAboveAfkOnes() {
        assertTrue(TablistEffects.listOrder(0, false) > TablistEffects.listOrder(100, true),
                "a default player who is here beats an AFK owner");
        assertTrue(TablistEffects.listOrder(100, false) > TablistEffects.listOrder(10, false), "owner above vip");
        assertEquals(TablistEffects.listOrder(99_999, false), TablistEffects.listOrder(5_000_000, false), "weights are capped");
    }

    @Test
    void pingAndTpsAreColoured() {
        assertEquals("&a20ms", TablistEffects.colorPing(20));
        assertEquals("&e100ms", TablistEffects.colorPing(100));
        assertEquals("&c500ms", TablistEffects.colorPing(500));
        assertEquals("&a20.0", TablistEffects.colorTps(21.3D));
        assertEquals("&e18.2", TablistEffects.colorTps(18.24D));
        assertEquals("&c12.0", TablistEffects.colorTps(12D));
    }

    @Test
    void gradientLineHasOneColourPerCharacterAndMoves() {
        String first = TablistEffects.gradientLine("=", 10, 0x000000, 0x0000FF, 0xFFFFFF, 0);
        String later = TablistEffects.gradientLine("=", 10, 0x000000, 0x0000FF, 0xFFFFFF, 5);
        org.junit.jupiter.api.Assertions.assertEquals(10, first.split("&#", -1).length - 1);
        org.junit.jupiter.api.Assertions.assertEquals(10, first.chars().filter(c -> c == '=').count());
        org.junit.jupiter.api.Assertions.assertNotEquals(first, later);
        org.junit.jupiter.api.Assertions.assertEquals("", TablistEffects.gradientLine("", 10, 0, 0, 0, 0));
    }
}
