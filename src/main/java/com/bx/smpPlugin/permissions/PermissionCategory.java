package com.bx.smpPlugin.permissions;

/**
 * How a permission node is meant to be used. The category decides its default and
 * whether the default-op {@code smpplugin.admin} bundle grants it.
 */
public enum PermissionCategory {
    /** Normal player feature. Default {@code true}; the feature toggle in config decides whether it exists. */
    PLAYER(true, false),
    /** Rank benefit (quick buy, flight perk, cosmetics). Default {@code false}. */
    PERK(false, false),
    /** Staff tool. Default {@code false}; granted to ops through {@code smpplugin.admin}. */
    STAFF(false, true),
    /** Server administration. Default {@code false}; granted to ops through {@code smpplugin.admin}. */
    ADMIN(false, true),
    /**
     * Bypass that admins need to run the server (build in the protected lobby, join during maintenance).
     * Default {@code false}; granted to ops through {@code smpplugin.admin}.
     */
    OPERATIONAL_BYPASS(false, true),
    /** Bypass that weakens a player-facing protection or privacy rule. Never in a bundle; grant explicitly. */
    SECURITY_BYPASS(false, false),
    /** Irreversible or data-overwriting action. Never in a bundle; grant explicitly. */
    DESTRUCTIVE(false, false),
    /**
     * Senior-staff action (managing giveaways). Default {@code false} and not in {@code smpplugin.admin} or the
     * helper, moderator or admin roles: only the manager, developer, co-owner and owner roles include it. Server
     * operators are always allowed by the command itself.
     */
    SENIOR_STAFF(false, false),
    /** Groups other nodes. */
    BUNDLE(false, false),
    /** Old node name kept so existing grants keep working; grants its replacement as a child. */
    LEGACY(false, false);

    private final boolean defaultTrue;
    private final boolean inAdminBundle;

    PermissionCategory(boolean defaultTrue, boolean inAdminBundle) {
        this.defaultTrue = defaultTrue;
        this.inAdminBundle = inAdminBundle;
    }

    public boolean defaultTrue() {
        return defaultTrue;
    }

    /** Whether {@code smpplugin.admin} includes nodes of this category. */
    public boolean inAdminBundle() {
        return inAdminBundle;
    }
}
