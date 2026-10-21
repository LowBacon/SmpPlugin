package com.bx.smpPlugin.economy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.OptionalLong;

/**
 * Money as whole cents. The economy adds, subtracts and compares {@code long} cents, so balances never drift;
 * {@code double} only appears at the edges (Vault, config files, placeholders and the {@code money} column).
 *
 * <p>A balance holds up to {@link #MAX_CENTS} ({@code Long.MAX_VALUE} cents, about 92 quadrillion) and is stored
 * exactly in {@code players.money_cents}. A double carries every cent only up to {@link #DOUBLE_EXACT_CENTS}
 * (2^45 units, about 35 trillion): above that neighbouring doubles are more than a cent apart, so an amount passed
 * as a double is the nearest one the double can still tell apart.</p>
 */
public final class Money {

    public static final long CENTS_PER_UNIT = 100L;

    /** The largest balance there is: 92,233,720,368,547,758.07. */
    public static final long MAX_CENTS = Long.MAX_VALUE;

    /** 2^45 units (about 35 trillion) in cents: up to here a double carries every cent exactly. */
    public static final long DOUBLE_EXACT_CENTS = (1L << 45) * CENTS_PER_UNIT;

    /** Every amount from here on is beyond {@link #MAX_CENTS}; checked first so it never becomes a huge decimal. */
    private static final double BEYOND_MAX_UNITS = 1e17D;
    private static final BigDecimal MAX_DECIMAL = BigDecimal.valueOf(MAX_CENTS);

    private Money() {
    }

    /**
     * Converts an amount to cents, rounding half up to the nearest cent. Empty for NaN, infinity and amounts
     * beyond {@link #MAX_CENTS} either way.
     */
    public static OptionalLong toCents(double amount) {
        if (!Double.isFinite(amount) || Math.abs(amount) >= BEYOND_MAX_UNITS) {
            return OptionalLong.empty();
        }
        BigDecimal cents = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).movePointRight(2);
        return cents.abs().compareTo(MAX_DECIMAL) > 0 ? OptionalLong.empty() : OptionalLong.of(cents.longValueExact());
    }

    /**
     * The amount for whole cents. Up to {@link #DOUBLE_EXACT_CENTS} it reads back as exactly these cents; above
     * that it is the nearest double that does not read back as more, so a balance shown or handed to another
     * plugin as a double is never overstated (and withdrawing all of it still succeeds).
     */
    public static double toUnits(long cents) {
        double units = cents / (double) CENTS_PER_UNIT;
        if (cents >= -DOUBLE_EXACT_CENTS && cents <= DOUBLE_EXACT_CENTS) {
            return units;
        }
        while (overstates(units, cents)) {
            units = cents > 0L ? Math.nextDown(units) : Math.nextUp(units);
        }
        return units;
    }

    private static boolean overstates(double units, long cents) {
        OptionalLong back = toCents(units);
        return back.isEmpty() || (cents > 0L ? back.getAsLong() > cents : back.getAsLong() < cents);
    }

    /**
     * Reads a stored balance as cents. Rows written by older versions can carry float noise
     * (e.g. {@code 10.000000001}), which is rounded to the nearest cent; negative and NaN values read as 0 and
     * values beyond the largest balance as {@link #MAX_CENTS}.
     */
    public static long storedToCents(double stored) {
        if (Double.isNaN(stored) || stored <= 0D) {
            return 0L;
        }
        return toCents(stored).orElse(MAX_CENTS);
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

    /** {@code a + b}, or empty when the result would pass {@link #MAX_CENTS} either way. */
    public static OptionalLong add(long a, long b) {
        try {
            long sum = Math.addExact(a, b);
            return sum == Long.MIN_VALUE ? OptionalLong.empty() : OptionalLong.of(sum);
        } catch (ArithmeticException overflow) {
            return OptionalLong.empty();
        }
    }
}
