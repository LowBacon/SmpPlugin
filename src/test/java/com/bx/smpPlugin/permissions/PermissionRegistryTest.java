// SmpPlugin by LowBacon
package com.bx.smpPlugin.permissions;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.permissions.PermissionDefault;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class PermissionRegistryTest {

    private static final Path PLUGIN_YML = Path.of("src/main/resources/plugin.yml");
    private static final Path DOCS = Path.of("docs/permissions-reference.md");
    private static final boolean REGENERATE = Boolean.getBoolean("smpplugin.regenerate");

    private static final Set<String> NOT_PERMISSIONS = Set.of("smpplugin.loaded-in-process");

    private static final Pattern NODE_LITERAL = Pattern.compile(
            "\"((?:smpplugin|anvilmod|safety|donutauction|donutfriends)\\.[A-Za-z0-9_.\\-]*[A-Za-z0-9_\\-])\"");

    @Test
    void everyChildIsRegistered() {
        for (PermissionNode node : PermissionRegistry.all()) {
            for (String child : node.children()) {
                if (child.startsWith("titanguard.")) {
                    continue;
                }
                assertTrue(PermissionRegistry.isRegistered(child), node.node() + " has unregistered child " + child);
            }
        }
    }

    @Test
    void onlyPlayerFeaturesDefaultToEveryoneAndOnlyTheAdminBundleDefaultsToOp() {
        for (PermissionNode node : PermissionRegistry.all()) {
            if (node.node().equals(PermissionRegistry.ADMIN_BUNDLE)) {
                assertEquals(PermissionDefault.OP, node.defaultValue());
            } else if (node.category() == PermissionCategory.PLAYER) {
                assertEquals(PermissionDefault.TRUE, node.defaultValue(), node.node());
            } else {
                assertEquals(PermissionDefault.FALSE, node.defaultValue(), node.node());
            }
        }
    }

    @Test
    void adminBundleExcludesDestructiveActionsBypassesAndPerks() {
        PermissionNode admin = PermissionRegistry.find(PermissionRegistry.ADMIN_BUNDLE).orElseThrow();
        for (String child : admin.children()) {
            PermissionCategory category = PermissionRegistry.find(child).orElseThrow().category();
            assertTrue(category.inAdminBundle(), "smpplugin.admin must not grant " + child + " (" + category + ")");
        }
        for (String excluded : List.of("smpplugin.admin.serverwipe", "smpplugin.admin.statswipe",
                "smpplugin.staff.punishments.delete", "smpplugin.ignore.bypass", "smpplugin.admin.spawner.seeall",
                "smpplugin.staff.invsee.modify", "smpplugin.admin.ecsee.edit")) {
            assertFalse(admin.children().contains(excluded), "smpplugin.admin must not grant " + excluded);
        }
    }

    @Test
    void staffModeNoLongerBundlesTools() {
        PermissionNode staffMode = PermissionRegistry.find("smpplugin.staff.mode").orElseThrow();
        assertTrue(staffMode.children().isEmpty(), "smpplugin.staff.mode must only open staff mode");
        PermissionNode legacy = PermissionRegistry.find(PermissionRegistry.LEGACY_STAFF_MODE_BUNDLE).orElseThrow();
        assertTrue(legacy.children().contains("smpplugin.staff.freeze"));
    }

    @Test
    void legacyPunishmentBundlesGrantExactlyWhatTheyGrantedBefore() {
        PermissionNode create = PermissionRegistry.find("smpplugin.staff.punishments.create").orElseThrow();
        assertEquals(Set.of("ban", "tempban", "mute", "tempmute", "blacklist", "warn", "kick"),
                suffixes(create.children(), "smpplugin.staff.punishments."));
        assertEquals(List.of("smpplugin.staff.punishments.tempban"),
                PermissionRegistry.find("smpplugin.staff.punishments.ban").orElseThrow().children());
        assertEquals(List.of("smpplugin.staff.punishments.tempmute"),
                PermissionRegistry.find("smpplugin.staff.punishments.mute").orElseThrow().children());
    }

    @Test
    void parentNodesAreDeclaredBeforeTheirDottedDescendants() {
        // Bukkit's YamlConfiguration treats dots as nesting; a parent declared after a descendant
        // would overwrite the descendant's section when plugin.yml is read that way.
        List<String> order = PermissionRegistry.all().stream().map(PermissionNode::node).toList();
        for (int i = 0; i < order.size(); i++) {
            for (int j = 0; j < i; j++) {
                assertFalse(order.get(j).startsWith(order.get(i) + "."),
                        order.get(i) + " must be declared before " + order.get(j));
            }
        }
    }

    @Test
    void pluginYmlPermissionsSectionIsGeneratedFromTheRegistry() throws IOException {
        String expected = PermissionRenderer.renderPluginYml(PermissionRegistry.all());
        String content = Files.readString(PLUGIN_YML, StandardCharsets.UTF_8).replace("\r\n", "\n");
        int begin = content.indexOf(PermissionRenderer.YAML_BEGIN);
        int end = content.indexOf(PermissionRenderer.YAML_END);
        if (begin < 0 || end < begin) {
            fail("plugin.yml is missing the generated permissions markers");
        }
        String current = content.substring(begin, end + PermissionRenderer.YAML_END.length() + 1);

        if (REGENERATE) {
            Files.writeString(PLUGIN_YML, content.replace(current, expected), StandardCharsets.UTF_8);
            return;
        }
        assertEquals(expected, current,
                "plugin.yml permissions are stale; run mvn test -Dtest=PermissionRegistryTest -Dsmpplugin.regenerate=true");
    }

    @Test
    void permissionsReferenceIsGeneratedFromTheRegistry() throws IOException {
        String expected = PermissionRenderer.renderMarkdown(PermissionRegistry.all(), PermissionRegistry.numberedPermissions());
        if (REGENERATE) {
            Files.createDirectories(DOCS.getParent());
            Files.writeString(DOCS, expected, StandardCharsets.UTF_8);
            return;
        }
        assertTrue(Files.exists(DOCS), "docs/permissions-reference.md is missing; regenerate it");
        assertEquals(expected, Files.readString(DOCS, StandardCharsets.UTF_8).replace("\r\n", "\n"),
                "docs/permissions-reference.md is stale; regenerate it");
    }

    @Test
    void pluginYmlParsesAndDeclaresEveryRegisteredNodeWithTheRightDefault() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(PLUGIN_YML.toFile());
        ConfigurationSection permissions = yaml.getConfigurationSection("permissions");
        assertTrue(permissions != null, "plugin.yml has no permissions section");
        for (PermissionNode node : PermissionRegistry.all()) {
            // Bukkit splits on dots when reading nested keys, so look the node up as a single key.
            ConfigurationSection section = findDirectChild(permissions, node.node());
            assertTrue(section != null, "plugin.yml does not declare " + node.node());
            String expectedDefault = switch (node.defaultValue()) {
                case TRUE -> "true";
                case FALSE -> "false";
                case OP -> "op";
                case NOT_OP -> "not op";
            };
            assertEquals(expectedDefault, String.valueOf(section.get("default")), node.node());
        }
    }

    @Test
    void commandPermissionsInPluginYmlAreRegistered() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(PLUGIN_YML.toFile());
        ConfigurationSection commands = yaml.getConfigurationSection("commands");
        assertTrue(commands != null);
        for (String command : commands.getKeys(false)) {
            String permission = commands.getString(command + ".permission");
            if (permission == null) {
                continue;
            }
            // Bukkit accepts several nodes separated by ';' (any one of them is enough).
            for (String node : permission.split(";")) {
                assertTrue(PermissionRegistry.isRegistered(node.trim()),
                        "/" + command + " uses unregistered permission " + node);
            }
        }
    }

    @Test
    void everyPermissionCheckedInSourceIsRegistered() throws IOException {
        Set<String> unknown = new TreeSet<>();
        for (Path root : List.of(Path.of("src/main/java"))) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    Matcher matcher = NODE_LITERAL.matcher(Files.readString(file, StandardCharsets.UTF_8));
                    while (matcher.find()) {
                        String node = matcher.group(1).toLowerCase(Locale.ROOT);
                        if (!NOT_PERMISSIONS.contains(node) && !PermissionRegistry.isKnown(node)) {
                            unknown.add(node + " (" + root.relativize(file) + ")");
                        }
                    }
                }
            }
        }
        assertTrue(unknown.isEmpty(), "Unregistered permissions checked in code: " + unknown);
    }

    @Test
    void composedAuctionHouseNodesAreRegisteredWithLegacyAliases() {
        for (String action : PermissionRegistry.AUCTION_ACTIONS) {
            assertTrue(PermissionRegistry.isRegistered("smpplugin.auctionhouse." + action), action);
            PermissionNode legacy = PermissionRegistry.find("donutauction." + action).orElseThrow();
            assertEquals(List.of("smpplugin.auctionhouse." + action), legacy.children(), action);
        }
    }

    @Test
    void permissionsConfiguredInBundledConfigFilesAreRegistered() throws IOException {
        Pattern configured = Pattern.compile("PERMISSION[A-Z_-]*:\\s*['\"]?(smpplugin\\.[A-Za-z0-9_.\\-]+)");
        Set<String> unknown = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/resources"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".yml")).toList()) {
                if (file.getFileName().toString().equals("plugin.yml")) {
                    continue;
                }
                Matcher matcher = configured.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    String node = matcher.group(1).toLowerCase(Locale.ROOT);
                    if (!PermissionRegistry.isKnown(node)) {
                        unknown.add(node + " (" + file.getFileName() + ")");
                    }
                }
            }
        }
        assertTrue(unknown.isEmpty(), "Unregistered permissions in bundled config: " + unknown);
    }

    private static ConfigurationSection findDirectChild(ConfigurationSection permissions, String node) {
        // plugin.yml keys contain dots; Bukkit's YAML reader keeps "a.b.c" as nested paths, so walk them.
        Object value = permissions.get(node);
        return value instanceof ConfigurationSection section ? section : null;
    }

    private static Set<String> suffixes(List<String> nodes, String prefix) {
        List<String> result = new ArrayList<>();
        for (String node : nodes) {
            result.add(node.substring(prefix.length()));
        }
        return Set.copyOf(result);
    }
}
