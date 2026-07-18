package `in`.c1ph3rj.scanly.feature.widget

import android.app.PendingIntent
import android.content.Context
import `in`.c1ph3rj.scanly.feature.launch.ScanlyLaunchAction

internal object ScanlyWidgetIntents {
    fun pendingIntent(
        context: Context,
        action: ScanlyLaunchAction,
        requestCode: Int,
    ): PendingIntent {
        val intent = ScanlyLaunchAction.createIntent(context, action)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
