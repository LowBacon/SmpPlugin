package com.bx.smpPlugin.coinflip;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Reveal timings. Every preset reveals the same, already stored result; only the pacing differs.
 * A preset produces a list of frame delays (in ticks): the coin speeds up, spins, then slows down.
 */
public enum AnimationPreset {
    FAST("Fast", 6, 1, 3),
    SUBTLE("Subtle", 10, 2, 4),
    CLASSIC("Classic", 18, 1, 5),
    CINEMATIC("Cinematic", 30, 1, 7),
    /** Few, slow changes and no particles; for players who prefer less motion. */
    REDUCED("Reduced motion", 4, 6, 8),
    /** No animation; the result is shown at once. */
    OFF("Off", 0, 0, 0);

    private final String displayName;
    private final int flips;
    private final int fastestDelay;
    private final int slowestDelay;

    AnimationPreset(String displayName, int flips, int fastestDelay, int slowestDelay) {
        this.displayName = displayName;
        this.flips = flips;
        this.fastestDelay = fastestDelay;
        this.slowestDelay = slowestDelay;
    }

    public String displayName() {
        return displayName;
    }

    /** Delay before each coin face change: speeding up over the first third, then slowing down to the reveal. */
    public List<Integer> frameDelays() {
        List<Integer> delays = new ArrayList<>(flips);
        if (flips == 0) {
            return delays;
        }
        int accelerate = Math.max(1, flips / 3);
        for (int i = 0; i < flips; i++) {
            double progress;
            if (i < accelerate) {
                progress = 1D - (double) i / accelerate;
            } else {
                progress = (double) (i - accelerate) / Math.max(1, flips - accelerate - 1);
                progress = progress * progress;
            }
            delays.add((int) Math.round(fastestDelay + (slowestDelay - fastestDelay) * progress));
        }
        return delays;
    }

    public int totalTicks() {
        return frameDelays().stream().mapToInt(Integer::intValue).sum();
    }

    public boolean reducedMotion() {
        return this == REDUCED || this == OFF;
    }

    public AnimationPreset next() {
        AnimationPreset[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public static Optional<AnimationPreset> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
