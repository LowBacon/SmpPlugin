// SmpPlugin by LowBacon
package com.bx.smpPlugin.utils;

import org.bukkit.permissions.Permissible;

public final class PunishmentExemptPolicy {

    public static final String EXEMPT_PERMISSION = "smpplugin.admin.punishments.exempt";
    public static final String BYPASS_PERMISSION = "smpplugin.admin.punishments.exempt.bypass";

    private PunishmentExemptPolicy() {
    }

    public static boolean isExempt(Permissible target) {
        return target != null && PermissionUtils.has(target, EXEMPT_PERMISSION);
    }

    public static boolean canPunish(Permissible issuer, Permissible target) {
        if (target == null || !isExempt(target)) {
            return true;
        }
        if (!(issuer instanceof org.bukkit.entity.Player)) {
            return true;
        }
        return PermissionUtils.has(issuer, BYPASS_PERMISSION);
    }
}
