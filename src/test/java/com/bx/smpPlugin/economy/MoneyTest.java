package com.bx.smpPlugin.economy;

import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyTest {

    @Test
    void amountsRoundToTheNearestCent() {
        assertEquals(OptionalLong.of(1234L), Money.toCents(12.34D));
        assertEquals(OptionalLong.of(1L), Money.toCents(0.005D));
        assertEquals(OptionalLong.of(0L), Money.toCents(0.004D));
        // 0.1 + 0.2 is 0.30000000000000004 as a double; in cents it is exactly 30.
        assertEquals(OptionalLong.of(30L), Money.toCents(0.1D + 0.2D));
    }

    @Test
    void badAmountsAreRefused() {
        assertTrue(Money.toCents(Double.NaN).isEmpty());
        assertTrue(Money.toCents(Double.POSITIVE_INFINITY).isEmpty());
        assertTrue(Money.toCents(-1D).isEmpty());
        assertTrue(Money.toCents(1e30D).isEmpty());
    }

    @Test
    void storedBalancesNeverGoNegativeOrNaN() {
        assertEquals(0L, Money.balanceCents(Double.NaN));
        assertEquals(0L, Money.balanceCents(-50D));
        assertEquals(Money.PRECISION_LIMIT_CENTS, Money.balanceCents(1e20D));
        assertEquals(1050L, Money.balanceCents(10.5D));
    }

    @Test
    void addingRespectsTheLimitAndNeverOverflows() {
        assertEquals(OptionalLong.of(300L), Money.add(100L, 200L, 0L));
        assertTrue(Money.add(100L, 200L, 250L).isEmpty(), "past MAX-BALANCE");
        assertEquals(OptionalLong.of(250L), Money.add(100L, 150L, 250L), "exactly at the limit is fine");
        assertTrue(Money.add(Long.MAX_VALUE - 1, 10L, 0L).isEmpty(), "overflow");
        assertTrue(Money.add(Money.PRECISION_LIMIT_CENTS, 1L, 0L).isEmpty(), "past the precision limit");
        assertTrue(Money.add(10L, -5L, 0L).isEmpty(), "a negative deposit");
    }

    @Test
    void subtractingNeverGoesBelowZero() {
        assertEquals(OptionalLong.of(50L), Money.subtract(100L, 50L));
        assertEquals(OptionalLong.of(0L), Money.subtract(100L, 100L));
        assertTrue(Money.subtract(100L, 101L).isEmpty());
        assertTrue(Money.subtract(100L, -1L).isEmpty());
    }

    @Test
    void feesRoundDownAndNeverExceedThePayment() {
        assertEquals(500L, Money.fee(10_000L, 5D));
        assertEquals(3L, Money.fee(99L, 3.5D), "3.465 cents rounds down");
        assertEquals(0L, Money.fee(10_000L, 0D));
        assertEquals(10_000L, Money.fee(10_000L, 250D), "capped at 100%");
        assertEquals(0L, Money.fee(10_000L, Double.NaN));
    }

    @Test
    void typedAmountsAreParsedStrictly() {
        assertEquals(OptionalLong.of(25_000L), Money.parsePositive("250"));
        assertEquals(OptionalLong.of(150_000L), Money.parsePositive("1.5k"));
        assertEquals(OptionalLong.of(200_000_000L), Money.parsePositive("2M"));
        assertEquals(OptionalLong.of(123_456_700L), Money.parsePositive("1,234,567"));
        assertEquals(OptionalLong.of(1999L), Money.parsePositive("$19.99"));
        assertEquals(OptionalLong.of(50L), Money.parsePositive(".5"));
        for (String bad : new String[]{"NaN", "Infinity", "-5", "0", "0.001", "1e5", "0x10", "", "abc", "5kk", null}) {
            assertTrue(Money.parsePositive(bad).isEmpty(), "should refuse " + bad);
        }
        assertTrue(Money.parsePositive("999999999999q").isEmpty(), "past the precision limit");
    }

    @Test
    void plainTextKeepsTwoDecimals() {
        assertEquals("12.30", Money.plain(1230L));
        assertEquals("0.05", Money.plain(5L));
    }
}
