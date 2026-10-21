package `in`.c1ph3rj.scanly.feature.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat

enum class CameraPermissionStatus {
    Granted,
    NotRequested,
    DeniedCanRetry,
    PermanentlyDenied,
}

object CameraPermissionSupport {
    private const val preferencesName = "scanly_camera_permission"
    private const val requestedKey = "camera_permission_requested"
    private const val permanentlyDeniedKey = "camera_permission_permanently_denied"

    fun isGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    fun hasRequestedBefore(context: Context): Boolean =
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .getBoolean(requestedKey, false)

    /** True only after a system prompt result came back denied with no rationale left to show. */
    fun isPermanentlyDeniedRecorded(context: Context): Boolean =
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .getBoolean(permanentlyDeniedKey, false)

    /**
     * Call from the permission launcher callback. The rationale flag is only trustworthy right
     * after a real prompt result, so the "permanently denied" outcome is recorded here instead
     * of being inferred later (it is also false after a one-time grant expires or "Ask every time").
     */
    fun markRequested(context: Context) {
        val permanentlyDenied = !isGranted(context) &&
            findActivity(context)
                ?.let { !it.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) } == true
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(requestedKey, true)
            .putBoolean(permanentlyDeniedKey, permanentlyDenied)
            .apply()
    }

    private fun clearPermanentlyDenied(context: Context) {
        if (!isPermanentlyDeniedRecorded(context)) return
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(permanentlyDeniedKey, false)
            .apply()
    }

    fun findActivity(context: Context): Activity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    fun resolveStatus(
        isGranted: Boolean,
        shouldShowRationale: Boolean,
        hasRequestedBefore: Boolean,
        canInspectRationale: Boolean,
        permanentlyDeniedRecorded: Boolean = true,
    ): CameraPermissionStatus {
        if (isGranted) {
            return CameraPermissionStatus.Granted
        }
        if (shouldShowRationale) {
            return CameraPermissionStatus.DeniedCanRetry
        }
        if (!hasRequestedBefore) {
            return CameraPermissionStatus.NotRequested
        }
        // Without an Activity we cannot tell "never asked" leftovers from a
        // permanent block — keep retrying the system prompt instead of Settings.
        if (!canInspectRationale) {
            return CameraPermissionStatus.DeniedCanRetry
        }
        // Not granted and no rationale is also what an expired one-time grant or "Ask every time"
        // looks like; only a recorded denied prompt result proves the dialog will no longer show.
        if (!permanentlyDeniedRecorded) {
            return CameraPermissionStatus.DeniedCanRetry
        }
        return CameraPermissionStatus.PermanentlyDenied
    }

    fun resolveStatus(activity: Activity?, context: Context): CameraPermissionStatus {
        val resolvedActivity = activity ?: findActivity(context)
        val granted = isGranted(context)
        val shouldShowRationale = resolvedActivity
            ?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == true
        // A granted or rationale-eligible state means the system dialog works again.
        if (granted || shouldShowRationale) clearPermanentlyDenied(context)
        return resolveStatus(
            isGranted = granted,
            shouldShowRationale = shouldShowRationale,
            hasRequestedBefore = hasRequestedBefore(context),
            canInspectRationale = resolvedActivity != null,
            permanentlyDeniedRecorded = isPermanentlyDeniedRecorded(context),
        )
    }

    fun shouldRequestSystemPermission(status: CameraPermissionStatus): Boolean =
        status == CameraPermissionStatus.NotRequested ||
            status == CameraPermissionStatus.DeniedCanRetry

    fun shouldAutoRequestSystemPermission(status: CameraPermissionStatus): Boolean =
        status == CameraPermissionStatus.NotRequested

    fun shouldOpenSettings(status: CameraPermissionStatus): Boolean =
        status == CameraPermissionStatus.PermanentlyDenied

    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}