// SmpPlugin by LowBacon
package com.bx.smpPlugin.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionComparatorTest {

    @Test
    void releaseIsNewerThanItsPreReleases() {
        assertTrue(VersionComparator.isNewer("2.0.0", "2.0.0-alpha.5"));
        assertFalse(VersionComparator.isNewer("2.0.0-alpha.5", "2.0.0"));
    }

    @Test
    void numbersCompareAsNumbers() {
        assertTrue(VersionComparator.isNewer("2.0.10", "2.0.9"));
        assertTrue(VersionComparator.isNewer("2.1", "2.0.9"));
        assertTrue(VersionComparator.isNewer("2.0.0-alpha.10", "2.0.0-alpha.9"));
        assertTrue(VersionComparator.isNewer("2.0.0-beta.1", "2.0.0-alpha.9"));
    }

    @Test
    void equalVersionsAndPrefixesMatch() {
        assertEquals(0, VersionComparator.compare("v2.0.0", "2.0.0"));
        assertEquals(0, VersionComparator.compare("2.0", "2.0.0"));
        assertFalse(VersionComparator.isNewer("2.0.0", "2.0.0"));
        assertFalse(VersionComparator.isNewer(null, "2.0.0"));
    }
}
