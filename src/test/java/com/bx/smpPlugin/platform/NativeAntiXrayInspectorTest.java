// SmpPlugin by LowBacon
package com.bx.smpPlugin.platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeAntiXrayInspectorTest {

    private static final String DEFAULTS = """
            anticheat:
              anti-xray:
                enabled: true
                engine-mode: 2
                max-block-height: 64
                use-permission: false
                hidden-blocks:
                - diamond_ore
                - deepslate_diamond_ore
                - ancient_debris
            """;

    @Test
    void reportsMissingDefaultsFile(@TempDir Path config) {
        NativeAntiXrayInspector.Report report = NativeAntiXrayInspector.inspect(config.toFile(), Map.of());
        assertFalse(report.defaultsFound());
    }

    @Test
    void appliesPerWorldOverridesOnTopOfDefaults(@TempDir Path root) throws Exception {
        Path config = Files.createDirectories(root.resolve("config"));
        Files.writeString(config.resolve(NativeAntiXrayInspector.DEFAULTS_FILE), DEFAULTS);

        Path overworld = Files.createDirectories(root.resolve("world"));
        Path nether = Files.createDirectories(root.resolve("world_nether"));
        Files.writeString(nether.resolve(NativeAntiXrayInspector.WORLD_FILE), """
                anticheat:
                  anti-xray:
                    enabled: false
                    engine-mode: default
                """);

        Map<String, File> worlds = new LinkedHashMap<>();
        worlds.put("world", overworld.toFile());
        worlds.put("world_nether", nether.toFile());
        NativeAntiXrayInspector.Report report = NativeAntiXrayInspector.inspect(config.toFile(), worlds);

        assertTrue(report.defaultsFound());
        NativeAntiXrayInspector.WorldStatus world = report.worlds().get(0);
        assertTrue(world.enabled());
        assertEquals(2, world.engineMode());
        assertEquals(64, world.maxBlockHeight());
        assertEquals(3, world.hiddenBlocks());
        assertFalse(world.hasWorldOverride());

        NativeAntiXrayInspector.WorldStatus netherStatus = report.worlds().get(1);
        assertFalse(netherStatus.enabled());
        assertEquals(2, netherStatus.engineMode(), "'default' inherits the defaults file");
        assertTrue(netherStatus.hasWorldOverride());
    }
}
