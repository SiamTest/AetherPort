package com.forgeport.android.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {
    @Test
    fun alphaBuildsIncreaseNumerically() {
        assertTrue(VersionComparator.isNewer("3.0.0-alpha07", "3.0.0-alpha06"))
        assertFalse(VersionComparator.isNewer("3.0.0-alpha05", "3.0.0-alpha06"))
    }

    @Test
    fun stableReleaseIsNewerThanPrerelease() {
        assertTrue(VersionComparator.isNewer("3.0.0", "3.0.0-alpha15"))
        assertFalse(VersionComparator.isNewer("3.0.0-alpha15", "3.0.0"))
        assertTrue(VersionComparator.isNewer("3.0.0", "3.0.0-rc2"))
        assertTrue(VersionComparator.isNewer("3.0.0-rc1", "3.0.0-beta9"))
    }

    @Test
    fun vPrefixAndMissingPatchComponentsAreHandled() {
        assertTrue(VersionComparator.isNewer("v3.1", "3.0.9"))
        assertFalse(VersionComparator.isNewer("v3.0.0", "3.0.0"))
    }
}
