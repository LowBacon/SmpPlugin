package com.bx.smpPlugin.seasonal;

import com.bx.smpPlugin.halloween.CalendarRules;

import java.time.LocalDate;

/** What an event is doing right now. */
public enum EventStatus {
    /** Players can take part. */
    ACTIVE,
    /** Waiting for its dates (later this year, or next year). */
    SCHEDULED,
    /** Switched off (by /events, its config file or the feature switch). */
    DISABLED,
    /** This year's season has ended; the next one is scheduled. */
    COMPLETED;

    /**
     * The status from the event's switches and its season. Pure, so it is tested without a server.
     *
     * @param configEnabled  ENABLED in the event's config file
     * @param featureEnabled the plugin feature (/smpplugin features) is on
     * @param mode           the /events switch
     * @param rules          the season dates
     * @param today          today in the event's time zone
     */
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
