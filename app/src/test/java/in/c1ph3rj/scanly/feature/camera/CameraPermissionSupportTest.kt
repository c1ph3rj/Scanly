package `in`.c1ph3rj.scanly.feature.camera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraPermissionSupportTest {
    @Test
    fun shouldOpenSettings_onlyForPermanentlyDenied() {
        assertTrue(CameraPermissionSupport.shouldOpenSettings(CameraPermissionStatus.PermanentlyDenied))
        assertFalse(CameraPermissionSupport.shouldOpenSettings(CameraPermissionStatus.NotRequested))
        assertFalse(CameraPermissionSupport.shouldOpenSettings(CameraPermissionStatus.DeniedCanRetry))
        assertFalse(CameraPermissionSupport.shouldOpenSettings(CameraPermissionStatus.Granted))
    }
}