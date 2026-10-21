package com.bx.smpPlugin.gambling;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.LongUnaryOperator;

/**
 * The maths of the head roulette, with no server code so they can be tested.
 *
 * <p><b>Chances are weighted by the bet:</b> each player's chance of winning is their bet divided
 * by the whole pot, so someone who puts in $300 of a $1,000 pot wins 30% of the time. The winner
 * takes the pot minus the fee. The draw uses one random number in {@code [0, pot)} and walks the
 * entries in the order they joined, so every cent of the pot is one equally likely ticket.
 */
public final class SpinMath {

    /** Largest allowed fee: 50%. */
    public static final int MAX_FEE_BASIS_POINTS = 5_000;

    private SpinMath() {
    }

    public static long total(long[] stakes) {
        long sum = 0L;
        for (long stake : stakes) {
            sum = Math.addExact(sum, Math.max(0L, stake));
        }
        return sum;
    }

    /**
     * The index of the winning entry. {@code ticket} must be a uniformly random number in
     * {@code [0, total(stakes))}; entry i owns the tickets from the sum of the stakes before it up to
     * (not including) that sum plus its own stake.
     */
    public static int pick(long[] stakes, long ticket) {
        long total = total(stakes);
        if (total <= 0L || ticket < 0L || ticket >= total) {
            throw new IllegalArgumentException("ticket " + ticket + " outside [0, " + total + ")");
        }
        long upTo = 0L;
        for (int i = 0; i < stakes.length; i++) {
            upTo += Math.max(0L, stakes[i]);
            if (ticket < upTo) {
                return i;
            }
        }
        throw new IllegalStateException("unreachable");
    }

    /** The fee taken from a pot, rounded down to the cent. */
    public static long fee(long pot, int basisPoints) {
        int bp = Math.max(0, Math.min(MAX_FEE_BASIS_POINTS, basisPoints));
        return BigDecimal.valueOf(pot).multiply(BigDecimal.valueOf(bp))
                .divide(BigDecimal.valueOf(10_000L), 0, RoundingMode.DOWN).longValueExact();
    }

    public static long payout(long pot, int basisPoints) {
        return pot - fee(pot, basisPoints);
    }

    /** "fee 2.5%" style: basis points to a percent string, "2.5%". */
    public static String percent(int basisPoints) {
        return BigDecimal.valueOf(basisPoints, 2).stripTrailingZeros().toPlainString() + "%";
    }

    /** A player's chance as text, e.g. "33.3%". */
    public static String chance(long stake, long pot) {
        return pot <= 0L ? "0%" : String.format(Locale.ROOT, "%.1f%%", stake * 100D / pot);
    }

    /**
     * The reel: {@code length} entry indexes that scroll past the window. The window shows
     * {@code window} slots; when the reel stops at offset {@code stop}, the middle slot shows
     * {@code reel[stop + window / 2]}, which is always the winner. The other places are filled with
     * draws of the same weighted chance, so the heads that fly past look like the real odds; they do
     * not affect who wins. {@code random} returns a number in {@code [0, bound)} for a given bound.
     */
    public static int[] reel(long[] stakes, int winner, int length, int window, int stop, LongUnaryOperator random) {
        if (stop + window > length) {
            throw new IllegalArgumentException("the reel is too short to stop at " + stop);
        }
        long total = total(stakes);
        int[] reel = new int[length];
        for (int i = 0; i < length; i++) {
            reel[i] = pick(stakes, random.applyAsLong(total));
        }
        reel[stop + window / 2] = winner;
        // Don't show the winner right beside the middle on the last frame, so the stop reads clearly.
        if (stakes.length > 1) {
            for (int side : new int[]{stop + window / 2 - 1, stop + window / 2 + 1}) {
                if (side >= 0 && side < length && reel[side] == winner) {
                    reel[side] = (winner + 1) % stakes.length;
                }
            }
        }
        return reel;
    }

    /**
     * Ticks to wait before each reel step: fast at first, then slowing down smoothly (ease-out) so
     * the last few steps crawl into place.
     */
    public static List<Integer> delays(int steps, int fastest, int slowest) {
        List<Integer> delays = new ArrayList<>(steps);
        for (int i = 0; i < steps; i++) {
            double progress = steps <= 1 ? 1D : (double) i / (steps - 1);
            double eased = Math.pow(progress, 3);
            delays.add((int) Math.round(fastest + (slowest - fastest) * eased));
        }
        return delays;
    }
}
