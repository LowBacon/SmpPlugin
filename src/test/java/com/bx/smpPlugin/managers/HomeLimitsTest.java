// SmpPlugin by LowBacon
package com.bx.smpPlugin.managers;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomeLimitsTest {

    private static final long HOUR = 3600L;

    private static ConfigurationSection bundledLimits() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));
        ConfigurationSection limits = config.getConfigurationSection("SETTINGS.HOME-LIMITS");
        assertNotNull(limits, "config.yml has no SETTINGS.HOME-LIMITS");
        return limits;
    }

    @Test
    void playersGainOneHomePerPlaytimeStepUpToTheirMax() {
        HomeManager.HomeTier tier = HomeManager.HomeTier.read(bundledLimits(), "default");
        assertEquals(3, tier.homesFor(0L, 10 * HOUR));
        assertEquals(3, tier.homesFor(9 * HOUR, 10 * HOUR));
        assertEquals(4, tier.homesFor(10 * HOUR, 10 * HOUR));
        assertEquals(5, tier.homesFor(20 * HOUR, 10 * HOUR));
        assertEquals(5, tier.homesFor(5000 * HOUR, 10 * HOUR));
    }

    @Test
    void bundledRanksMatchTheAgreedRanges() {
        ConfigurationSection limits = bundledLimits();
        assertRange(limits, "default", 3, 5);
        assertRange(limits, "vip", 5, 10);
        assertRange(limits, "vipplus", 10, 20);
        for (String unlimited : new String[]{"owner", "coowner", "developer", "manager", "op"}) {
            assertTrue(HomeManager.HomeTier.read(limits, unlimited).unlimited(), unlimited + " should have unlimited homes");
            assertEquals(HomeManager.UNLIMITED, HomeManager.HomeTier.read(limits, unlimited).homesFor(0L, HOUR));
        }
    }

    @Test
    void staffRanksGetMoreThanVipPlus() {
        ConfigurationSection limits = bundledLimits();
        int vipPlusMax = HomeManager.HomeTier.read(limits, "vipplus").max();
        for (String staff : new String[]{"helper", "moderator", "admin"}) {
            assertTrue(HomeManager.HomeTier.read(limits, staff).max() > vipPlusMax, staff);
        }
    }

    @Test
    void aPlainNumberIsAFixedLimitAndNoStepGivesTheMax() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("legacy", 7);
        yaml.set("ranged.MIN", 2);
        yaml.set("ranged.MAX", 6);
        yaml.set("broken.MIN", 9);
        yaml.set("broken.MAX", 3);
        assertEquals(7, HomeManager.HomeTier.read(yaml, "legacy").homesFor(999 * HOUR, HOUR));
        assertEquals(6, HomeManager.HomeTier.read(yaml, "ranged").homesFor(0L, 0L));
        // MAX below MIN is read as MIN.
        assertEquals(9, HomeManager.HomeTier.read(yaml, "broken").homesFor(999 * HOUR, HOUR));
        assertNull(HomeManager.HomeTier.read(yaml, "missing"));
    }

    private static void assertRange(ConfigurationSection limits, String rank, int min, int max) {
        HomeManager.HomeTier tier = HomeManager.HomeTier.read(limits, rank);
        assertEquals(min, tier.min(), rank + " MIN");
        assertEquals(max, tier.max(), rank + " MAX");
    }
}
