package `in`.c1ph3rj.scanly.feature.widget

import android.content.Context
import android.widget.RemoteViews
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.feature.launch.ScanlyLaunchAction

/**
 * Horizontal multi-action widget: Scan · Import · QR · Library.
 */
class ScanlyActionsWidgetProvider : ScanlyBaseWidgetProvider() {
    override fun buildViews(context: Context): RemoteViews = Companion.buildViews(context)

    companion object {
        private const val REQUEST_SCAN = 201
        private const val REQUEST_IMPORT = 202
        private const val REQUEST_QR = 203
        private const val REQUEST_LIBRARY = 204

        fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_actions)
            views.setOnClickPendingIntent(
                R.id.widget_action_scan,
                ScanlyWidgetIntents.pendingIntent(context, ScanlyLaunchAction.Scan, REQUEST_SCAN),
            )
            views.setOnClickPendingIntent(
                R.id.widget_action_import,
                ScanlyWidgetIntents.pendingIntent(context, ScanlyLaunchAction.Import, REQUEST_IMPORT),
            )
            views.setOnClickPendingIntent(
                R.id.widget_action_qr,
                ScanlyWidgetIntents.pendingIntent(context, ScanlyLaunchAction.Qr, REQUEST_QR),
            )
            views.setOnClickPendingIntent(
                R.id.widget_action_library,
                ScanlyWidgetIntents.pendingIntent(context, ScanlyLaunchAction.Library, REQUEST_LIBRARY),
            )
            return views
        }
    }
}
