package `in`.c1ph3rj.scanly.feature.launch

import android.content.Context
import android.content.Intent
import `in`.c1ph3rj.scanly.MainActivity

/**
 * External entry points from home-screen widgets and launcher quick actions.
 * Explicit [MainActivity] intents carry [intentAction] so redirects stay in-process
 * without relying on browsable deep links.
 */
enum class ScanlyLaunchAction(
    val intentAction: String,
) {
    Scan("in.c1ph3rj.scanly.action.SCAN"),
    Import("in.c1ph3rj.scanly.action.IMPORT"),
    Qr("in.c1ph3rj.scanly.action.QR"),
    Library("in.c1ph3rj.scanly.action.LIBRARY"),
    ;

    companion object {
        const val EXTRA_LAUNCH_ACTION = "in.c1ph3rj.scanly.extra.LAUNCH_ACTION"

        fun fromIntent(intent: Intent?): ScanlyLaunchAction? =
            fromActionAndExtra(
                action = intent?.action,
                extra = intent?.getStringExtra(EXTRA_LAUNCH_ACTION),
            )

        fun fromActionAndExtra(action: String?, extra: String?): ScanlyLaunchAction? {
            val fromAction = entries.firstOrNull { it.intentAction == action }
            if (fromAction != null) return fromAction
            val raw = extra?.trim().orEmpty()
            if (raw.isEmpty()) return null
            return entries.firstOrNull {
                it.name.equals(raw, ignoreCase = true) || it.intentAction == raw
            }
        }

        fun createIntent(context: Context, action: ScanlyLaunchAction): Intent =
            Intent(context, MainActivity::class.java).apply {
                this.action = action.intentAction
                putExtra(EXTRA_LAUNCH_ACTION, action.name)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK
            }
    }
}
