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

    fun isGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    fun hasRequestedBefore(context: Context): Boolean =
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .getBoolean(requestedKey, false)

    fun markRequested(context: Context) {
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(requestedKey, true)
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
        return CameraPermissionStatus.PermanentlyDenied
    }

    fun resolveStatus(activity: Activity?, context: Context): CameraPermissionStatus {
        val resolvedActivity = activity ?: findActivity(context)
        return resolveStatus(
            isGranted = isGranted(context),
            shouldShowRationale = resolvedActivity
                ?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == true,
            hasRequestedBefore = hasRequestedBefore(context),
            canInspectRationale = resolvedActivity != null,
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