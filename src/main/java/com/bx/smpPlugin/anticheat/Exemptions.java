package com.bx.smpPlugin.anticheat;

import java.util.EnumMap;
import java.util.Map;

/**
 * Time-bounded exemptions for one player. Every exemption expires; nothing grants a permanent
 * movement or combat bypass (teleports, knockback and similar events only open a short window).
 */
final class Exemptions {

    private final Map<CheckCategory, Long> categoryUntil = new EnumMap<>(CheckCategory.class);
    private final Map<CheckType, Long> checkUntil = new EnumMap<>(CheckType.class);

    void exempt(CheckCategory category, long durationMillis, long nowMillis) {
        categoryUntil.merge(category, nowMillis + Math.max(0L, durationMillis), Math::max);
    }

    void exempt(CheckType check, long durationMillis, long nowMillis) {
        checkUntil.merge(check, nowMillis + Math.max(0L, durationMillis), Math::max);
    }

    boolean isExempt(CheckType check, long nowMillis) {
        Long byCheck = checkUntil.get(check);
        if (byCheck != null && byCheck > nowMillis) {
            return true;
        }
        Long byCategory = categoryUntil.get(check.category());
        return byCategory != null && byCategory > nowMillis;
    }

    /** Milliseconds left on the longest exemption covering this check, or 0. */
    long remaining(CheckType check, long nowMillis) {
        long until = Math.max(checkUntil.getOrDefault(check, 0L), categoryUntil.getOrDefault(check.category(), 0L));
        return Math.max(0L, until - nowMillis);
    }

    void clear() {
        categoryUntil.clear();
        checkUntil.clear();
    }
}
