package com.bx.smpPlugin.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BorderZoneTest {

    private static BorderZone zone(double size) {
        return new BorderZone("spawn", true, "world", 100D, -50D, size, BorderZone.Mode.PLAYER, 5, 15, 0.2D, 5D);
    }

    @Test
    void squareContainsItsEdgesButNothingBeyond() {
        BorderZone zone = zone(200D);
        assertTrue(zone.contains(100D, -50D));
        assertTrue(zone.contains(200D, 50D));
        assertTrue(zone.contains(0D, -150D));
        assertFalse(zone.contains(200.01D, 0D));
        assertFalse(zone.contains(100D, -150.5D));
    }

    @Test
    void clampPutsAPointBackInside() {
        BorderZone zone = zone(20D);
        assertArrayEquals(new double[]{109D, -50D}, zone.clamp(150D, -50D, 1D), 1e-9);
        assertArrayEquals(new double[]{100D, -50D}, zone.clamp(100D, -50D, 1D), 1e-9);
        assertTrue(zone.containsWithin(105D, -50D, 1D));
        assertFalse(zone.containsWithin(109.5D, -50D, 1D));
    }

    @Test
    void settingsAreKeptInRange() {
        BorderZone zone = new BorderZone("lobby", true, null, 0D, 0D, 1D, null, -3, 99_999, Double.NaN, -1D);
        assertEquals(BorderZone.MIN_SIZE, zone.size());
        assertEquals("", zone.world());
        assertEquals(BorderZone.Mode.PLAYER, zone.mode());
        assertEquals(0, zone.warningDistance());
        assertEquals(3_600, zone.warningTime());
        assertEquals(0.2D, zone.damageAmount());
        assertEquals(0D, zone.damageBuffer());
        assertFalse(zone.configured());
        assertFalse(zone.active());
        assertEquals(BorderZone.MAX_SIZE, BorderZone.clampSize(1e12));
        assertEquals(200D, BorderZone.clampSize(Double.POSITIVE_INFINITY));
    }

    @Test
    void modeParsingFallsBack() {
        assertEquals(BorderZone.Mode.WORLD, BorderZone.Mode.parse(" world ", BorderZone.Mode.PLAYER));
        assertEquals(BorderZone.Mode.PLAYER, BorderZone.Mode.parse("everything", BorderZone.Mode.PLAYER));
        assertEquals(null, BorderZone.Mode.parse("nope", null));
    }

    @Test
    void withersKeepTheOtherSettings() {
        BorderZone zone = zone(100D).withArea("lobby_world", 1D, 2D, 50D).withMode(BorderZone.Mode.WORLD).withEnabled(false);
        assertEquals("lobby_world", zone.world());
        assertEquals(50D, zone.size());
        assertEquals(BorderZone.Mode.WORLD, zone.mode());
        assertFalse(zone.enabled());
        assertEquals(5, zone.warningDistance());
        assertEquals("50 x 50 around 1, 2 in lobby_world", zone.describe());
    }
}
