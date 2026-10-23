// SmpPlugin by LowBacon
package com.bx.smpPlugin.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BorderHintTest {

    private static final double NO_BORDER = 59_999_968D;

    @Test
    void addingToAWorldWithoutABorderIsExplained() {
        assertEquals(141D, BorderManager.mistakenVanillaAdd("/worldborder add 141", NO_BORDER));
        assertEquals(141D, BorderManager.mistakenVanillaAdd("/minecraft:worldborder add 141 10", NO_BORDER));
        assertEquals(1D, BorderManager.mistakenVanillaAdd("/WorldBorder ADD 1", NO_BORDER));
    }

    @Test
    void commandsThatWorkAreLeftAlone() {
        assertNull(BorderManager.mistakenVanillaAdd("/worldborder add 141", 200D), "a real border can grow");
        assertNull(BorderManager.mistakenVanillaAdd("/worldborder set 141", NO_BORDER));
        assertNull(BorderManager.mistakenVanillaAdd("/worldborder add -50", NO_BORDER), "shrinking works");
        assertNull(BorderManager.mistakenVanillaAdd("/worldborder add lots", NO_BORDER));
        assertNull(BorderManager.mistakenVanillaAdd("/border spawn add 141", NO_BORDER));
        assertNull(BorderManager.mistakenVanillaAdd("/worldborder", NO_BORDER));
    }
}
