// SmpPlugin by LowBacon
package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.managers.FeatureManager;
import com.bx.smpPlugin.permissions.PermissionNode;
import com.bx.smpPlugin.permissions.PermissionRegistry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// docs/commands.md is generated from plugin.yml, the /help categories and the permission registry,
// so it cannot drift. Regenerate with: mvn test -Dtest=CommandReferenceTest -Dsmpplugin.regenerate=true
class CommandReferenceTest {

    private static final Path DOCS = Path.of("docs/commands.md");
    private static final boolean REGENERATE = Boolean.getBoolean("smpplugin.regenerate");

    @Test
    void commandReferenceIsGeneratedFromPluginYml() throws IOException {
        String expected = render();
        if (REGENERATE) {
            Files.writeString(DOCS, expected, StandardCharsets.UTF_8);
            return;
        }
        assertTrue(Files.exists(DOCS), "docs/commands.md is missing; regenerate it");
        assertEquals(expected, Files.readString(DOCS, StandardCharsets.UTF_8).replace("\r\n", "\n"),
                "docs/commands.md is stale; run mvn test -Dtest=CommandReferenceTest -Dsmpplugin.regenerate=true");
    }

    static String render() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File("src/main/resources/plugin.yml"));
        ConfigurationSection section = yaml.getConfigurationSection("commands");
        Map<String, Map<String, Object>> commands = new LinkedHashMap<>();
        for (String name : section.getKeys(false)) {
            ConfigurationSection details = section.getConfigurationSection(name);
            commands.put(name, details == null ? Map.of() : details.getValues(false));
        }
        Map<String, PermissionNode> nodes = PermissionRegistry.all().stream()
                .collect(java.util.stream.Collectors.toMap(PermissionNode::node, node -> node, (a, b) -> a));

        StringBuilder out = new StringBuilder();
        out.append("# SmpPlugin commands\n\n");
        out.append("Generated from `plugin.yml` by `CommandReferenceTest`; do not edit by hand. ")
                .append(commands.size()).append(" commands.\n\n");
        out.append("- **Usage:** `<value>` is required, `[value]` is optional, `a|b` means pick one.\n");
        out.append("- **Permission / default:** the node checked before the command runs and who has it out of the box ")
                .append("(`everyone`, `op` or `nobody` until a rank is given it). Commands without a node are open to everyone; ")
                .append("their staff-only parts check their own permissions (see `docs/permissions-reference.md`).\n");
        out.append("- **Feature:** the `/smpplugin features` switch that turns the command off. A switched-off command ")
                .append("says so and does nothing, and `/help` leaves it out.\n");
        out.append("- `/help <category>` and `/help <command>` show the same information in game, filtered to what the reader may use.\n\n");

        Map<CommandCatalog.Category, List<CommandCatalog.Entry>> grouped =
                CommandCatalog.byCategory(CommandCatalog.entries(commands));
        for (CommandCatalog.Category category : CommandCatalog.Category.values()) {
            List<CommandCatalog.Entry> entries = grouped.getOrDefault(category, List.of());
            if (entries.isEmpty()) {
                continue;
            }
            out.append("## ").append(category.title()).append(" (`/help ").append(category.key()).append("`)\n\n");
            out.append(category.description()).append(".\n\n");
            out.append("| Command | Aliases | Usage | Description | Permission | Default | Feature |\n");
            out.append("|---|---|---|---|---|---|---|\n");
            for (CommandCatalog.Entry entry : entries) {
                out.append("| `/").append(entry.name()).append("` | ")
                        .append(entry.aliases().isEmpty() ? "" : "`/" + String.join("` `/", entry.aliases()) + "`").append(" | ")
                        .append(code(entry.usage())).append(" | ")
                        .append(cell(entry.description())).append(" | ")
                        .append(permissions(entry)).append(" | ")
                        .append(defaults(entry, nodes)).append(" | ")
                        .append(features(entry.name())).append(" |\n");
            }
            out.append('\n');
        }
        return out.toString();
    }

    private static String permissions(CommandCatalog.Entry entry) {
        List<String> nodes = new ArrayList<>();
        if (!entry.permission().isBlank()) {
            nodes.addAll(List.of(entry.permission().split(";")));
        } else {
            nodes.addAll(CommandCatalog.viewPermissions(entry.name()));
        }
        return nodes.isEmpty() ? "" : "`" + String.join("` `", nodes) + "`";
    }

    private static String defaults(CommandCatalog.Entry entry, Map<String, PermissionNode> nodes) {
        String first = entry.permission().isBlank()
                ? CommandCatalog.viewPermissions(entry.name()).stream().findFirst().orElse("")
                : entry.permission().split(";")[0];
        if (first.isBlank()) {
            return "everyone";
        }
        PermissionNode node = nodes.get(first);
        if (node == null) {
            return "?";
        }
        return switch (node.defaultValue()) {
            case TRUE -> "everyone";
            case OP -> "op";
            case NOT_OP -> "non-op";
            default -> "nobody";
        };
    }

    private static String features(String command) {
        FeatureManager.Feature[] features = FeatureManager.featuresForCommand(command);
        if (features == null || features.length == 0) {
            return "";
        }
        List<String> keys = new ArrayList<>();
        for (FeatureManager.Feature feature : features) {
            keys.add(feature.configKey().toLowerCase(Locale.ROOT));
        }
        return String.join(", ", keys);
    }

    private static String code(String text) {
        return "`" + text.replace("|", "\\|").replace("`", "'") + "`";
    }

    private static String cell(String text) {
        return text.replace("|", "\\|").replace("\n", " ");
    }
}
