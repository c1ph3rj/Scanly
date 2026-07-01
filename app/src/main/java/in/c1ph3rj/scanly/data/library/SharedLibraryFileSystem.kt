package `in`.c1ph3rj.scanly.data.library

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class SharedEntry(
    val name: String,
    val uri: Uri,
    val mimeType: String,
    val size: Long,
)

@Singleton
class SharedLibraryFileSystem @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: ScanlyDispatchers,
) {
    private val resolver: ContentResolver get() = context.contentResolver

    suspend fun hasPersistedReadWriteGrant(treeUri: Uri): Boolean = withContext(dispatchers.io) {
        resolver.persistedUriPermissions.any { permission ->
            permission.uri == treeUri && permission.isReadPermission && permission.isWritePermission
        }
    }

    suspend fun displayName(treeUri: Uri): String = withContext(dispatchers.io) {
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        resolver.query(rootUri(treeUri), projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "Scanly library"
    }

    suspend fun ensureDirectory(treeUri: Uri, relativePath: String): Uri = withContext(dispatchers.io) {
        validateRelativePath(relativePath)
        relativePath.split('/').filter(String::isNotBlank).fold(rootUri(treeUri)) { parent, name ->
            findChild(treeUri, parent, name)?.also { entry ->
                require(entry.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    "$relativePath contains a non-directory entry."
                }
            }?.uri ?: DocumentsContract.createDocument(
                resolver,
                parent,
                DocumentsContract.Document.MIME_TYPE_DIR,
                name,
            ) ?: error("Could not create library directory $relativePath.")
        }
    }

    suspend fun writeBytes(
        treeUri: Uri,
        relativePath: String,
        bytes: ByteArray,
        mimeType: String,
    ): Uri = withContext(dispatchers.io) {
        validateRelativePath(relativePath)
        require(bytes.size <= MAX_SINGLE_WRITE_BYTES) { "Library entry is too large." }
        val segments = relativePath.split('/').filter(String::isNotBlank)
        require(segments.isNotEmpty()) { "A file name is required." }
        val parentPath = segments.dropLast(1).joinToString("/")
        val parent = if (parentPath.isBlank()) rootUri(treeUri) else ensureDirectory(treeUri, parentPath)
        val name = segments.last()
        val existing = findChild(treeUri, parent, name)
        require(existing == null || existing.mimeType != DocumentsContract.Document.MIME_TYPE_DIR) {
            "$relativePath is a directory."
        }
        val uri = existing?.uri ?: DocumentsContract.createDocument(resolver, parent, mimeType, name)
            ?: error("Could not create $relativePath.")
        resolver.openOutputStream(uri, "wt")?.use { output ->
            output.write(bytes)
            output.flush()
        } ?: error("Could not open $relativePath for writing.")
        uri
    }

    suspend fun copyFromFile(
        treeUri: Uri,
        relativePath: String,
        source: File,
        mimeType: String,
    ): SharedEntry = withContext(dispatchers.io) {
        require(source.isFile && source.length() > 0L) { "Working file is missing." }
        validateRelativePath(relativePath)
        val segments = relativePath.split('/').filter(String::isNotBlank)
        val parentPath = segments.dropLast(1).joinToString("/")
        val parent = if (parentPath.isBlank()) rootUri(treeUri) else ensureDirectory(treeUri, parentPath)
        val name = segments.last()
        val existing = findChild(treeUri, parent, name)
        val uri = existing?.uri ?: DocumentsContract.createDocument(resolver, parent, mimeType, name)
            ?: error("Could not create $relativePath.")
        resolver.openOutputStream(uri, "wt")?.use { output ->
            source.inputStream().use { input -> input.copyTo(output) }
            output.flush()
        } ?: error("Could not write $relativePath.")
        val entry = stat(treeUri, relativePath) ?: error("Could not verify $relativePath.")
        require(entry.size == source.length()) { "Shared asset verification failed for $relativePath." }
        entry
    }

    suspend fun readBytes(treeUri: Uri, relativePath: String, maxBytes: Int = MAX_MANIFEST_BYTES): ByteArray =
        withContext(dispatchers.io) {
            val entry = stat(treeUri, relativePath) ?: error("Missing library entry $relativePath.")
            require(entry.size <= maxBytes || entry.size < 0L) { "$relativePath is too large." }
            resolver.openInputStream(entry.uri)?.use { input ->
                val bytes = input.readBytes()
                require(bytes.size <= maxBytes) { "$relativePath is too large." }
                bytes
            } ?: error("Could not read $relativePath.")
        }

    suspend fun copyToFile(treeUri: Uri, relativePath: String, destination: File): File =
        withContext(dispatchers.io) {
            val entry = stat(treeUri, relativePath) ?: error("Missing library asset $relativePath.")
            destination.parentFile?.mkdirs()
            resolver.openInputStream(entry.uri)?.use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Could not read $relativePath.")
            destination
        }

    suspend fun stat(treeUri: Uri, relativePath: String): SharedEntry? = withContext(dispatchers.io) {
        validateRelativePath(relativePath)
        var current = rootUri(treeUri)
        var result: SharedEntry? = null
        for (segment in relativePath.split('/').filter(String::isNotBlank)) {
            result = findChild(treeUri, current, segment) ?: return@withContext null
            current = result.uri
        }
        result
    }

    suspend fun list(treeUri: Uri, relativePath: String): List<SharedEntry> = withContext(dispatchers.io) {
        val directory = if (relativePath.isBlank()) rootUri(treeUri) else stat(treeUri, relativePath)?.uri
            ?: return@withContext emptyList()
        listChildren(treeUri, directory)
    }

    suspend fun delete(treeUri: Uri, relativePath: String): Boolean = withContext(dispatchers.io) {
        val entry = stat(treeUri, relativePath) ?: return@withContext true
        DocumentsContract.deleteDocument(resolver, entry.uri)
    }

    suspend fun sha256(treeUri: Uri, relativePath: String): String = withContext(dispatchers.io) {
        val entry = stat(treeUri, relativePath) ?: error("Missing library asset $relativePath.")
        val digest = MessageDigest.getInstance("SHA-256")
        resolver.openInputStream(entry.uri)?.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        } ?: error("Could not hash $relativePath.")
        digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    suspend fun directorySize(treeUri: Uri, relativePath: String = ""): Long = withContext(dispatchers.io) {
        list(treeUri, relativePath).sumOf { entry ->
            if (entry.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                directorySize(treeUri, listOf(relativePath, entry.name).filter(String::isNotBlank).joinToString("/"))
            } else {
                entry.size.coerceAtLeast(0L)
            }
        }
    }

    fun validateRelativePath(relativePath: String) {
        LibraryPathValidator.requireValid(relativePath)
    }

    private fun rootUri(treeUri: Uri): Uri = DocumentsContract.buildDocumentUriUsingTree(
        treeUri,
        DocumentsContract.getTreeDocumentId(treeUri),
    )

    private fun findChild(treeUri: Uri, parent: Uri, name: String): SharedEntry? =
        listChildren(treeUri, parent).firstOrNull { it.name == name }

    private fun listChildren(treeUri: Uri, parent: Uri): List<SharedEntry> {
        val parentId = DocumentsContract.getDocumentId(parent)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        return buildList {
            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                while (cursor.moveToNext()) {
                    add(
                        SharedEntry(
                            name = cursor.getString(nameColumn),
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idColumn)),
                            mimeType = cursor.getString(mimeColumn),
                            size = if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) cursor.getLong(sizeColumn) else -1L,
                        ),
                    )
                }
            }
        }
    }

    companion object {
        const val MAX_MANIFEST_BYTES = 2 * 1024 * 1024
        const val MAX_SINGLE_WRITE_BYTES = 4 * 1024 * 1024
    }
}
