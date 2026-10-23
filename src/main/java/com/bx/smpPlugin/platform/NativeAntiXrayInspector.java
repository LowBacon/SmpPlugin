// SmpPlugin by LowBacon
package com.bx.smpPlugin.platform;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NativeAntiXrayInspector {

    static final String SECTION = "anticheat.anti-xray";
    static final String DEFAULTS_FILE = "paper-world-defaults.yml";
    static final String WORLD_FILE = "paper-world.yml";

    public record WorldStatus(
            String worldName,
            boolean enabled,
            int engineMode,
            int maxBlockHeight,
            int hiddenBlocks,
            boolean usePermission,
            boolean hasWorldOverride
    ) {
    }

    public record Report(boolean defaultsFound, WorldStatus defaults, List<WorldStatus> worlds) {
    }

    private NativeAntiXrayInspector() {
    }

    public static Report inspect(File paperConfigDirectory, Map<String, File> worldFolders) {
        File defaultsFile = new File(paperConfigDirectory, DEFAULTS_FILE);
        YamlConfiguration defaultsYaml = defaultsFile.isFile() ? YamlConfiguration.loadConfiguration(defaultsFile) : null;
        ConfigurationSection defaultsSection = defaultsYaml == null ? null : defaultsYaml.getConfigurationSection(SECTION);
        WorldStatus defaults = read("(defaults)", defaultsSection, null);

        List<WorldStatus> worlds = new ArrayList<>();
        for (Map.Entry<String, File> world : new LinkedHashMap<>(worldFolders).entrySet()) {
            File overrideFile = new File(world.getValue(), WORLD_FILE);
            ConfigurationSection override = null;
            if (overrideFile.isFile()) {
                override = YamlConfiguration.loadConfiguration(overrideFile).getConfigurationSection(SECTION);
            }
            worlds.add(read(world.getKey(), defaultsSection, override));
        }
        return new Report(defaultsYaml != null, defaults, List.copyOf(worlds));
    }

    private static WorldStatus read(String worldName, ConfigurationSection defaults, ConfigurationSection override) {
        boolean overridden = false;

        Object enabled = value("enabled", defaults, override);
        Object engineMode = value("engine-mode", defaults, override);
        Object maxHeight = value("max-block-height", defaults, override);
        Object usePermission = value("use-permission", defaults, override);
        Object hiddenBlocks = value("hidden-blocks", defaults, override);

        if (override != null) {
            for (String key : List.of("enabled", "engine-mode", "max-block-height", "use-permission", "hidden-blocks")) {
                Object raw = override.get(key);
                if (raw != null && !"default".equals(String.valueOf(raw))) {
                    overridden = true;
                    break;
                }
            }
        }

        return new WorldStatus(
                worldName,
                Boolean.parseBoolean(String.valueOf(enabled)),
                asInt(engineMode, 0),
                asInt(maxHeight, Integer.MIN_VALUE),
                hiddenBlocks instanceof List<?> list ? list.size() : -1,
                Boolean.parseBoolean(String.valueOf(usePermission)),
                overridden
        );
    }

    private static Object value(String key, ConfigurationSection defaults, ConfigurationSection override) {
        if (override != null) {
            Object raw = override.get(key);
            if (raw != null && !"default".equals(String.valueOf(raw))) {
                return raw;
            }
        }
        return defaults == null ? null : defaults.get(key);
    }

    private static int asInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
