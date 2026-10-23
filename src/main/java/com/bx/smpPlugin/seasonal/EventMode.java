// SmpPlugin by LowBacon
package com.bx.smpPlugin.seasonal;

import java.util.Locale;
import java.util.Optional;

public enum EventMode {
    AUTO,
    ON,
    OFF;

    public static Optional<EventMode> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "on", "enable", "enabled", "start", "true" -> Optional.of(ON);
            case "off", "disable", "disabled", "stop", "false" -> Optional.of(OFF);
            case "auto", "schedule", "scheduled", "default" -> Optional.of(AUTO);
            default -> Optional.empty();
        };
    }
}
