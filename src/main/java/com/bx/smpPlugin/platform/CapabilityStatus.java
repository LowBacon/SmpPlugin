// SmpPlugin by LowBacon
package com.bx.smpPlugin.platform;

public enum CapabilityStatus {
    SUPPORTED("&a"),
    DEGRADED("&e"),
    UNAVAILABLE("&c"),
    UNTESTED("&6");

    private final String color;

    CapabilityStatus(String color) {
        this.color = color;
    }

    public String color() {
        return color;
    }
}
