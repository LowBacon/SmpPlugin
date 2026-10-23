// SmpPlugin by LowBacon
package com.bx.smpPlugin.commands;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.permissions.Permissible;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PunishmentCommandPermissionTest {

    private static final String PREFIX = "smpplugin.staff.punishments.";

    @Test
    void mapsCommandsAndAliasesToExpectedPermissions() {
        Map<String, String> expected = Map.ofEntries(
                Map.entry("ban", PREFIX + "ban"),
                Map.entry("tempban", PREFIX + "tempban"),
                Map.entry("unban", PREFIX + "unban"),
                Map.entry("pardon", PREFIX + "unban"),
                Map.entry("mute", PREFIX + "mute"),
                Map.entry("tempmute", PREFIX + "tempmute"),
                Map.entry("unmute", PREFIX + "unmute"),
                Map.entry("blacklist", PREFIX + "blacklist"),
                Map.entry("unblacklist", PREFIX + "unblacklist"),
                Map.entry("warn", PREFIX + "warn"),
                Map.entry("kick", PREFIX + "kick")
        );

        expected.forEach((action, permission) ->
                assertEquals(permission, PunishmentCommand.permissionForAction(action), action));
        assertNull(PunishmentCommand.permissionForAction("unknown"));
    }

    @Test
    void granularPermissionOnlyAllowsItsMappedActions() {
        // Bukkit resolves plugin.yml children before the check runs, so a test permissible only holds
        // what it was granted directly: holding tempban alone must not allow a permanent ban.
        TestPermissible permissible = new TestPermissible().grant(PREFIX + "tempban");

        assertTrue(PunishmentCommand.hasPermissionForAction(permissible, "tempban"));
        assertFalse(PunishmentCommand.hasPermissionForAction(permissible, "ban"));
        assertFalse(PunishmentCommand.hasPermissionForAction(permissible, "warn"));
        assertFalse(PunishmentCommand.hasPermissionForAction(permissible, "mute"));
        assertFalse(PunishmentCommand.hasPermissionForAction(permissible, "blacklist"));
        assertFalse(PunishmentCommand.hasPermissionForAction(permissible, "unban"));
    }

    @Test
    void pluginMetadataUsesGranularCommandsAndLegacyParentPermissions() throws Exception {
        YamlConfiguration pluginYaml = loadPluginYaml();

        for (String action : Set.of("ban", "tempban", "mute", "tempmute", "blacklist", "unban", "unmute",
                "unblacklist", "warn", "kick")) {
            assertEquals(PREFIX + action, pluginYaml.getString("commands." + action + ".permission"), action);
        }
        assertTrue(pluginYaml.getStringList("commands.unban.aliases").contains("pardon"));

        // 1.5 grants keep working: the legacy bundles still grant everything they granted before.
        for (String child : Set.of("ban", "tempban", "mute", "tempmute", "blacklist", "warn", "kick")) {
            assertTrue(pluginYaml.getBoolean("permissions." + PREFIX + "create.children." + PREFIX + child), child);
        }
        assertTrue(pluginYaml.getBoolean("permissions." + PREFIX + "ban.children." + PREFIX + "tempban"));
        assertTrue(pluginYaml.getBoolean("permissions." + PREFIX + "mute.children." + PREFIX + "tempmute"));
        assertTrue(pluginYaml.getBoolean("permissions." + PREFIX + "remove.children." + PREFIX + "unban"));
        assertTrue(pluginYaml.getBoolean("permissions." + PREFIX + "remove.children." + PREFIX + "unmute"));
        assertTrue(pluginYaml.getBoolean("permissions." + PREFIX + "remove.children." + PREFIX + "unblacklist"));

        for (String action : Set.of("ban", "tempban", "mute", "tempmute", "warn", "kick", "blacklist",
                "unban", "unmute", "unblacklist")) {
            String path = "permissions." + PREFIX + action + ".default";
            assertEquals(Boolean.FALSE, pluginYaml.get(path), action);
        }
    }

    private YamlConfiguration loadPluginYaml() {
        var file = new java.io.File("src/main/resources/plugin.yml");
        return YamlConfiguration.loadConfiguration(file);
    }

    private static final class TestPermissible implements Permissible {
        private final Set<PermissionAttachmentInfo> effectivePermissions = new LinkedHashSet<>();
        private boolean op;

        private TestPermissible grant(String permission) {
            effectivePermissions.add(new PermissionAttachmentInfo(this, permission, null, true));
            return this;
        }

        @Override
        public boolean isPermissionSet(String name) {
            return effectivePermissions.stream()
                    .anyMatch(info -> info.getPermission().equalsIgnoreCase(name));
        }

        @Override
        public boolean isPermissionSet(Permission permission) {
            return permission != null && isPermissionSet(permission.getName());
        }

        @Override
        public boolean hasPermission(String name) {
            return effectivePermissions.stream()
                    .anyMatch(info -> info.getValue() && info.getPermission().equalsIgnoreCase(name));
        }

        @Override
        public boolean hasPermission(Permission permission) {
            return permission != null && hasPermission(permission.getName());
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin, int ticks) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeAttachment(PermissionAttachment attachment) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void recalculatePermissions() {
        }

        @Override
        public Set<PermissionAttachmentInfo> getEffectivePermissions() {
            return effectivePermissions;
        }

        @Override
        public boolean isOp() {
            return op;
        }

        @Override
        public void setOp(boolean value) {
            op = value;
        }
    }
}
