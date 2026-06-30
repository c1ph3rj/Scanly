package `in`.c1ph3rj.scanly.feature.camera

import android.Manifest
import android.app.Activity
import android.content.Context
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

    fun resolveStatus(activity: Activity?, context: Context): CameraPermissionStatus {
        if (isGranted(context)) {
            return CameraPermissionStatus.Granted
        }

        val shouldShowRationale = activity
            ?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == true
        if (shouldShowRationale) {
            return CameraPermissionStatus.DeniedCanRetry
        }

        return if (hasRequestedBefore(context)) {
            CameraPermissionStatus.PermanentlyDenied
        } else {
            CameraPermissionStatus.NotRequested
        }
    }

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