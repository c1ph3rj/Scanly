package `in`.c1ph3rj.scanly.feature.tools.pdf

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.content.FileProvider
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.model.PdfToolSource
import `in`.c1ph3rj.scanly.domain.model.ShareArtifact
import java.io.File

@Composable
fun rememberSinglePdfPicker(onPicked: (Uri) -> Unit) =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onPicked(uri)
    }

@Composable
fun rememberMultiPdfPicker(onPicked: (List<Uri>) -> Unit) =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onPicked(uris)
    }

fun shareExportArtifact(context: Context, artifact: ExportArtifact, title: String = "Scanly PDF") {
    sharePreparedFiles(
        context = context,
        artifact = ShareArtifact(
            mimeType = artifact.mimeType,
            title = title,
            filePaths = listOf(artifact.filePath),
        ),
    )
}

fun sharePreparedFiles(context: Context, artifact: ShareArtifact) {
    val uris = artifact.filePaths.map { path ->
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            File(path),
        )
    }
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

fun uriDisplayName(context: Context, uri: Uri): String {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (it.moveToFirst() && nameIndex >= 0) {
            return it.getString(nameIndex) ?: "Document.pdf"
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/') ?: "Document.pdf"
}

fun PdfToolSource.label(): String = when (this) {
    is PdfToolSource.DeviceUri -> displayName
    is PdfToolSource.LibraryDocument -> title
    is PdfToolSource.AppFile -> displayName
}
