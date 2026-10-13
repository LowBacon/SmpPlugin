package com.bx.smpPlugin.anticheat;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Matches a client's self-reported brand and registered plugin channels against known cheat clients.
 * Clients can lie about both, so this only catches clients that do not hide themselves; it is a cheap
 * first filter, not proof of innocence.
 */
public final class ClientSignature {

    private ClientSignature() {
    }

    /** Returns the blocked pattern the brand contains, or null. */
    public static String matchBrand(String brand, List<String> blockedPatterns) {
        if (brand == null || brand.isBlank()) {
            return null;
        }
        String lower = brand.toLowerCase(Locale.ROOT);
        for (String pattern : blockedPatterns) {
            if (!pattern.isBlank() && lower.contains(pattern)) {
                return pattern;
            }
        }
        return null;
    }

    /** Returns the first registered channel that contains a blocked pattern, or null. */
    public static String matchChannel(Collection<String> channels, List<String> blockedPatterns) {
        if (channels == null) {
            return null;
        }
        for (String channel : channels) {
            if (matchBrand(channel, blockedPatterns) != null) {
                return channel;
            }
        }
        return null;
    }
}
