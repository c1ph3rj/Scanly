package `in`.c1ph3rj.scanly.data.library

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.core.common.LibraryPathFormatter
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class SharedEntry(
    val name: String,
    val uri: Uri,
    val mimeType: String,
    val size: Long,
)

/** Verified size and content hash of a freshly written shared asset. */
data class StoredAsset(
    val size: Long,
    val sha256: String,
)

@Singleton
class SharedLibraryFileSystem @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: ScanlyDispatchers,
) {
    private val resolver: ContentResolver get() = context.contentResolver

    /**
     * Caches resolved document URIs for directory paths so repeated capture/edit/browse
     * operations skip walking (and listing) every path segment through SAF. Directory URIs and
     * immutable/versioned file entries are cached in memory and invalidated when their owning
     * paths are deleted. Caches are namespaced by tree URI so switching libraries never collides.
     */
    private val directoryUriCache = ConcurrentHashMap<String, Uri>()
    private val entryCache = ConcurrentHashMap<String, SharedEntry>()

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

    suspend fun displayPath(treeUri: Uri): String = withContext(dispatchers.io) {
        LibraryPathFormatter.formatTreeDocumentId(DocumentsContract.getTreeDocumentId(treeUri))
    }

    suspend fun ensureDirectory(treeUri: Uri, relativePath: String): Uri = withContext(dispatchers.io) {
        validateRelativePath(relativePath)
        resolveDirectory(treeUri, relativePath, create = true)
            ?: error("Could not create library directory $relativePath.")
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
        entryCache[entryCacheKey(treeUri, relativePath)] = SharedEntry(
            name = name,
            uri = uri,
            mimeType = mimeType,
            size = bytes.size.toLong(),
        )
        uri
    }

    suspend fun copyFromFile(
        treeUri: Uri,
        relativePath: String,
        source: File,
        mimeType: String,
    ): StoredAsset = withContext(dispatchers.io) {
        require(source.isFile && source.length() > 0L) { "Working file is missing." }
        validateRelativePath(relativePath)
        val segments = relativePath.split('/').filter(String::isNotBlank)
        val parentPath = segments.dropLast(1).joinToString("/")
        val parent = if (parentPath.isBlank()) rootUri(treeUri) else ensureDirectory(treeUri, parentPath)
        val name = segments.last()
        val existing = findChild(treeUri, parent, name)
        val uri = existing?.uri ?: DocumentsContract.createDocument(resolver, parent, mimeType, name)
            ?: error("Could not create $relativePath.")
        val digest = MessageDigest.getInstance("SHA-256")
        resolver.openOutputStream(uri, "wt")?.use { output ->
            source.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
            }
            output.flush()
        } ?: error("Could not write $relativePath.")
        val persistedSize = documentSize(uri)
        require(persistedSize == source.length()) { "Shared asset verification failed for $relativePath." }
        val sourceChecksum = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        val persistedChecksum = sha256(uri)
        require(persistedChecksum == sourceChecksum) { "Shared asset checksum verification failed for $relativePath." }
        entryCache[entryCacheKey(treeUri, relativePath)] = SharedEntry(
            name = name,
            uri = uri,
            mimeType = mimeType,
            size = persistedSize,
        )
        StoredAsset(persistedSize, persistedChecksum)
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
        entryCache[entryCacheKey(treeUri, relativePath)]?.let { return@withContext it }
        val segments = relativePath.split('/').filter(String::isNotBlank)
        if (segments.isEmpty()) return@withContext null
        val parentPath = segments.dropLast(1).joinToString("/")
        val parent = resolveDirectory(treeUri, parentPath, create = false) ?: return@withContext null
        findChild(treeUri, parent, segments.last())?.also { entry ->
            entryCache[entryCacheKey(treeUri, relativePath)] = entry
        }
    }

    suspend fun list(treeUri: Uri, relativePath: String): List<SharedEntry> = withContext(dispatchers.io) {
        val directory = if (relativePath.isBlank()) {
            rootUri(treeUri)
        } else {
            resolveDirectory(treeUri, relativePath, create = false) ?: return@withContext emptyList()
        }
        listChildren(treeUri, directory).also { entries ->
            val prefix = relativePath.split('/').filter(String::isNotBlank).joinToString("/")
            entries.forEach { entry ->
                val childPath = listOf(prefix, entry.name).filter(String::isNotBlank).joinToString("/")
                entryCache[entryCacheKey(treeUri, childPath)] = entry
            }
        }
    }

    suspend fun delete(treeUri: Uri, relativePath: String): Boolean = withContext(dispatchers.io) {
        val entry = stat(treeUri, relativePath) ?: return@withContext true
        val deleted = DocumentsContract.deleteDocument(resolver, entry.uri)
        invalidateEntryCache(treeUri, relativePath, includeChildren = entry.mimeType == DocumentsContract.Document.MIME_TYPE_DIR)
        if (entry.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
            invalidateDirectoryCache(treeUri, relativePath)
        }
        deleted
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

    /**
     * Resolves the document URI for a directory path, consulting (and populating) the directory
     * cache segment by segment. When [create] is false a missing segment yields null instead of
     * creating it. Must be called on an IO context (it performs blocking SAF queries).
     */
    private fun resolveDirectory(treeUri: Uri, relativePath: String, create: Boolean): Uri? {
        val segments = relativePath.split('/').filter(String::isNotBlank)
        if (segments.isEmpty()) return rootUri(treeUri)
        directoryUriCache[directoryCacheKey(treeUri, segments.joinToString("/"))]?.let { return it }

        var current = rootUri(treeUri)
        val builder = StringBuilder()
        for (name in segments) {
            if (builder.isNotEmpty()) builder.append('/')
            builder.append(name)
            val key = directoryCacheKey(treeUri, builder.toString())
            val cached = directoryUriCache[key]
            if (cached != null) {
                current = cached
                continue
            }
            val child = findChild(treeUri, current, name)
            val childUri = when {
                child != null -> {
                    require(child.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        "$relativePath contains a non-directory entry."
                    }
                    child.uri
                }
                create -> DocumentsContract.createDocument(
                    resolver,
                    current,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    name,
                ) ?: error("Could not create library directory $relativePath.")
                else -> return null
            }
            directoryUriCache[key] = childUri
            current = childUri
        }
        return current
    }

    private fun directoryCacheKey(treeUri: Uri, normalizedPath: String): String =
        "$treeUri\u0000$normalizedPath"

    private fun entryCacheKey(treeUri: Uri, relativePath: String): String =
        "$treeUri\u0000${relativePath.split('/').filter(String::isNotBlank).joinToString("/")}"

    private fun invalidateDirectoryCache(treeUri: Uri, relativePath: String) {
        val normalized = relativePath.split('/').filter(String::isNotBlank).joinToString("/")
        if (normalized.isEmpty()) {
            val prefix = "$treeUri\u0000"
            directoryUriCache.keys.removeIf { it.startsWith(prefix) }
            entryCache.keys.removeIf { it.startsWith(prefix) }
            return
        }
        val exact = directoryCacheKey(treeUri, normalized)
        directoryUriCache.keys.removeIf { it == exact || it.startsWith("$exact/") }
        invalidateEntryCache(treeUri, normalized, includeChildren = true)
    }

    private fun invalidateEntryCache(treeUri: Uri, relativePath: String, includeChildren: Boolean) {
        val exact = entryCacheKey(treeUri, relativePath)
        entryCache.keys.removeIf { key -> key == exact || (includeChildren && key.startsWith("$exact/")) }
    }

    private fun documentSize(uri: Uri): Long {
        val projection = arrayOf(DocumentsContract.Document.COLUMN_SIZE)
        val queried = resolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else -1L
        } ?: -1L
        if (queried >= 0L) return queried
        return resolver.openFileDescriptor(uri, "r")?.use { descriptor -> descriptor.statSize }
            ?.takeIf { it >= 0L }
            ?: error("Could not verify shared file size.")
    }

    private fun sha256(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        } ?: error("Could not verify shared file checksum.")
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

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
