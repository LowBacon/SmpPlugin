// SmpPlugin by LowBacon
package com.bx.smpPlugin.permissions;

public enum PermissionCategory {
    PLAYER(true, false),
    PERK(false, false),
    STAFF(false, true),
    ADMIN(false, true),
    OPERATIONAL_BYPASS(false, true),
    SECURITY_BYPASS(false, false),
    DESTRUCTIVE(false, false),
    SENIOR_STAFF(false, false),
    BUNDLE(false, false),
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

    public boolean inAdminBundle() {
        return inAdminBundle;
    }
}
