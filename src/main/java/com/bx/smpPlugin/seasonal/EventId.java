package com.bx.smpPlugin.seasonal;

import java.util.Locale;
import java.util.Optional;

/** The seasonal events staff can switch with /events. The key never changes; it is stored in data/events.yml. */
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

    /** Accepts the key, the enum name and a few spellings ("trick-or-treat", "tot"). */
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
