package com.bx.smpPlugin.economy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * Money arithmetic in whole cents.
 *
 * <p>Balances are stored as decimal numbers (the database column predates 4.0), but every change
 * is worked out here in {@code long} cents: the amount and the balance are rounded to the cent
 * once, added or subtracted exactly with overflow checks, and turned back into a decimal. A
 * {@code double} holds every whole number of cents exactly up to 2^53, so balances stay exact up
 * to {@link #PRECISION_LIMIT_CENTS} (90 trillion); larger balances are refused.
 */
public final class Money {

    /** 90,000,000,000,000.00: below 2^53 cents, the largest balance a double stores exactly. */
    public static final long PRECISION_LIMIT_CENTS = 9_000_000_000_000_000L;

    private Money() {
    }

    /**
     * An amount in cents, rounded half-up. Empty for NaN, infinity, negative numbers and anything
     * past {@link #PRECISION_LIMIT_CENTS}.
     */
    public static OptionalLong toCents(double amount) {
        if (!Double.isFinite(amount) || amount < 0D) {
            return OptionalLong.empty();
        }
        BigDecimal cents = BigDecimal.valueOf(amount).movePointRight(2).setScale(0, RoundingMode.HALF_UP);
        if (cents.compareTo(BigDecimal.valueOf(PRECISION_LIMIT_CENTS)) > 0) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(cents.longValueExact());
    }

    /** A stored balance in cents. A corrupt stored value (NaN, negative) counts as zero. */
    public static long balanceCents(double storedBalance) {
        if (!Double.isFinite(storedBalance) || storedBalance <= 0D) {
            return 0L;
        }
        if (storedBalance >= PRECISION_LIMIT_CENTS / 100D) {
            return PRECISION_LIMIT_CENTS;
        }
        return toCents(storedBalance).orElse(0L);
    }

    public static double toDouble(long cents) {
        return cents / 100D;
    }

    /**
     * The balance after adding, or empty when it would pass {@code maxCents} (or overflow).
     * {@code maxCents <= 0} means the precision limit.
     */
    public static OptionalLong add(long balanceCents, long amountCents, long maxCents) {
        long limit = effectiveLimit(maxCents);
        if (amountCents < 0L || balanceCents < 0L) {
            return OptionalLong.empty();
        }
        long result;
        try {
            result = Math.addExact(balanceCents, amountCents);
        } catch (ArithmeticException overflow) {
            return OptionalLong.empty();
        }
        return result > limit ? OptionalLong.empty() : OptionalLong.of(result);
    }

    /** The balance after taking the amount away, or empty when the balance is too small. */
    public static OptionalLong subtract(long balanceCents, long amountCents) {
        if (amountCents < 0L || balanceCents < amountCents) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(balanceCents - amountCents);
    }

    public static long effectiveLimit(long maxCents) {
        return maxCents <= 0L || maxCents > PRECISION_LIMIT_CENTS ? PRECISION_LIMIT_CENTS : maxCents;
    }

    /** The fee on a payment, {@code percent} of it rounded down to the cent, never the whole payment. */
    public static long fee(long amountCents, double percent) {
        if (!Double.isFinite(percent) || percent <= 0D || amountCents <= 0L) {
            return 0L;
        }
        double clamped = Math.min(100D, percent);
        long fee = BigDecimal.valueOf(amountCents).multiply(BigDecimal.valueOf(clamped))
                .divide(BigDecimal.valueOf(100L), 0, RoundingMode.DOWN).longValue();
        return Math.min(fee, amountCents);
    }

    /**
     * Reads an amount a player typed: digits with an optional decimal point and an optional
     * K/M/B/T/Q suffix ("1.5k", "250", "2m"). Commas and underscores between digits are ignored.
     * Empty for anything else, including NaN, "Infinity", hex, exponents, negative or zero amounts,
     * and amounts that round to less than a cent.
     */
    public static OptionalLong parsePositive(String input) {
        if (input == null) {
            return OptionalLong.empty();
        }
        String clean = input.trim().replace(",", "").replace("_", "").toLowerCase(Locale.ROOT);
        if (clean.startsWith("$")) {
            clean = clean.substring(1);
        }
        if (clean.isEmpty() || clean.length() > 32) {
            return OptionalLong.empty();
        }
        int exponent = 0;
        char last = clean.charAt(clean.length() - 1);
        switch (last) {
            case 'k' -> exponent = 3;
            case 'm' -> exponent = 6;
            case 'b' -> exponent = 9;
            case 't' -> exponent = 12;
            case 'q' -> exponent = 15;
            default -> {
            }
        }
        if (exponent > 0) {
            clean = clean.substring(0, clean.length() - 1);
        }
        if (!clean.matches("\\d+(\\.\\d+)?|\\.\\d+")) {
            return OptionalLong.empty();
        }
        BigDecimal cents;
        try {
            cents = new BigDecimal(clean).movePointRight(exponent + 2).setScale(0, RoundingMode.HALF_UP);
        } catch (NumberFormatException | ArithmeticException exception) {
            return OptionalLong.empty();
        }
        if (cents.signum() <= 0 || cents.compareTo(BigDecimal.valueOf(PRECISION_LIMIT_CENTS)) > 0) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(cents.longValueExact());
    }

    /** Plain "1234.50" text for a log or the ledger. */
    public static String plain(long cents) {
        return BigDecimal.valueOf(cents, 2).toPlainString();
    }
}
