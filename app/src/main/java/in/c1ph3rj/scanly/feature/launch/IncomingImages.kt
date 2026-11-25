package `in`.c1ph3rj.scanly.feature.launch

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/**
 * Images shared into Scanly from Photos, Files, chat apps and so on (`ACTION_SEND` /
 * `ACTION_SEND_MULTIPLE` with an `image/` type). PDFs shared the same way are handled by
 * `IncomingPdf`.
 */
object IncomingImages {
    private const val ACTION_SEND = "android.intent.action.SEND"
    private const val ACTION_SEND_MULTIPLE = "android.intent.action.SEND_MULTIPLE"

    /**
     * Shared image URIs in [intent], in the sender's order, or an empty list when it isn't an
     * image share. [ownPackage] is this app's package, whose own providers are never read.
     */
    fun urisFrom(intent: Intent?, ownPackage: String): List<Uri> {
        if (intent == null || !isImageShare(intent.action, intent.type)) return emptyList()
        val streams = when (intent.action) {
            ACTION_SEND -> listOfNotNull(
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java),
            )
            else -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                ?.filterNotNull()
                .orEmpty()
        }
        // Some senders only fill ClipData; it carries the same read grants.
        val fromClip = intent.clipData?.let { clip ->
            (0 until clip.itemCount).mapNotNull { clip.getItemAt(it)?.uri }
        }.orEmpty()
        return streams.ifEmpty { fromClip }
            .filter { isAcceptedSource(it.scheme, it.authority, ownPackage) }
            .distinct()
    }

    internal fun isImageShare(action: String?, type: String?): Boolean =
        (action == ACTION_SEND || action == ACTION_SEND_MULTIPLE) &&
            type?.trim()?.lowercase()?.startsWith("image/") == true

    /**
     * Only `content://` URIs from other apps. `file://` and our own providers are refused so a
     * share can't make Scanly copy its private files into a document.
     */
    internal fun isAcceptedSource(scheme: String?, authority: String?, ownPackage: String): Boolean {
        if (!scheme.equals("content", ignoreCase = true)) return false
        // Cross-profile URIs prefix the authority with "userId@".
        val host = authority?.trim()?.lowercase()?.substringAfterLast('@').orEmpty()
        if (host.isEmpty()) return false
        val own = ownPackage.lowercase()
        return host != own && !host.startsWith("$own.")
    }
}
