package `in`.c1ph3rj.scanly.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionComparatorTest {
    @Test
    fun compare_treatsVPrefixedVersionAsSameVersion() {
        assertEquals(0, AppVersionComparator.compare("v1.0.4", "1.0.4"))
    }

    @Test
    fun isRemoteNewer_detectsNewPatchRelease() {
        assertTrue(AppVersionComparator.isRemoteNewer("1.0.3", "v1.0.4"))
    }

    @Test
    fun isRemoteNewer_doesNotPreferPrereleaseOverStableSameCoreVersion() {
        assertFalse(AppVersionComparator.isRemoteNewer("1.0.4", "v1.0.4-beta"))
    }
}
