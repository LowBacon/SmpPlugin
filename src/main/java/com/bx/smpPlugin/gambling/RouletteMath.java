// SmpPlugin by LowBacon
package com.bx.smpPlugin.gambling;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.LongUnaryOperator;

public final class RouletteMath {

    public static final int MAX_FEE_BASIS_POINTS = 5_000;

    private RouletteMath() {
    }

    public static long total(long[] stakes) {
        long sum = 0L;
        for (long stake : stakes) {
            sum = Math.addExact(sum, Math.max(0L, stake));
        }
        return sum;
    }

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

    public static long fee(long pot, int basisPoints) {
        int bp = Math.max(0, Math.min(MAX_FEE_BASIS_POINTS, basisPoints));
        return BigDecimal.valueOf(pot).multiply(BigDecimal.valueOf(bp))
                .divide(BigDecimal.valueOf(10_000L), 0, RoundingMode.DOWN).longValueExact();
    }

    public static long payout(long pot, int basisPoints) {
        return pot - fee(pot, basisPoints);
    }

    public static String percent(int basisPoints) {
        return BigDecimal.valueOf(basisPoints, 2).stripTrailingZeros().toPlainString() + "%";
    }

    public static String chance(long stake, long pot) {
        return pot <= 0L ? "0%" : String.format(Locale.ROOT, "%.1f%%", stake * 100D / pot);
    }

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
