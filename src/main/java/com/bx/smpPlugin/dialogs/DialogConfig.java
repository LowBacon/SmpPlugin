package com.bx.smpPlugin.dialogs;

import com.bx.smpPlugin.SmpPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed reads over {@code dialog.yml}.
 *
 * <p>Every getter takes the dialog's root path so a screen never has to hard-code the layout of
 * the file, and every string goes through {@link #apply(String, Map)} so {@code %target%} style
 * tokens in labels, titles and action ids resolve the same way everywhere.
 */
public final class DialogConfig {

    /** A button as written in the config, before it becomes an Adventure action button. */
    public record ButtonSpec(String label, String tooltip, int width, String action, String command) {

        public boolean hasAction() {
            return action != null && !action.isBlank();
        }

        public boolean hasCommand() {
            return command != null && !command.isBlank();
        }
    }

    /** A text or dropdown input as written in the config. */
    public record InputSpec(
            String id,
            String label,
            int width,
            int maxLength,
            String initial,
            List<OptionSpec> options
    ) {
        public boolean isDropdown() {
            return options != null && !options.isEmpty();
        }
    }

    /** One entry of a dropdown input. */
    public record OptionSpec(String id, String label, boolean isDefault) {
    }

    private static final int DEFAULT_BUTTON_WIDTH = 150;
    private static final int MIN_WIDTH = 1;
    private static final int MAX_WIDTH = 1024;

    private final SmpPlugin plugin;

    public DialogConfig(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration config() {
        return plugin.getConfigManager().getDialog();
    }

    /** Master switch for the whole dialog interface. */
    public boolean isEnabled() {
        return DialogSupport.isAvailable() && config().getBoolean("ENABLED", true);
    }

    /**
     * Per-screen switch. A server owner can send one command back to its chest menu without
     * losing the rest of the dialog interface.
     */
    public boolean isScreenEnabled(String screen) {
        return isEnabled() && config().getBoolean("SCREENS." + screen, true);
    }

    public ConfigurationSection section(String path) {
        return config().getConfigurationSection(path);
    }

    public String string(String path, String fallback) {
        String value = config().getString(path);
        return value == null ? fallback : value;
    }

    public String string(String path, String fallback, Map<String, String> tokens) {
        return apply(string(path, fallback), tokens);
    }

    public int integer(String path, int fallback) {
        return config().getInt(path, fallback);
    }

    public boolean bool(String path, boolean fallback) {
        return config().getBoolean(path, fallback);
    }

    public List<String> stringList(String path) {
        return config().getStringList(path);
    }

    public List<Integer> integerList(String path) {
        return config().getIntegerList(path);
    }

    public int columns(String path, int fallback) {
        return clamp(config().getInt(path + ".COLUMNS", fallback), 1, 8);
    }

    public int buttonWidth(String path) {
        return buttonWidth(path, DEFAULT_BUTTON_WIDTH);
    }

    public int buttonWidth(String path, int fallback) {
        return clampWidth(config().getInt(path + ".BUTTON-WIDTH", fallback));
    }

    /** Reads {@code <path>.BUTTONS}, dropping malformed entries rather than failing the screen. */
    public List<ButtonSpec> buttonsOf(String path, Map<String, String> tokens) {
        return buttons(path + ".BUTTONS", tokens, buttonWidth(path));
    }

    /** Reads an arbitrary button list such as {@code ....BUTTONS-10}. */
    public List<ButtonSpec> buttons(String listPath, Map<String, String> tokens, int defaultWidth) {
        return buttonsFrom(config().getMapList(listPath), tokens, defaultWidth);
    }

    /**
     * Reads buttons out of a list already pulled from the config. Nested lists — a category's
     * buttons, say — cannot be addressed by path, so they arrive here as raw maps.
     */
    public List<ButtonSpec> buttonsFrom(List<?> raw, Map<String, String> tokens, int defaultWidth) {
        List<ButtonSpec> specs = new ArrayList<>();
        if (raw == null) {
            return specs;
        }
        for (Object element : raw) {
            if (!(element instanceof Map<?, ?> entry)) {
                continue;
            }
            ButtonSpec spec = toButton(entry, tokens, defaultWidth);
            if (spec != null) {
                specs.add(spec);
            }
        }
        return specs;
    }

    private ButtonSpec toButton(Map<?, ?> entry, Map<String, String> tokens, int defaultWidth) {
        String label = apply(str(entry, "LABEL"), tokens);
        if (label == null) {
            return null;
        }
        return new ButtonSpec(
                label,
                apply(str(entry, "TOOLTIP"), tokens),
                clampWidth(intOr(entry, "WIDTH", defaultWidth)),
                apply(str(entry, "ACTION"), tokens),
                apply(str(entry, "COMMAND"), tokens)
        );
    }

    /** Reads {@code <path>.INPUTS}. */
    public List<InputSpec> inputs(String path, Map<String, String> tokens) {
        List<InputSpec> specs = new ArrayList<>();
        for (Map<?, ?> entry : config().getMapList(path + ".INPUTS")) {
            String id = str(entry, "ID");
            if (id == null || id.isBlank()) {
                continue;
            }
            specs.add(new InputSpec(
                    id,
                    apply(str(entry, "LABEL"), tokens),
                    clampWidth(intOr(entry, "WIDTH", 200)),
                    Math.max(1, intOr(entry, "MAX-LENGTH", 32)),
                    apply(str(entry, "INITIAL"), tokens),
                    options(entry, tokens)
            ));
        }
        return specs;
    }

    private List<OptionSpec> options(Map<?, ?> entry, Map<String, String> tokens) {
        Object raw = entry.get("OPTIONS");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<OptionSpec> options = new ArrayList<>();
        for (Object element : list) {
            if (!(element instanceof Map<?, ?> option)) {
                continue;
            }
            String id = str(option, "ID");
            if (id == null || id.isBlank()) {
                continue;
            }
            options.add(new OptionSpec(id, apply(str(option, "LABEL"), tokens), boolOr(option, "DEFAULT", false)));
        }
        return options;
    }

    /** Replaces {@code %token%} occurrences; a null map leaves the text untouched. */
    public static String apply(String text, Map<String, String> tokens) {
        if (text == null || tokens == null || tokens.isEmpty() || text.indexOf('%') < 0) {
            return text;
        }
        String result = text;
        for (Map.Entry<String, String> token : tokens.entrySet()) {
            result = result.replace('%' + token.getKey() + '%', token.getValue());
        }
        return result;
    }

    /** Convenience builder so screens can write {@code tokens("target", name)} inline. */
    public static Map<String, String> tokens(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            map.put(pairs[index], pairs[index + 1] == null ? "" : pairs[index + 1]);
        }
        return map;
    }

    public static int clampWidth(int width) {
        return clamp(width, MIN_WIDTH, MAX_WIDTH);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String str(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static int intOr(Map<?, ?> map, String key, int fallback) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static boolean boolOr(Map<?, ?> map, String key, boolean fallback) {
        Object value = map.get(key);
        if (value instanceof Boolean flag) {
            return flag;
        }
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }
}
