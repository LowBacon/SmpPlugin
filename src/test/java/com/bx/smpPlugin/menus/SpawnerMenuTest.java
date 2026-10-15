package com.bx.smpPlugin.menus;

import com.bx.smpPlugin.models.SpawnerInstance;
import com.bx.smpPlugin.models.SpawnerTypeDefinition;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnerMenuTest {

    @Test
    void xpCurveMatchesVanilla() {
        // Each level's total is the previous total plus the cost of the level before it.
        for (int level = 1; level <= 60; level++) {
            assertEquals(SpawnerMenu.totalXpAtLevel(level - 1) + SpawnerMenu.xpToNextLevel(level - 1),
                    SpawnerMenu.totalXpAtLevel(level), 1e-9, "level " + level);
        }
        assertEquals(1395D, SpawnerMenu.totalXpAtLevel(30), 1e-9);
        assertEquals(7, SpawnerMenu.xpToNextLevel(0));
        assertEquals(112, SpawnerMenu.xpToNextLevel(30));
    }

    @Test
    void worldNamesAreCapitalised() {
        assertEquals("The End", SpawnerWorldListMenu.capitalize("the end"));
        assertEquals("Overworld", SpawnerWorldListMenu.capitalize("overworld"));
    }

    @Test
    void defaultXpFollowsMobKind() {
        assertEquals(2D, SpawnerTypeDefinition.defaultXpPerCycle(EntityType.PIG));
        assertEquals(5D, SpawnerTypeDefinition.defaultXpPerCycle(EntityType.ZOMBIE));
        assertEquals(10D, SpawnerTypeDefinition.defaultXpPerCycle(EntityType.BLAZE));
        assertEquals(0D, SpawnerTypeDefinition.defaultXpPerCycle(EntityType.IRON_GOLEM));
    }

    @Test
    void storedXpRespectsCap() {
        SpawnerInstance instance = new SpawnerInstance(1L, "world", 0, 64, 0, UUID.randomUUID(), "owner",
                "PIG", 1L, SpawnerInstance.AccessMode.OWNER_ONLY, 0L, 0L, 0L);
        instance.addStoredXp(80L, 100L);
        instance.addStoredXp(80L, 100L);
        assertEquals(100L, instance.getStoredXp());
        instance.addStoredXp(Long.MAX_VALUE, 0L);
        assertEquals(Long.MAX_VALUE, instance.getStoredXp(), "an uncapped store saturates instead of overflowing");
        instance.addLifetimeEarnings(Double.NaN);
        instance.addLifetimeEarnings(12.5D);
        assertEquals(12.5D, instance.getLifetimeEarnings());
    }

    @Test
    void bundledSpawnerConfigHasTheNewOptions() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStream input = SpawnerMenuTest.class.getClassLoader().getResourceAsStream("spawners.yml")) {
            assertNotNull(input);
            yaml.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
        assertTrue(yaml.getBoolean("GUI.MAIN.ENABLED"));
        assertTrue(yaml.getBoolean("SETTINGS.STORE_XP"));
        assertTrue(yaml.getBoolean("SETTINGS.PROTECT_STORED_LOOT_ON_BREAK"));
        for (String type : yaml.getConfigurationSection("TYPES").getKeys(false)) {
            assertTrue(yaml.contains("TYPES." + type + ".XP_PER_CYCLE"), type + " sets XP_PER_CYCLE");
        }
    }

    @Test
    void bundledRtpMenuHasThreeWorlds() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStream input = SpawnerMenuTest.class.getClassLoader().getResourceAsStream("rtp.yml")) {
            assertNotNull(input);
            yaml.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
        assertTrue(yaml.getBoolean("RTP-MENU.OPEN-ON-COMMAND"));
        assertEquals("world", yaml.getString("RTP-MENU.BUTTONS.OVERWORLD.WORLD"));
        assertEquals("world_nether", yaml.getString("RTP-MENU.BUTTONS.NETHER.WORLD"));
        assertEquals("world_the_end", yaml.getString("RTP-MENU.BUTTONS.THE_END.WORLD"));
        int size = yaml.getInt("RTP-MENU.SIZE");
        for (String key : new String[]{"OVERWORLD", "NETHER", "THE_END"}) {
            int slot = yaml.getInt("RTP-MENU.BUTTONS." + key + ".SLOT");
            assertTrue(slot >= 9 && slot < size - 9, key + " sits between the border rows");
        }
    }
}
