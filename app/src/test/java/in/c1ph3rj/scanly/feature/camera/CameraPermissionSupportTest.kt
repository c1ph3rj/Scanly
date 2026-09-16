package `in`.c1ph3rj.scanly.feature.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraPermissionSupportTest {
    @Test
    fun firstLaunchIsNotRequestedAndAsksTheSystemNotSettings() {
        val status = CameraPermissionSupport.resolveStatus(
            isGranted = false,
            shouldShowRationale = false,
            hasRequestedBefore = false,
            canInspectRationale = true,
        )
        assertEquals(CameraPermissionStatus.NotRequested, status)
        assertTrue(CameraPermissionSupport.shouldAutoRequestSystemPermission(status))
        assertTrue(CameraPermissionSupport.shouldRequestSystemPermission(status))
        assertFalse(CameraPermissionSupport.shouldOpenSettings(status))
    }

    @Test
    fun deniedWithRationaleRetriesTheSystemPrompt() {
        val status = CameraPermissionSupport.resolveStatus(
            isGranted = false,
            shouldShowRationale = true,
            hasRequestedBefore = true,
            canInspectRationale = true,
        )
        assertEquals(CameraPermissionStatus.DeniedCanRetry, status)
        assertFalse(CameraPermissionSupport.shouldAutoRequestSystemPermission(status))
        assertTrue(CameraPermissionSupport.shouldRequestSystemPermission(status))
        assertFalse(CameraPermissionSupport.shouldOpenSettings(status))
    }

    @Test
    fun permanentBlockIsTheOnlyPathToSettings() {
        val status = CameraPermissionSupport.resolveStatus(
            isGranted = false,
            shouldShowRationale = false,
            hasRequestedBefore = true,
            canInspectRationale = true,
        )
        assertEquals(CameraPermissionStatus.PermanentlyDenied, status)
        assertFalse(CameraPermissionSupport.shouldAutoRequestSystemPermission(status))
        assertFalse(CameraPermissionSupport.shouldRequestSystemPermission(status))
        assertTrue(CameraPermissionSupport.shouldOpenSettings(status))
    }

    @Test
    fun missingActivityDoesNotTreatAPriorRequestAsPermanent() {
        val status = CameraPermissionSupport.resolveStatus(
            isGranted = false,
            shouldShowRationale = false,
            hasRequestedBefore = true,
            canInspectRationale = false,
        )
        assertEquals(CameraPermissionStatus.DeniedCanRetry, status)
        assertTrue(CameraPermissionSupport.shouldRequestSystemPermission(status))
        assertFalse(CameraPermissionSupport.shouldOpenSettings(status))
    }

    @Test
    fun grantedSkipsPrompts() {
        val status = CameraPermissionSupport.resolveStatus(
            isGranted = true,
            shouldShowRationale = false,
            hasRequestedBefore = true,
            canInspectRationale = true,
        )
        assertEquals(CameraPermissionStatus.Granted, status)
        assertFalse(CameraPermissionSupport.shouldAutoRequestSystemPermission(status))
        assertFalse(CameraPermissionSupport.shouldRequestSystemPermission(status))
        assertFalse(CameraPermissionSupport.shouldOpenSettings(status))
    }

    @Test
    fun shouldOpenSettings_onlyForPermanentlyDenied() {
        assertTrue(CameraPermissionSupport.shouldOpenSettings(CameraPermissionStatus.PermanentlyDenied))
        assertFalse(CameraPermissionSupport.shouldOpenSettings(CameraPermissionStatus.NotRequested))
        assertFalse(CameraPermissionSupport.shouldOpenSettings(CameraPermissionStatus.DeniedCanRetry))
        assertFalse(CameraPermissionSupport.shouldOpenSettings(CameraPermissionStatus.Granted))
    }
}
