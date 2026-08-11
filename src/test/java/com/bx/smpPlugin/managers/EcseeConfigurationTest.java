package com.bx.smpPlugin.managers;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EcseeConfigurationTest {

    @Test
    void bundledCommandPermissionAndSettingsArePresent() throws Exception {
        YamlConfiguration plugin = new YamlConfiguration();
        plugin.load(new File("src/main/resources/plugin.yml"));

        assertTrue(plugin.isConfigurationSection("commands.ecsee"));
        assertEquals("/ecsee <player>", plugin.getString("commands.ecsee.usage"));
        assertTrue(plugin.getBoolean(
                "permissions.smpplugin.admin.children.smpplugin.admin.ecsee"
        ));
        assertEquals(
                "op",
                plugin.getString("permissions.smpplugin.admin.ecsee.default")
        );

        YamlConfiguration enderChest = new YamlConfiguration();
        enderChest.load(new File("src/main/resources/ender-chest.yml"));

        assertTrue(enderChest.getBoolean("ENDER-CHEST.ECSEE.ENABLED"));
        assertEquals(
                "smpplugin.admin.ecsee",
                enderChest.getString("ENDER-CHEST.ECSEE.PERMISSION")
        );
        assertTrue(enderChest.getLong("ENDER-CHEST.ECSEE.AUTO-REFRESH-TICKS") > 0L);
    }
}
