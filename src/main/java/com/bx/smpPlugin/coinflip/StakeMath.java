package com.bx.smpPlugin.coinflip;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Exact money arithmetic for CoinFlip. Amounts are whole cents in a {@code long}; the server economy keeps
 * two decimal places, so a cent is the smallest unit it can represent. Conversion to the economy's
 * {@code double} happens only at its boundary and is exact for every amount CoinFlip allows (below 2^53 cents).
 *
 * <p>For a standard match with stake {@code A} and fee rate {@code f}: pot = 2A, fee = floor(2A * f),
 * winner receives 2A - fee, winner's net result A - fee, loser loses A. The fee is the only currency removed.</p>
 */
public final class StakeMath {

    /** Largest stake CoinFlip will ever accept: one trillion coins. Keeps the pot far below 2^53 cents. */
    public static final long HARD_MAX_STAKE_CENTS = 100_000_000_000_000L;
    public static final int MAX_FEE_BASIS_POINTS = 5_000;

    private StakeMath() {
    }

    /** Why a typed amount was rejected. */
    public enum ParseError {
        EMPTY, MALFORMED, NEGATIVE, TOO_MANY_DECIMALS, TOO_LARGE, ZERO
    }

    /** Either an amount in cents or the reason it was rejected. */
    public record ParseResult(long cents, ParseError error) {
        public boolean ok() {
            return error == null;
        }

        static ParseResult of(long cents) {
            return new ParseResult(cents, null);
        }

        static ParseResult fail(ParseError error) {
            return new ParseResult(0L, error);
        }
    }

    /**
     * Parses a player-typed amount. Accepts digits with optional thousands separators (comma, space,
     * underscore, apostrophe), at most two decimals, and an optional k / m / b suffix ("2.5k" = 2500).
     * Rejects negative numbers, scientific notation, NaN/Infinity and anything that would overflow.
     */
    public static ParseResult parse(String input) {
        if (input == null) {
            return ParseResult.fail(ParseError.EMPTY);
        }
        String text = input.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return ParseResult.fail(ParseError.EMPTY);
        }
        if (text.startsWith("-")) {
            return ParseResult.fail(ParseError.NEGATIVE);
        }
        if (text.startsWith("+")) {
            text = text.substring(1);
        }
        long multiplier = 1L;
        char last = text.charAt(text.length() - 1);
        if (last == 'k' || last == 'm' || last == 'b') {
            multiplier = last == 'k' ? 1_000L : last == 'm' ? 1_000_000L : 1_000_000_000L;
            text = text.substring(0, text.length() - 1).trim();
        }
        text = text.replace(",", "").replace(" ", "").replace("_", "").replace("'", "");
        if (text.isEmpty() || !text.matches("\\d+(\\.\\d*)?|\\.\\d+")) {
            return ParseResult.fail(ParseError.MALFORMED);
        }
        if (text.length() > 30) {
            return ParseResult.fail(ParseError.TOO_LARGE);
        }
        BigDecimal value = new BigDecimal(text).multiply(BigDecimal.valueOf(multiplier));
        BigDecimal cents = value.movePointRight(2);
        if (cents.stripTrailingZeros().scale() > 0) {
            return ParseResult.fail(ParseError.TOO_MANY_DECIMALS);
        }
        if (cents.compareTo(BigDecimal.valueOf(HARD_MAX_STAKE_CENTS)) > 0) {
            return ParseResult.fail(ParseError.TOO_LARGE);
        }
        long result = cents.longValueExact();
        if (result == 0L) {
            return ParseResult.fail(ParseError.ZERO);
        }
        return ParseResult.of(result);
    }

    public static long pot(long stakeCents) {
        return Math.multiplyExact(stakeCents, 2L);
    }

    /** Fee on the pot, rounded down to a whole cent so players are never charged more than the stated rate. */
    public static long fee(long potCents, int basisPoints) {
        if (basisPoints <= 0) {
            return 0L;
        }
        return Math.multiplyExact(potCents, (long) basisPoints) / 10_000L;
    }

    public static long payout(long stakeCents, int basisPoints) {
        long pot = pot(stakeCents);
        return pot - fee(pot, basisPoints);
    }

    /** What the winner gains relative to their own stake. */
    public static long winnerNet(long stakeCents, int basisPoints) {
        return payout(stakeCents, basisPoints) - stakeCents;
    }

    /** Converts cents to the economy's two-decimal double; exact below 2^53 cents. */
    public static double toEconomy(long cents) {
        return BigDecimal.valueOf(cents, 2).doubleValue();
    }

    /** Converts an economy balance to whole cents, rounding down so a balance is never overstated. */
    public static long fromEconomy(double amount) {
        if (!Double.isFinite(amount) || amount <= 0D) {
            return 0L;
        }
        return BigDecimal.valueOf(amount).movePointRight(2).setScale(0, RoundingMode.FLOOR).longValue();
    }

    /** "1,234" or "1,234.50". */
    public static String format(long cents) {
        DecimalFormat format = new DecimalFormat(cents % 100L == 0L ? "#,##0" : "#,##0.00",
                DecimalFormatSymbols.getInstance(Locale.ROOT));
        return format.format(BigDecimal.valueOf(cents, 2));
    }

    /** "1.2k", "3.5M" for tight spaces such as item names. */
    public static String compact(long cents) {
        double value = cents / 100D;
        String[] units = {"", "k", "M", "B", "T"};
        int unit = 0;
        while (Math.abs(value) >= 1_000D && unit < units.length - 1) {
            value /= 1_000D;
            unit++;
        }
        if (unit == 0) {
            return format(cents);
        }
        String number = value >= 100D ? String.format(Locale.ROOT, "%.0f", value)
                : String.format(Locale.ROOT, "%.1f", value).replace(".0", "");
        return number + units[unit];
    }

    /** Basis points as a percentage string, "2.5%". */
    public static String percent(int basisPoints) {
        return BigDecimal.valueOf(basisPoints, 2).stripTrailingZeros().toPlainString() + "%";
    }
}
