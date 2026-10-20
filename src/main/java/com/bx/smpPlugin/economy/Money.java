package com.bx.smpPlugin.economy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.OptionalLong;

/**
 * Money as whole cents. The economy adds, subtracts and compares {@code long} cents, so balances never drift;
 * {@code double} only appears at the edges (Vault, config files and the {@code REAL} database column).
 *
 * <p>A {@code REAL} column stores the amount in units (cents / 100). Below 2^45 units the gap between two
 * neighbouring doubles is under one cent, so every whole-cent amount up to {@link #MAX_EXACT_CENTS} reads back
 * exactly. Amounts outside that range are refused rather than rounded.</p>
 */
public final class Money {

    public static final long CENTS_PER_UNIT = 100L;

    /** 2^45 units (about 35 trillion) in cents: the largest amount whose cents survive the REAL column. */
    public static final long MAX_EXACT_CENTS = (1L << 45) * CENTS_PER_UNIT;

    private static final double MAX_EXACT_UNITS = (double) (1L << 45);

    private Money() {
    }

    /**
     * Converts an amount to cents, rounding half up to the nearest cent. Empty for NaN, infinity and amounts
     * outside the exact range.
     */
    public static OptionalLong toCents(double amount) {
        if (!Double.isFinite(amount) || Math.abs(amount) > MAX_EXACT_UNITS) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).movePointRight(2)
                .longValueExact());
    }

    /** The amount for whole cents; reads back exactly for every value up to {@link #MAX_EXACT_CENTS}. */
    public static double toUnits(long cents) {
        return cents / (double) CENTS_PER_UNIT;
    }

    /**
     * Reads a stored balance as cents. Rows written by older versions can carry float noise
     * (e.g. {@code 10.000000001}), which is rounded to the nearest cent; negative, NaN and out-of-range
     * values read as 0 and {@link #MAX_EXACT_CENTS}.
     */
    public static long storedToCents(double stored) {
        if (Double.isNaN(stored) || stored <= 0D) {
            return 0L;
        }
        if (stored >= MAX_EXACT_UNITS) {
            return MAX_EXACT_CENTS;
        }
        return toCents(stored).orElse(0L);
    }

    /**
     * {@code percent}% of {@code cents}, rounded half up to a cent. {@code percent} is clamped to 0..100.
     */
    public static long percentOf(long cents, double percent) {
        if (cents <= 0L || !Double.isFinite(percent) || percent <= 0D) {
            return 0L;
        }
        double clamped = Math.min(100D, percent);
        return BigDecimal.valueOf(cents).multiply(BigDecimal.valueOf(clamped))
                .divide(BigDecimal.valueOf(100L), 0, RoundingMode.HALF_UP).longValueExact();
    }

    /** {@code a + b}, or empty when the result leaves the exact range or overflows. */
    public static OptionalLong add(long a, long b) {
        try {
            long sum = Math.addExact(a, b);
            return Math.abs(sum) > MAX_EXACT_CENTS ? OptionalLong.empty() : OptionalLong.of(sum);
        } catch (ArithmeticException overflow) {
            return OptionalLong.empty();
        }
    }
}
