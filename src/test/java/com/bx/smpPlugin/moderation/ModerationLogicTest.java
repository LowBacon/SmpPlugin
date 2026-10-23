// SmpPlugin by LowBacon
package com.bx.smpPlugin.moderation;

import com.bx.smpPlugin.permissions.PermissionCategory;
import com.bx.smpPlugin.permissions.PermissionNode;
import com.bx.smpPlugin.permissions.PermissionRegistry;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModerationLogicTest {

    @Test
    void eachStaffRoleContainsTheOneBelowIt() {
        Set<String> helper = PermsCommand.expand(PermissionRegistry.ROLE_HELPER);
        Set<String> moderator = PermsCommand.expand(PermissionRegistry.ROLE_MODERATOR);
        Set<String> admin = PermsCommand.expand(PermissionRegistry.ROLE_ADMIN);
        Set<String> owner = PermsCommand.expand(PermissionRegistry.ROLE_OWNER);
        assertTrue(moderator.containsAll(helper));
        assertTrue(admin.containsAll(moderator));
        assertTrue(owner.containsAll(admin));
        assertTrue(helper.contains("smpplugin.staff.punishments.warn"));
        assertFalse(helper.contains("smpplugin.staff.punishments.ban"), "helpers cannot ban");
        assertTrue(moderator.contains("smpplugin.staff.punishments.ban"));
        assertTrue(admin.contains("smpplugin.admin.reload"));
        assertFalse(admin.contains("smpplugin.admin.serverwipe"), "the admin role has no destructive actions");
        assertTrue(owner.contains("smpplugin.admin.serverwipe"));
    }

    @Test
    void noRoleGrantsSecurityBypasses() {
        for (String role : PermissionRegistry.ROLES) {
            for (String node : PermsCommand.expand(role)) {
                if (node.startsWith("titanguard.")) {
                    assertFalse(node.startsWith("titanguard.bypass"), role + " grants " + node);
                    continue;
                }
                PermissionNode found = PermissionRegistry.find(node).orElseThrow();
                assertFalse(found.category() == PermissionCategory.SECURITY_BYPASS, role + " grants " + node);
            }
        }
    }

    @Test
    void spyReadsTheCommandNameWithoutPluginPrefix() {
        assertEquals("msg", Spy.label("/msg Steve hi"));
        assertEquals("msg", Spy.label("/smpplugin:msg Steve hi"));
        assertEquals("login", Spy.label("/LOGIN secret"));
        assertTrue(Spy.NEVER_SHOWN.contains(Spy.label("/register secret secret")));
    }

    @Test
    void speedLevelOneIsVanillaAndTenIsTheMaximum() {
        assertEquals(0.2F, AdminCommands.speedValue(1F, false), 0.0001F);
        assertEquals(0.1F, AdminCommands.speedValue(1F, true), 0.0001F);
        assertEquals(1F, AdminCommands.speedValue(10F, false), 0.0001F);
        assertEquals(1F, AdminCommands.speedValue(10F, true), 0.0001F);
        assertEquals(0.1F, AdminCommands.speedValue(0.5F, false), 0.0001F);
    }
}
