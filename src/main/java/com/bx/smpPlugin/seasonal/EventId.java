// SmpPlugin by LowBacon
package com.bx.smpPlugin.seasonal;

import java.util.Locale;
import java.util.Optional;

public enum EventId {
    ADVENT("advent", "Advent Calendar"),
    HALLOWEEN("halloween", "Halloween"),
    TRICK_OR_TREAT("trickortreat", "Trick or Treat");

    private final String key;
    private final String displayName;

    EventId(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<EventId> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String typed = raw.trim().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "").replace(" ", "");
        if (typed.equals("tot")) {
            return Optional.of(TRICK_OR_TREAT);
        }
        for (EventId id : values()) {
            if (id.key.equals(typed) || id.name().replace("_", "").equalsIgnoreCase(typed)) {
                return Optional.of(id);
            }
        }
        return Optional.empty();
    }
}
