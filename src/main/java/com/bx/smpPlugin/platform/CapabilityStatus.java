package com.bx.smpPlugin.platform;

/** Status of one feature or dependency on the running server. */
public enum CapabilityStatus {
    /** Available and expected to work on this platform. */
    SUPPORTED("&a"),
    /** Running with reduced functionality; the detail explains what is missing. */
    DEGRADED("&e"),
    /** Not available here; dependent features are switched off. */
    UNAVAILABLE("&c"),
    /** Present, but this combination has not been verified. */
    UNTESTED("&6");

    private final String color;

    CapabilityStatus(String color) {
        this.color = color;
    }

    public String color() {
        return color;
    }
}
