// SmpPlugin by LowBacon
package com.bx.smpPlugin.economy;

import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
        // Up to Long.MAX_VALUE cents (about 92 quadrillion) is a balance; above it there is nothing to hold it.
        assertTrue(Money.toCents(9.2e16D).isPresent());
        assertTrue(Money.toCents(9.3e16D).isEmpty());
        assertEquals(Long.MAX_VALUE, Money.MAX_CENTS);
    }

    @Test
    void everyCentValueBelowThirtyFiveTrillionSurvivesADouble() {
        // Vault and the money column use doubles; below 2^45 units they still carry every cent exactly.
        SplittableRandom random = new SplittableRandom(42);
        for (int i = 0; i < 200_000; i++) {
            long cents = random.nextLong(Money.DOUBLE_EXACT_CENTS);
            assertEquals(cents, Money.toCents(Money.toUnits(cents)).getAsLong(), "cents " + cents);
        }
        for (long cents = 0; cents < 100_000; cents++) {
            assertEquals(cents, Money.toCents(Money.toUnits(cents)).getAsLong());
        }
        // The top of the range, where neighbouring doubles are furthest apart.
        for (long cents = Money.DOUBLE_EXACT_CENTS - 100_000; cents <= Money.DOUBLE_EXACT_CENTS; cents++) {
            assertEquals(cents, Money.toCents(Money.toUnits(cents)).getAsLong(), "cents " + cents);
        }
    }

    @Test
    void aboveThirtyFiveTrillionADoubleCannotCarryEveryCent() {
        // Documents why balances are kept as whole cents (players.money_cents) and not as a double: at 2^46 units
        // neighbouring doubles are 1.56 cents apart, so the double alone would lose the cent.
        long above = (1L << 46) * Money.CENTS_PER_UNIT + 1L;
        assertNotEquals(above, Money.toCents(Money.toUnits(above)).getAsLong());
    }

    @Test
    void aBalanceReadAsADoubleIsNeverOverstated() {
        // Vault, /balance and placeholders read a balance as a double. Above 35 trillion it can be a cent or a few
        // units short, but never more than the player has, so another plugin withdrawing "everything" succeeds.
        SplittableRandom random = new SplittableRandom(7);
        for (int i = 0; i < 100_000; i++) {
            long cents = Money.DOUBLE_EXACT_CENTS + random.nextLong(Money.MAX_CENTS - Money.DOUBLE_EXACT_CENTS);
            long back = Money.toCents(Money.toUnits(cents)).getAsLong();
            assertTrue(back <= cents && cents - back < 4_000L, "cents " + cents + " read back as " + back);
        }
        long top = Money.toCents(Money.toUnits(Money.MAX_CENTS)).getAsLong();
        assertTrue(top <= Money.MAX_CENTS && Money.MAX_CENTS - top < 4_000L);
        assertTrue(Money.toCents(Money.toUnits(-Money.MAX_CENTS)).getAsLong() >= -Money.MAX_CENTS);
    }

    @Test
    void readsOldFloatNoiseAndBadValues() {
        assertEquals(1999L, Money.storedToCents(19.989999999999998D));
        assertEquals(0L, Money.storedToCents(-5D));
        assertEquals(0L, Money.storedToCents(Double.NaN));
        assertEquals(Money.MAX_CENTS, Money.storedToCents(1e300D));
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
    void additionIsExactUpToTheLargestBalance() {
        assertEquals(300L, Money.add(100L, 200L).getAsLong());
        assertEquals(1_234_567_890_123_456_790L, Money.add(1_234_567_890_123_456_789L, 1L).getAsLong());
        assertEquals(Long.MAX_VALUE, Money.add(Long.MAX_VALUE - 1L, 1L).getAsLong());
        assertTrue(Money.add(Long.MAX_VALUE, 1L).isEmpty());
    }
}
