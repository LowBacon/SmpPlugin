// SmpPlugin by LowBacon
package com.bx.smpPlugin.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NumberUtilsParseTest {

    @Test
    void amountsWithSuffixesStillParse() {
        assertEquals(1500D, NumberUtils.parse("1.5k"));
        assertEquals(2_000_000D, NumberUtils.parse("2M"));
        assertEquals(1234D, NumberUtils.parse("1,234"));
    }

    @Test
    void nonFiniteInputIsRefusedForEveryCaller() {
        assertThrows(NumberFormatException.class, () -> NumberUtils.parse("NaN"));
        assertThrows(NumberFormatException.class, () -> NumberUtils.parse("Infinity"));
        assertThrows(NumberFormatException.class, () -> NumberUtils.parse("-Infinity"));
        assertThrows(NumberFormatException.class, () -> NumberUtils.parse("1e308Q"), "overflows to infinity");
        assertFalse(NumberUtils.isValidPositiveAmount("NaN"));
    }
}
