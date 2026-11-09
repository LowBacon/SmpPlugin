// SmpPlugin by LowBacon
package com.bx.smpPlugin.dialogs;

import com.bx.smpPlugin.SmpPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.logging.Level;

/** Creates the tiny datapack needed to add Custom SMP to Minecraft's pause screen. */
public final class PauseScreenDatapack {
    private static final String PACK_NAME = "smpplugin_custom_smp";
    private static final String DEFAULT_KEY = "smpplugin:custom_smp";

    private PauseScreenDatapack() { }

    public static void writeBeforeWorldLoad(SmpPlugin plugin) {
        try {
            if (!DialogSupport.isAvailable()) return;
        } catch (LinkageError unavailableOnThisServer) {
            return;
        }
        File file = new File(plugin.getDataFolder(), "dialog.yml");
        FileConfiguration config = file.isFile() ? YamlConfiguration.loadConfiguration(file) : bundled(plugin);
        if (!config.getBoolean("PAUSE-SCREEN.ENABLED", true)) return;

        String key = safeKey(config.getString("PAUSE-SCREEN.KEY", DEFAULT_KEY));
        String[] parts = key.split(":", 2);
        String label = json(config.getString("PAUSE-SCREEN.BUTTON-LABEL", "Custom SMP"));
        int packFormat = config.getInt("PAUSE-SCREEN.PACK-FORMAT", 80);
        File menusFile = new File(plugin.getDataFolder(), "menus.yml");
        FileConfiguration menus;
        if (menusFile.isFile()) {
            menus = YamlConfiguration.loadConfiguration(menusFile);
        } else {
            try (var stream = plugin.getResource("menus.yml")) {
                menus = stream == null ? new YamlConfiguration() : YamlConfiguration.loadConfiguration(
                        new java.io.InputStreamReader(stream, StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                menus = new YamlConfiguration();
            }
        }
        Path root = Bukkit.getWorldContainer().toPath().resolve(levelName()).resolve("datapacks").resolve(PACK_NAME);
        try {
            write(root.resolve("pack.mcmeta"), "{\"pack\":{\"description\":\"SmpPlugin Custom SMP menu\",\"pack_format\":"
                    + packFormat + ",\"min_format\":1,\"max_format\":9999,\"supported_formats\":{\"min_inclusive\":1,\"max_inclusive\":9999,\"min_format\":1,\"max_format\":9999}}}");
            write(root.resolve("data").resolve(parts[0]).resolve("dialog").resolve(parts[1] + ".json"),
                    pauseDialog(label, menus));
            write(root.resolve("data/minecraft/tags/dialog/pause_screen_additions.json"),
                    "{\"replace\":false,\"values\":[{\"id\":\"" + key + "\",\"required\":false}]}");
            plugin.getLogger().info("Prepared the Custom SMP pause-screen datapack in " + root + ".");
        } catch (IOException | RuntimeException error) {
            plugin.getLogger().log(Level.WARNING, "Could not prepare the Custom SMP pause-screen datapack", error);
        }
    }

    private static String pauseDialog(String label, FileConfiguration menus) {
        StringBuilder json = new StringBuilder("{\"type\":\"minecraft:multi_action\",\"title\":{\"text\":\"")
                .append(label).append("\"},\"external_title\":{\"text\":\"").append(label)
                .append("\"},\"can_close_with_escape\":true,\"pause\":false,\"after_action\":\"none\",\"columns\":2,\"actions\":[");
        ConfigurationSection buttons = menus.getConfigurationSection("MAIN-MENU.BUTTONS");
        boolean first = true;
        if (buttons != null) {
            int index = 0;
            for (String key : buttons.getKeys(false)) {
                ConfigurationSection button = buttons.getConfigurationSection(key);
                int buttonIndex = index++;
                if (button == null || button.getString("COMMAND", "").isBlank()) continue;
                if (!first) json.append(',');
                first = false;
                String buttonLabel = json(button.getString("NAME", key).replaceAll("&[0-9a-fk-or]", ""));
                json.append("{\"label\":{\"text\":\"").append(buttonLabel)
                        .append("\"},\"width\":150,\"action\":{\"type\":\"minecraft:custom\",\"id\":\"smpplugin:menucmd_")
                        .append(buttonIndex)
                        .append("\"}}");
            }
        }
        return json.append("]}").toString();
    }

    private static FileConfiguration bundled(SmpPlugin plugin) {
        try (var stream = plugin.getResource("dialog.yml")) {
            return stream == null ? new YamlConfiguration() : YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            return new YamlConfiguration();
        }
    }

    private static String levelName() {
        Path properties = Path.of("server.properties");
        if (Files.isRegularFile(properties)) {
            Properties values = new Properties();
            try (var reader = Files.newBufferedReader(properties, StandardCharsets.UTF_8)) {
                values.load(reader);
                String name = values.getProperty("level-name");
                if (name != null && !name.isBlank()) return name.trim();
            } catch (IOException ignored) { }
        }
        return "world";
    }

    private static String safeKey(String value) {
        if (value == null || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) return DEFAULT_KEY;
        String path = value.substring(value.indexOf(':') + 1);
        for (String part : path.split("/")) {
            if (part.isBlank() || part.equals(".") || part.equals("..")) return DEFAULT_KEY;
        }
        return value;
    }

    private static String json(String value) {
        return (value == null ? "Custom SMP" : value).replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        String body = content + System.lineSeparator();
        if (!Files.exists(path) || !Files.readString(path, StandardCharsets.UTF_8).equals(body)) {
            Files.writeString(path, body, StandardCharsets.UTF_8);
        }
    }
}
