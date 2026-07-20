package `in`.c1ph3rj.scanly.feature.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.model.ShareArtifact
import java.io.File

/**
 * Canonical share helpers for [ShareArtifact] / [ExportArtifact].
 * Prefer this over local FileProvider/Intent copies in feature screens.
 */
fun sharePreparedFiles(
    context: Context,
    artifact: ShareArtifact,
) {
    val uris = artifact.filePaths.map { path -> context.exportUriFor(path) }
    val shareIntent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = artifact.mimeType
            putExtra(Intent.EXTRA_STREAM, uris.first())
            putExtra(Intent.EXTRA_TITLE, artifact.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = artifact.mimeType
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            putExtra(Intent.EXTRA_TITLE, artifact.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share ${artifact.title}"))
}

fun shareExportArtifact(
    context: Context,
    artifact: ExportArtifact,
    title: String = "Scanly PDF",
) {
    sharePreparedFiles(
        context = context,
        artifact = ShareArtifact(
            mimeType = artifact.mimeType,
            title = title,
            filePaths = listOf(artifact.filePath),
        ),
    )
}

fun Context.exportUriFor(path: String): Uri = FileProvider.getUriForFile(
    this,
    "$packageName.fileprovider",
    File(path),
)
