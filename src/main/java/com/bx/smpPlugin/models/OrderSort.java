// SmpPlugin by LowBacon
package com.bx.smpPlugin.models;

import java.util.Locale;

public enum OrderSort {
    MOST_PAID,
    MOST_DELIVERED,
    RECENTLY_LISTED,
    MOST_MONEY_PER_ITEM;

    public static OrderSort fromConfig(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return MOST_PAID;
        }

        try {
            return OrderSort.valueOf(rawValue.trim().toUpperCase(Locale.US));
        } catch (IllegalArgumentException ignored) {
            return MOST_PAID;
        }
    }

    public String displayName() {
        return switch (this) {
            case MOST_PAID -> "Most Paid";
            case MOST_DELIVERED -> "Most Delivered";
            case RECENTLY_LISTED -> "Recently Listed";
            case MOST_MONEY_PER_ITEM -> "Most Per Item";
        };
    }
}
