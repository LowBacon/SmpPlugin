package com.bx.smpPlugin.platform;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only inspection of Paper's native anti-Xray settings.
 *
 * <p>Paper keeps anti-Xray in {@code config/paper-world-defaults.yml} under
 * {@code anticheat.anti-xray}, with optional per-world overrides in {@code paper-world.yml}.
 * Changes there need a full server restart. This class never writes either file.</p>
 */
public final class NativeAntiXrayInspector {

    static final String SECTION = "anticheat.anti-xray";
    static final String DEFAULTS_FILE = "paper-world-defaults.yml";
    static final String WORLD_FILE = "paper-world.yml";

    /**
     * Anti-Xray state for one world, after applying its override on top of the defaults.
     *
     * @param worldName       world name, or {@code "(defaults)"}
     * @param enabled         whether anti-Xray is on
     * @param engineMode      1, 2 or 3; 0 when unknown
     * @param maxBlockHeight  highest Y covered; {@code Integer.MIN_VALUE} when unknown
     * @param hiddenBlocks    number of configured hidden block types; -1 when unknown
     * @param usePermission   whether {@code paper.antixray.bypass} is honoured
     * @param hasWorldOverride whether a per-world file changed any anti-Xray value
     */
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

    /**
     * @param defaultsFound whether {@code paper-world-defaults.yml} was found
     * @param defaults      the values from the defaults file
     * @param worlds        effective values for each loaded world
     */
    public record Report(boolean defaultsFound, WorldStatus defaults, List<WorldStatus> worlds) {
    }

    private NativeAntiXrayInspector() {
    }

    /**
     * @param paperConfigDirectory directory holding {@code paper-world-defaults.yml}
     * @param worldFolders         loaded world name → world folder
     */
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

    /** Paper writes {@code default} in per-world files to mean "inherit"; treat that like a missing key. */
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
