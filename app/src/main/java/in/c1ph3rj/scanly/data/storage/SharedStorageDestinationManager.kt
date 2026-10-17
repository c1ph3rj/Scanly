package `in`.c1ph3rj.scanly.data.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.domain.model.ExportDestination
import `in`.c1ph3rj.scanly.domain.model.SavedExport
import `in`.c1ph3rj.scanly.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedStorageDestinationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun currentDestination(): ExportDestination =
        settingsRepository.observeExportDestination().first()

    fun hasPersistedAccess(destination: ExportDestination.CustomTree): Boolean {
        val target = Uri.parse(destination.uriString)
        return context.contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == target && permission.isWritePermission
        }
    }

    suspend fun saveExport(source: File, fileName: String, mimeType: String): SavedExport {
        require(source.isFile && source.length() > 0L) { "The exported file is unavailable." }
        return when (val destination = currentDestination()) {
            ExportDestination.DefaultDownloadsScanly -> writeMediaStoreFile(
                collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                relativePath = DEFAULT_EXPORT_RELATIVE_PATH,
                requestedName = fileName,
                mimeType = mimeType,
                source = source,
                destinationLabel = destination.exportLabel,
            )

            is ExportDestination.CustomTree -> writeTreeFile(
                parentUri = requireWritableTree(destination),
                requestedName = fileName,
                mimeType = mimeType,
                destinationLabel = destination.exportLabel,
            ) { output -> source.inputStream().use { input -> input.copyTo(output) } }
        }
    }

    /**
     * Saves an image into the shared gallery under Pictures/Scanly. Images always go here, not
     * to the configured export folder, so they show up in Photos/Gallery straight away.
     */
    fun saveImageToGallery(source: File, fileName: String, mimeType: String): SavedExport {
        require(source.isFile && source.length() > 0L) { "The exported image is unavailable." }
        return writeMediaStoreFile(
            collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            relativePath = GALLERY_RELATIVE_PATH,
            requestedName = fileName,
            mimeType = mimeType,
            source = source,
            destinationLabel = GALLERY_LABEL,
        )
    }

    private fun writeMediaStoreFile(
        collection: Uri,
        relativePath: String,
        requestedName: String,
        mimeType: String,
        source: File,
        destinationLabel: String,
    ): SavedExport {
        val resolver = context.contentResolver
        val fileName = uniqueMediaStoreName(collection, relativePath, requestedName)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: error("Could not create a file in $destinationLabel.")
        try {
            resolver.openOutputStream(uri, "w")
                ?.use { output -> source.inputStream().use { input -> input.copyTo(output) } }
                ?: error("Could not open $destinationLabel for writing.")
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
        return SavedExport(fileName, destinationLabel, uri.toString())
    }

    private fun writeTreeFile(
        parentUri: Uri,
        requestedName: String,
        mimeType: String,
        destinationLabel: String,
        writer: (OutputStream) -> Unit,
    ): SavedExport {
        val resolver = context.contentResolver
        val fileName = uniqueTreeName(parentUri, requestedName)
        val uri = DocumentsContract.createDocument(resolver, parentUri, mimeType, fileName)
            ?: error("Could not create a file in $destinationLabel.")
        try {
            resolver.openOutputStream(uri, "w")?.use { output -> writer(output) }
                ?: error("Could not open $destinationLabel for writing.")
        } catch (error: Throwable) {
            DocumentsContract.deleteDocument(resolver, uri)
            throw error
        }
        return SavedExport(fileName, destinationLabel, uri.toString())
    }

    private fun requireWritableTree(destination: ExportDestination.CustomTree): Uri {
        if (!hasPersistedAccess(destination)) {
            error("The selected save folder is no longer available. Choose it again in Settings.")
        }
        val treeUri = Uri.parse(destination.uriString)
        val documentId = DocumentsContract.getTreeDocumentId(treeUri)
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
    }

    private fun uniqueTreeName(parentUri: Uri, requestedName: String): String {
        val names = childNames(parentUri)
        return uniqueName(requestedName) { candidate -> candidate in names }
    }

    private fun uniqueMediaStoreName(collection: Uri, relativePath: String, requestedName: String): String =
        uniqueName(requestedName) { candidate ->
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                arrayOf(relativePath.withTrailingSlash(), candidate),
                null,
            )?.use { it.moveToFirst() } == true
        }

    private fun uniqueName(requestedName: String, exists: (String) -> Boolean): String {
        if (!exists(requestedName)) return requestedName
        val extensionIndex = requestedName.lastIndexOf('.').takeIf { it > 0 } ?: requestedName.length
        val stem = requestedName.substring(0, extensionIndex)
        val extension = requestedName.substring(extensionIndex)
        var index = 1
        while (exists("$stem ($index)$extension")) index++
        return "$stem ($index)$extension"
    }

    private fun childNames(parentUri: Uri): Set<String> {
        val resolver = context.contentResolver
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            parentUri,
            DocumentsContract.getDocumentId(parentUri),
        )
        return resolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val column = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(column))
            }
        }.orEmpty()
    }

    private fun String.withTrailingSlash(): String = if (endsWith('/')) this else "$this/"

    companion object {
        const val DEFAULT_EXPORT_RELATIVE_PATH = "Download/Scanly"
        const val GALLERY_RELATIVE_PATH = "Pictures/Scanly"
        const val GALLERY_LABEL = "Pictures/Scanly"
    }
}
