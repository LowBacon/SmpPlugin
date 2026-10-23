// SmpPlugin by LowBacon
package com.bx.smpPlugin.coinflip;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class StakeMath {

    public static final long HARD_MAX_STAKE_CENTS = 100_000_000_000_000L;
    public static final int MAX_FEE_BASIS_POINTS = 5_000;

    private StakeMath() {
    }

    public enum ParseError {
        EMPTY, MALFORMED, NEGATIVE, TOO_MANY_DECIMALS, TOO_LARGE, ZERO
    }

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

    public static long winnerNet(long stakeCents, int basisPoints) {
        return payout(stakeCents, basisPoints) - stakeCents;
    }

    public static double toEconomy(long cents) {
        return BigDecimal.valueOf(cents, 2).doubleValue();
    }

    public static long fromEconomy(double amount) {
        if (!Double.isFinite(amount) || amount <= 0D) {
            return 0L;
        }
        BigDecimal cents = BigDecimal.valueOf(amount).movePointRight(2).setScale(0, RoundingMode.FLOOR);
        return cents.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) >= 0 ? Long.MAX_VALUE : cents.longValueExact();
    }

    public static String format(long cents) {
        DecimalFormat format = new DecimalFormat(cents % 100L == 0L ? "#,##0" : "#,##0.00",
                DecimalFormatSymbols.getInstance(Locale.ROOT));
        return format.format(BigDecimal.valueOf(cents, 2));
    }

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

    public static String percent(int basisPoints) {
        return BigDecimal.valueOf(basisPoints, 2).stripTrailingZeros().toPlainString() + "%";
    }
}
