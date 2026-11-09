// SmpPlugin by LowBacon
package com.bx.smpPlugin.worlds;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PvpAreaTest {

    @Test
    void ringCoversOnlyTheBandAroundItsCentre() {
        PvpArea ring = PvpArea.parse("ring 62 96 0 0").orElseThrow();
        assertFalse(ring.contains(0, 0), "the town in the middle is safe");
        assertFalse(ring.contains(60, 0));
        assertTrue(ring.contains(62.5, 0));
        assertTrue(ring.contains(0, -90));
        assertTrue(ring.contains(50, 50));
        assertFalse(ring.contains(97, 0), "the stands outside are safe");
    }

    @Test
    void boxIncludesItsEdgeBlocks() {
        PvpArea box = PvpArea.parse("box 10 10 -10 -10").orElseThrow();
        assertTrue(box.contains(-10, -10));
        assertTrue(box.contains(10.9, 10.9));
        assertFalse(box.contains(11.1, 0));
    }

    @Test
    void brokenLinesAreIgnored() {
        assertTrue(PvpArea.parse("ring 96 62").isEmpty());
        assertTrue(PvpArea.parse("ring NaN 5").isEmpty());
        assertTrue(PvpArea.parse("circle 1 2").isEmpty());
        assertTrue(PvpArea.parse("box 1 2 3").isEmpty());
        assertEquals(1, PvpArea.parseAll(List.of("nonsense", "ring 1 2 0 0")).size());
    }

    @Test
    void fightersMustBothStandInTheArena() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("PVP", false);
        yaml.set("PVP-AREAS", List.of("ring 62 96 0 0"));
        WorldRules rules = WorldRules.read("spawn", yaml);
        assertTrue(rules.pvpAllowedBetween(70, 0, 75, 0));
        assertFalse(rules.pvpAllowedBetween(10, 0, 75, 0), "no hitting into the arena from the safe town");
        assertFalse(rules.pvpAllowedBetween(75, 0, 10, 0), "no chasing into the town");
        assertFalse(WorldRules.read("spawn", new YamlConfiguration()).pvpAllowedBetween(70, 0, 75, 0));
    }
}
