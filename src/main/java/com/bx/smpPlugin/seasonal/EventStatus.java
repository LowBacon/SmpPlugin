// SmpPlugin by LowBacon
package com.bx.smpPlugin.seasonal;

import com.bx.smpPlugin.halloween.CalendarRules;

import java.time.LocalDate;

public enum EventStatus {
    ACTIVE,
    SCHEDULED,
    DISABLED,
    COMPLETED;

    public static EventStatus of(boolean configEnabled, boolean featureEnabled, EventMode mode, CalendarRules rules,
                                 LocalDate today) {
        if (!configEnabled || !featureEnabled || mode == EventMode.OFF) {
            return DISABLED;
        }
        if (mode == EventMode.ON || rules.inSeason(today)) {
            return ACTIVE;
        }
        return today.isAfter(rules.claimDeadline(today.getYear())) ? COMPLETED : SCHEDULED;
    }
}
