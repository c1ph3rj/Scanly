package `in`.c1ph3rj.scanly.feature.widget

import android.content.Context
import android.widget.RemoteViews
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.feature.launch.ScanlyLaunchAction

/** Compact 1x1 widget that starts a new camera scan. */
class ScanlyScanWidgetProvider : ScanlyBaseWidgetProvider() {
    override fun buildViews(context: Context): RemoteViews = Companion.buildViews(context)

    companion object {
        private const val REQUEST_SCAN = 211

        fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_scan)
            views.setOnClickPendingIntent(
                R.id.widget_scan_root,
                ScanlyWidgetIntents.pendingIntent(context, ScanlyLaunchAction.Scan, REQUEST_SCAN),
            )
            return views
        }
    }
}
