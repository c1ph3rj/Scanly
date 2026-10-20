package `in`.c1ph3rj.scanly.feature.tools.pdf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * "Open with Scanly" for PDFs from other apps (Files, Gmail, Drive, WhatsApp…). The reader works
 * on local files, so the shared document is copied into the cache first; the original is never
 * touched.
 */
object IncomingPdf {
    fun uriFrom(intent: Intent?): Uri? {
        if (intent == null) return null
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getStreamUri()
            else -> null
        } ?: return null
        val type = intent.type ?: return uri.takeIf { it.path?.endsWith(".pdf", ignoreCase = true) == true }
        return uri.takeIf { type == PDF_MIME || type == "application/x-pdf" }
    }

    /** Copies [uri] into the cache. Returns the local path and a display name, or null. */
    fun copyToCache(context: Context, uri: Uri): Pair<String, String>? = runCatching {
        val name = displayName(context, uri)
            ?.let { if (it.endsWith(".pdf", ignoreCase = true)) it else "$it.pdf" }
            ?: "Document.pdf"
        val dir = File(context.cacheDir, "incoming_pdf").apply {
            // Keep only the latest few so the cache doesn't grow with every opened attachment.
            listFiles()?.sortedByDescending { it.lastModified() }?.drop(4)?.forEach { it.delete() }
            mkdirs()
        }
        val target = File(dir, "${System.currentTimeMillis()}_${name.replace(Regex("[^\\w.\\- ]"), "_")}")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        target.absolutePath to name.removeSuffix(".pdf").removeSuffix(".PDF")
    }.getOrNull()

    private fun displayName(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    @Suppress("DEPRECATION")
    private fun Intent.getStreamUri(): Uri? = getParcelableExtra(Intent.EXTRA_STREAM)

    private const val PDF_MIME = "application/pdf"
}
