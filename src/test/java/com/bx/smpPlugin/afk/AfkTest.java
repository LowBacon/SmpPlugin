package com.bx.smpPlugin.afk;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfkTest {

    @Test
    void headTurnsWrapAroundTheCircle() {
        assertEquals(2.0D, AfkManager.angleDelta(179.0F, -179.0F), 1.0E-6);
        assertEquals(-2.0D, AfkManager.angleDelta(-179.0F, 179.0F), 1.0E-6);
        assertEquals(0.0D, AfkManager.angleDelta(90.0F, 450.0F), 1.0E-6);
    }

    @Test
    void regionsNormaliseTheirCorners() {
        AfkZone.Region region = new AfkZone.Region("world", 10, 80, 10, -10, 60, -10);
        assertEquals(-10D, region.minX());
        assertEquals(60D, region.minY());
        assertEquals(10D, region.maxZ());
        assertTrue(region.contains(0, 70, 0));
        assertTrue(region.contains(10, 80, 10), "the edges are inside");
        assertFalse(region.contains(11, 70, 0));
        assertFalse(region.contains(0, 81, 0));
    }

    @Test
    void zonesWithoutAnAreaOnlyTeleport() {
        AfkZone teleportOnly = new AfkZone("spot", null, "world,0,64,0,0,0", null, 5L, 60);
        assertFalse(teleportOnly.hasRegion());
        assertFalse(teleportOnly.paysRewards());
        assertEquals("&dspot", teleportOnly.displayName());
        assertNull(teleportOnly.region());

        AfkZone paying = teleportOnly.withRegion(new AfkZone.Region("world", 0, 0, 0, 5, 5, 5));
        assertTrue(paying.paysRewards());
        assertFalse(paying.withReward(0L, 60).paysRewards(), "a zero reward pays nothing");
        assertEquals(1, paying.withReward(1L, -5).rewardSeconds(), "the interval is at least a second");
    }

    @Test
    void zoneNamesAreSafe() {
        assertTrue(AfkManager.isValidZoneId("Lounge"));
        assertTrue(AfkManager.isValidZoneId("afk-1"));
        assertFalse(AfkManager.isValidZoneId("two words"));
        assertFalse(AfkManager.isValidZoneId("dots.break.yaml"));
        assertFalse(AfkManager.isValidZoneId(""));
    }

    @Test
    void rewardTimesParse() {
        assertEquals(60, AfkZoneCommand.parseSeconds("60"));
        assertEquals(60, AfkZoneCommand.parseSeconds("1m"));
        assertEquals(150, AfkZoneCommand.parseSeconds("2m30s"));
        assertEquals(3600, AfkZoneCommand.parseSeconds("1h"));
        assertEquals(-1, AfkZoneCommand.parseSeconds("1x"));
        assertEquals(-1, AfkZoneCommand.parseSeconds("abc"));
        assertEquals(-1, AfkZoneCommand.parseSeconds(""));
    }

    @Test
    void bundledAfkConfigHasSaneDefaults() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStream input = AfkTest.class.getClassLoader().getResourceAsStream("afk.yml")) {
            assertNotNull(input);
            yaml.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
        assertEquals(300, yaml.getInt("STATUS.IDLE-SECONDS"));
        assertEquals(0, yaml.getInt("IDLE-ACTIONS.KICK-SECONDS"), "kicking is opt-in");
        assertEquals(0, yaml.getInt("IDLE-ACTIONS.SEND-TO-ZONE-SECONDS"), "moving players is opt-in");
        assertEquals("BOSSBAR", yaml.getString("DISPLAY.MODE"));
        assertTrue(yaml.isConfigurationSection("ZONES"));
        assertFalse(yaml.contains("MIGRATED"), "a fresh server migrates once and records it itself");
    }
}
