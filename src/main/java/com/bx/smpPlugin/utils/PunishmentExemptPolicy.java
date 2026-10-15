package com.bx.smpPlugin.utils;

import org.bukkit.permissions.Permissible;

/**
 * Decides whether one player is allowed to punish another.
 *
 * <p>The permission that grants a punishment says nothing about who it may be used on, so a rank
 * given {@code smpplugin.staff.punishments.offend} could ban the owner. A protected player carries
 * {@link #EXEMPT_PERMISSION}, and only somebody holding {@link #BYPASS_PERMISSION} can still punish
 * them. Both are security bypasses, so {@code smpplugin.admin} does not hand them out and
 * moderators cannot make each other exempt.
 */
public final class PunishmentExemptPolicy {

    public static final String EXEMPT_PERMISSION = "smpplugin.admin.punishments.exempt";
    public static final String BYPASS_PERMISSION = "smpplugin.admin.punishments.exempt.bypass";

    private PunishmentExemptPolicy() {
    }

    public static boolean isExempt(Permissible target) {
        return target != null && PermissionUtils.has(target, EXEMPT_PERMISSION);
    }

    /**
     * A null target is an offline player. Their permissions cannot be read without them being on
     * the server, so an offline punishment goes through as it always has. The console is never
     * blocked.
     */
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
