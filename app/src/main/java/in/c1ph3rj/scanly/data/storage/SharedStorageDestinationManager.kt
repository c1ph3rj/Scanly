package `in`.c1ph3rj.scanly.data.storage

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
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

data class DestinationCapacity(
    val destination: ExportDestination,
    val availableBytes: Long?,
    val backupDirectoryReady: Boolean,
    val errorMessage: String? = null,
)

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

    suspend fun inspectBackupCapacity(): DestinationCapacity {
        val destination = currentDestination()
        return runCatching {
            when (destination) {
                ExportDestination.DefaultDownloadsScanly -> DestinationCapacity(
                    destination = destination,
                    availableBytes = StatFs(Environment.getExternalStorageDirectory().absolutePath)
                        .availableBytes,
                    backupDirectoryReady = true,
                )

                is ExportDestination.CustomTree -> {
                    val treeUri = requireWritableTree(destination)
                    ensureChildDirectory(treeUri, BACKUP_DIRECTORY_NAME)
                    DestinationCapacity(
                        destination = destination,
                        availableBytes = queryProviderAvailableBytes(treeUri),
                        backupDirectoryReady = true,
                    )
                }
            }
        }.getOrElse { error ->
            DestinationCapacity(
                destination = destination,
                availableBytes = null,
                backupDirectoryReady = false,
                errorMessage = error.message ?: "The backup folder is unavailable.",
            )
        }
    }

    suspend fun writeBackup(
        requestedName: String,
        writer: suspend (OutputStream) -> Unit,
    ): SavedExport = when (val destination = currentDestination()) {
        ExportDestination.DefaultDownloadsScanly -> writeMediaStoreStream(
            relativePath = DEFAULT_BACKUP_RELATIVE_PATH,
            requestedName = requestedName,
            mimeType = BACKUP_MIME_TYPE,
            destinationLabel = destination.backupLabel,
            writer = writer,
        )

        is ExportDestination.CustomTree -> {
            val backupDirectory = ensureChildDirectory(
                requireWritableTree(destination),
                BACKUP_DIRECTORY_NAME,
            )
            writeTreeFile(
                parentUri = backupDirectory,
                requestedName = requestedName,
                mimeType = BACKUP_MIME_TYPE,
                destinationLabel = destination.backupLabel,
                publishAfterWrite = true,
                writer = writer,
            )
        }
    }

    private suspend fun writeMediaStoreFile(
        relativePath: String,
        requestedName: String,
        mimeType: String,
        source: File,
        destinationLabel: String,
    ): SavedExport = writeMediaStoreStream(
        relativePath = relativePath,
        requestedName = requestedName,
        mimeType = mimeType,
        destinationLabel = destinationLabel,
    ) { output -> source.inputStream().use { input -> input.copyTo(output) } }

    private suspend fun writeMediaStoreStream(
        relativePath: String,
        requestedName: String,
        mimeType: String,
        destinationLabel: String,
        writer: suspend (OutputStream) -> Unit,
    ): SavedExport {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
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
            resolver.openOutputStream(uri, "w")?.use { output -> writer(output) }
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

    private suspend fun writeTreeFile(
        parentUri: Uri,
        requestedName: String,
        mimeType: String,
        destinationLabel: String,
        publishAfterWrite: Boolean = false,
        writer: suspend (OutputStream) -> Unit,
    ): SavedExport {
        val resolver = context.contentResolver
        val fileName = uniqueTreeName(parentUri, requestedName)
        val createName = if (publishAfterWrite) uniqueTreeName(parentUri, "$fileName.partial") else fileName
        val uri = DocumentsContract.createDocument(resolver, parentUri, mimeType, createName)
            ?: error("Could not create a file in $destinationLabel.")
        var publishedUri = uri
        try {
            resolver.openOutputStream(uri, "w")?.use { output -> writer(output) }
                ?: error("Could not open $destinationLabel for writing.")
            if (publishAfterWrite) {
                publishedUri = DocumentsContract.renameDocument(resolver, uri, fileName)
                    ?: error("The selected folder cannot publish completed backups.")
            }
        } catch (error: Throwable) {
            DocumentsContract.deleteDocument(resolver, uri)
            throw error
        }
        return SavedExport(fileName, destinationLabel, publishedUri.toString())
    }

    private fun requireWritableTree(destination: ExportDestination.CustomTree): Uri {
        if (!hasPersistedAccess(destination)) {
            error("The selected save folder is no longer available. Choose it again in Settings.")
        }
        val treeUri = Uri.parse(destination.uriString)
        val documentId = DocumentsContract.getTreeDocumentId(treeUri)
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
    }

    private fun ensureChildDirectory(parentUri: Uri, name: String): Uri {
        val resolver = context.contentResolver
        findChild(parentUri, name, DocumentsContract.Document.MIME_TYPE_DIR)?.let { return it }
        return DocumentsContract.createDocument(
            resolver,
            parentUri,
            DocumentsContract.Document.MIME_TYPE_DIR,
            name,
        ) ?: error("Could not create the $name folder.")
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

    private fun findChild(parentUri: Uri, name: String, mimeType: String): Uri? {
        val resolver = context.contentResolver
        val treeUri = parentUri
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getDocumentId(parentUri),
        )
        return resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                if (cursor.getString(nameColumn) == name && cursor.getString(mimeColumn) == mimeType) {
                    return@use DocumentsContract.buildDocumentUriUsingTree(
                        treeUri,
                        cursor.getString(idColumn),
                    )
                }
            }
            null
        }
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

    private fun queryProviderAvailableBytes(treeUri: Uri): Long? {
        val authority = treeUri.authority ?: return null
        val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        return context.contentResolver.query(
            DocumentsContract.buildRootsUri(authority),
            arrayOf(
                DocumentsContract.Root.COLUMN_ROOT_ID,
                DocumentsContract.Root.COLUMN_AVAILABLE_BYTES,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            val rootColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Root.COLUMN_ROOT_ID)
            val bytesColumn = cursor.getColumnIndex(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES)
            if (bytesColumn < 0) return@use null
            while (cursor.moveToNext()) {
                val rootId = cursor.getString(rootColumn)
                if (treeDocumentId == rootId || treeDocumentId.startsWith("$rootId:")) {
                    return@use if (cursor.isNull(bytesColumn)) null else cursor.getLong(bytesColumn)
                }
            }
            null
        }
    }

    private fun String.withTrailingSlash(): String = if (endsWith('/')) this else "$this/"

    companion object {
        const val BACKUP_DIRECTORY_NAME = "backup"
        const val BACKUP_MIME_TYPE = "application/zip"
        const val DEFAULT_EXPORT_RELATIVE_PATH = "Download/Scanly"
        const val DEFAULT_BACKUP_RELATIVE_PATH = "Download/Scanly/backup"
    }
}
