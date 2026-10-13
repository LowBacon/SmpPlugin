package com.bx.smpPlugin.anticheat;

/** Groups checks so exemptions and bypass permissions can be scoped narrowly. */
public enum CheckCategory {
    MOVEMENT("smpplugin.bypass.anticheat.movement"),
    COMBAT("smpplugin.bypass.anticheat.combat"),
    WORLD("smpplugin.bypass.anticheat.world"),
    PLAYER("smpplugin.bypass.anticheat.player");

    private final String bypassPermission;

    CheckCategory(String bypassPermission) {
        this.bypassPermission = bypassPermission;
    }

    public String bypassPermission() {
        return bypassPermission;
    }
}
