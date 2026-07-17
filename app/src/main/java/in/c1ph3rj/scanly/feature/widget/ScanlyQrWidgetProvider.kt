package `in`.c1ph3rj.scanly.feature.widget

import android.content.Context
import android.widget.RemoteViews
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.feature.launch.ScanlyLaunchAction

/** Compact 1x1 widget that opens the QR tool. */
class ScanlyQrWidgetProvider : ScanlyBaseWidgetProvider() {
    override fun buildViews(context: Context): RemoteViews = Companion.buildViews(context)

    companion object {
        private const val REQUEST_QR = 221

        fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_qr)
            views.setOnClickPendingIntent(
                R.id.widget_qr_root,
                ScanlyWidgetIntents.pendingIntent(context, ScanlyLaunchAction.Qr, REQUEST_QR),
            )
            return views
        }
    }
}
