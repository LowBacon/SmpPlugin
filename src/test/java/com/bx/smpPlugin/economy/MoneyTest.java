package com.bx.smpPlugin.economy;

import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyTest {

    @Test
    void roundsHalfUpToTheNearestCent() {
        assertEquals(1L, Money.toCents(0.005D).getAsLong());
        assertEquals(101L, Money.toCents(1.005D).getAsLong());
        assertEquals(0L, Money.toCents(0.0049D).getAsLong());
        assertEquals(1000L, Money.toCents(10.000000001D).getAsLong());
        assertEquals(-250L, Money.toCents(-2.5D).getAsLong());
    }

    @Test
    void refusesValuesThatCannotBeExact() {
        assertTrue(Money.toCents(Double.NaN).isEmpty());
        assertTrue(Money.toCents(Double.POSITIVE_INFINITY).isEmpty());
        assertTrue(Money.toCents(1e18D).isEmpty());
        assertTrue(Money.toCents(-1e18D).isEmpty());
        assertTrue(Money.toCents(Money.MAX_EXACT_CENTS / 100D).isPresent());
    }

    @Test
    void everyCentValueSurvivesTheRealColumn() {
        // A stored balance is a double; reading it back must give exactly the cents that were written.
        SplittableRandom random = new SplittableRandom(42);
        for (int i = 0; i < 200_000; i++) {
            long cents = random.nextLong(Money.MAX_EXACT_CENTS);
            assertEquals(cents, Money.toCents(Money.toUnits(cents)).getAsLong(), "cents " + cents);
        }
        for (long cents = 0; cents < 100_000; cents++) {
            assertEquals(cents, Money.toCents(Money.toUnits(cents)).getAsLong());
        }
        // The top of the range, where neighbouring doubles are furthest apart.
        for (long cents = Money.MAX_EXACT_CENTS - 100_000; cents <= Money.MAX_EXACT_CENTS; cents++) {
            assertEquals(cents, Money.toCents(Money.toUnits(cents)).getAsLong(), "cents " + cents);
        }
    }

    @Test
    void justAboveTheExactRangeCentsWouldBeLost() {
        // Documents why the limit is 2^45 units: at 2^46 units neighbouring doubles are 1.56 cents apart.
        long above = (1L << 46) * Money.CENTS_PER_UNIT + 1L;
        assertTrue(Money.toCents(Money.toUnits(above)).isEmpty(), "refused instead of silently rounded");
    }

    @Test
    void readsOldFloatNoiseAndBadValues() {
        assertEquals(1999L, Money.storedToCents(19.989999999999998D));
        assertEquals(0L, Money.storedToCents(-5D));
        assertEquals(0L, Money.storedToCents(Double.NaN));
        assertEquals(Money.MAX_EXACT_CENTS, Money.storedToCents(1e300D));
    }

    @Test
    void percentagesRoundToCents() {
        assertEquals(5L, Money.percentOf(100L, 5D));
        assertEquals(1L, Money.percentOf(10L, 5D)); // 0.5 cent rounds up
        assertEquals(0L, Money.percentOf(9L, 5D));
        assertEquals(100L, Money.percentOf(100L, 250D)); // clamped to 100%
        assertEquals(0L, Money.percentOf(100L, Double.NaN));
    }

    @Test
    void additionStaysInsideTheExactRange() {
        assertEquals(300L, Money.add(100L, 200L).getAsLong());
        assertTrue(Money.add(Money.MAX_EXACT_CENTS, 1L).isEmpty());
        assertTrue(Money.add(Long.MAX_VALUE, 1L).isEmpty());
    }
}
