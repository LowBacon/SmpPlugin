package com.bx.smpPlugin.seasonal;

import java.util.Locale;
import java.util.Optional;

/**
 * The staff switch of an event. It never changes the event's dates in its config file: AUTO follows them, ON runs
 * the event now whatever the date, OFF stops it.
 */
public enum EventMode {
    /** Follow the dates in the event's config file. */
    AUTO,
    /** Run now, also outside the dates. */
    ON,
    /** Stopped: no new claims or interactions; progress and earned rewards are kept. */
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
