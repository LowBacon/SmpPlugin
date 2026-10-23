// SmpPlugin by LowBacon
package com.bx.smpPlugin.economy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.OptionalLong;

public final class Money {

    public static final long CENTS_PER_UNIT = 100L;

    public static final long MAX_CENTS = Long.MAX_VALUE;

    public static final long DOUBLE_EXACT_CENTS = (1L << 45) * CENTS_PER_UNIT;

    private static final double BEYOND_MAX_UNITS = 1e17D;
    private static final BigDecimal MAX_DECIMAL = BigDecimal.valueOf(MAX_CENTS);

    private Money() {
    }

    public static OptionalLong toCents(double amount) {
        if (!Double.isFinite(amount) || Math.abs(amount) >= BEYOND_MAX_UNITS) {
            return OptionalLong.empty();
        }
        BigDecimal cents = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).movePointRight(2);
        return cents.abs().compareTo(MAX_DECIMAL) > 0 ? OptionalLong.empty() : OptionalLong.of(cents.longValueExact());
    }

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

    public static long storedToCents(double stored) {
        if (Double.isNaN(stored) || stored <= 0D) {
            return 0L;
        }
        return toCents(stored).orElse(MAX_CENTS);
    }

    public static long percentOf(long cents, double percent) {
        if (cents <= 0L || !Double.isFinite(percent) || percent <= 0D) {
            return 0L;
        }
        double clamped = Math.min(100D, percent);
        return BigDecimal.valueOf(cents).multiply(BigDecimal.valueOf(clamped))
                .divide(BigDecimal.valueOf(100L), 0, RoundingMode.HALF_UP).longValueExact();
    }

    public static OptionalLong add(long a, long b) {
        try {
            long sum = Math.addExact(a, b);
            return sum == Long.MIN_VALUE ? OptionalLong.empty() : OptionalLong.of(sum);
        } catch (ArithmeticException overflow) {
            return OptionalLong.empty();
        }
    }
}
