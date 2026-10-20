package com.bx.smpPlugin.permissions;

import org.bukkit.permissions.PermissionDefault;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Renders {@link PermissionRegistry} as plugin.yml YAML and as Markdown documentation. */
public final class PermissionRenderer {

    public static final String YAML_BEGIN = "# BEGIN GENERATED PERMISSIONS - edit PermissionRegistry.java, not this section";
    public static final String YAML_END = "# END GENERATED PERMISSIONS";

    private static final Map<PermissionCategory, String> CATEGORY_TITLES = categoryTitles();

    private PermissionRenderer() {
    }

    private static Map<PermissionCategory, String> categoryTitles() {
        Map<PermissionCategory, String> titles = new EnumMap<>(PermissionCategory.class);
        titles.put(PermissionCategory.PLAYER, "Player features (default: everyone)");
        titles.put(PermissionCategory.PERK, "Rank perks (default: nobody)");
        titles.put(PermissionCategory.STAFF, "Staff tools (default: nobody; ops via smpplugin.admin)");
        titles.put(PermissionCategory.ADMIN, "Administration (default: nobody; ops via smpplugin.admin)");
        titles.put(PermissionCategory.OPERATIONAL_BYPASS, "Operational bypasses (default: nobody; ops via smpplugin.admin)");
        titles.put(PermissionCategory.SECURITY_BYPASS, "Security and privacy bypasses (default: nobody; never bundled)");
        titles.put(PermissionCategory.DESTRUCTIVE, "Destructive actions (default: nobody; never bundled)");
        titles.put(PermissionCategory.SENIOR_STAFF, "Senior staff (default: nobody, but always allowed for ops; only the manager, developer, co-owner and owner roles)");
        titles.put(PermissionCategory.BUNDLE, "Bundles");
        titles.put(PermissionCategory.LEGACY, "Legacy names and bundles");
        return titles;
    }

    /** The {@code permissions:} block for plugin.yml, including the marker comments. */
    public static String renderPluginYml(List<PermissionNode> nodes) {
        StringBuilder yaml = new StringBuilder();
        yaml.append(YAML_BEGIN).append('\n');
        yaml.append("permissions:\n");
        for (PermissionNode node : nodes) {
            yaml.append("  ").append(node.node()).append(":\n");
            yaml.append("    description: ").append(quote(node.description())).append('\n');
            yaml.append("    default: ").append(yamlDefault(node.defaultValue())).append('\n');
            if (!node.children().isEmpty()) {
                yaml.append("    children:\n");
                for (String child : node.children()) {
                    yaml.append("      ").append(child).append(": true\n");
                }
            }
        }
        yaml.append(YAML_END).append('\n');
        return yaml.toString();
    }

    public static String renderMarkdown(List<PermissionNode> nodes, List<PermissionRegistry.NumberedPermission> numbered) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# SmpPlugin permissions reference\n\n");
        markdown.append("> Generated from `PermissionRegistry.java`. Do not edit by hand; run\n");
        markdown.append("> `mvn test -Dtest=PermissionRegistryTest -Dsmpplugin.regenerate=true` after changing the registry.\n\n");
        markdown.append("**Defaults.** Player features are granted to everyone. Every other node defaults to *nobody*.\n");
        markdown.append("`smpplugin.admin` is granted to server operators by default and contains staff tools, administration\n");
        markdown.append("and operational bypasses. It never contains destructive actions or security bypasses; grant those to\n");
        markdown.append("specific people.\n\n");
        markdown.append("**Wildcards.** A permissions plugin wildcard such as LuckPerms `smpplugin.*` or `*` grants *every* node,\n");
        markdown.append("including destructive actions and bypasses. Only use one when you mean that.\n\n");

        for (PermissionCategory category : PermissionCategory.values()) {
            List<PermissionNode> inCategory = nodes.stream().filter(node -> node.category() == category).toList();
            if (inCategory.isEmpty()) {
                continue;
            }
            markdown.append("## ").append(CATEGORY_TITLES.get(category)).append("\n\n");
            markdown.append("| Node | Allows | Default | Notes |\n");
            markdown.append("|---|---|---|---|\n");
            for (PermissionNode node : inCategory) {
                String notes = node.history();
                if (!node.children().isEmpty() && category != PermissionCategory.BUNDLE) {
                    String children = "Grants: " + String.join(", ", node.children().stream().map(c -> "`" + c + "`").toList());
                    notes = notes.isEmpty() ? children : notes + " " + children;
                } else if (category == PermissionCategory.BUNDLE) {
                    notes = (notes.isEmpty() ? "" : notes + " ") + "Contains " + node.children().size()
                            + " nodes: every node in the staff, administration and operational-bypass tables.";
                }
                markdown.append("| `").append(node.node()).append("` | ")
                        .append(escapeTable(node.description())).append(" | ")
                        .append(markdownDefault(node.defaultValue())).append(" | ")
                        .append(escapeTable(notes)).append(" |\n");
            }
            markdown.append('\n');
        }

        markdown.append("## Numbered permissions\n\n");
        markdown.append("Grant the exact node with the number you want, e.g. `smpplugin.homes.10`. These are not declared\n");
        markdown.append("in plugin.yml because the numbers are your choice. Wildcards do not grant them.\n\n");
        markdown.append("| Pattern | Meaning |\n|---|---|\n");
        for (PermissionRegistry.NumberedPermission permission : numbered) {
            markdown.append("| `").append(permission.pattern()).append("` | ")
                    .append(escapeTable(permission.description())).append(" |\n");
        }
        markdown.append('\n');
        return markdown.toString();
    }

    private static String yamlDefault(PermissionDefault value) {
        return switch (value) {
            case TRUE -> "true";
            case FALSE -> "false";
            case OP -> "op";
            case NOT_OP -> "not op";
        };
    }

    private static String markdownDefault(PermissionDefault value) {
        return switch (value) {
            case TRUE -> "everyone";
            case FALSE -> "nobody";
            case OP -> "ops";
            case NOT_OP -> "non-ops";
        };
    }

    private static String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static String escapeTable(String value) {
        return value.replace("|", "\\|");
    }
}
