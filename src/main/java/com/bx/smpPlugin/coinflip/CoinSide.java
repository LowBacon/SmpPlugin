// SmpPlugin by LowBacon
package com.bx.smpPlugin.coinflip;

import java.util.Locale;
import java.util.Optional;

public enum CoinSide {
    HEADS("Heads"),
    TAILS("Tails");

    private final String displayName;

    CoinSide(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public CoinSide opposite() {
        return this == HEADS ? TAILS : HEADS;
    }

    public static Optional<CoinSide> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "heads", "head", "h" -> Optional.of(HEADS);
            case "tails", "tail", "t" -> Optional.of(TAILS);
            default -> Optional.empty();
        };
    }
}
