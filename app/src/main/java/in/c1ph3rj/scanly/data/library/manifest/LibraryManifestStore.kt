package `in`.c1ph3rj.scanly.data.library.manifest

import android.net.Uri
import `in`.c1ph3rj.scanly.data.library.SharedLibraryFileSystem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class StoredManifest<T>(
    val value: T,
    val checksum: String,
    val relativePath: String,
)

@Singleton
class LibraryManifestStore @Inject constructor(
    private val fileSystem: SharedLibraryFileSystem,
) {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
        prettyPrint = true
    }

    suspend fun createLibrary(treeUri: Uri): LibraryMarker {
        val existing = readMarkerOrNull(treeUri)
        if (existing != null) return existing
        val marker = LibraryMarker(
            libraryId = UUID.randomUUID().toString(),
            createdAtMillis = System.currentTimeMillis(),
        )
        fileSystem.writeBytes(treeUri, MARKER_PATH, encode(marker), JSON_MIME_TYPE)
        REQUIRED_DIRECTORIES.forEach { path -> fileSystem.ensureDirectory(treeUri, path) }
        runCatching { fileSystem.writeBytes(treeUri, NOMEDIA_PATH, ByteArray(0), BINARY_MIME_TYPE) }
        writeCatalog(
            treeUri,
            LibraryCatalog(libraryId = marker.libraryId, generation = 0L),
        )
        return marker
    }

    suspend fun readMarker(treeUri: Uri): LibraryMarker = readMarkerOrNull(treeUri)
        ?: error("This folder is not a Scanly library.")

    suspend fun readMarkerOrNull(treeUri: Uri): LibraryMarker? {
        if (fileSystem.stat(treeUri, MARKER_PATH) == null) return null
        return decode<LibraryMarker>(fileSystem.readBytes(treeUri, MARKER_PATH)).also(::validateMarker)
    }

    suspend fun readLatestCatalog(treeUri: Uri): StoredManifest<LibraryCatalog> {
        val candidates = fileSystem.list(treeUri, CATALOG_DIRECTORY)
            .mapNotNull { entry -> CATALOG_PATTERN.matchEntire(entry.name)?.groupValues?.get(1)?.toLongOrNull()?.let { it to entry } }
            .sortedByDescending { it.first }
        candidates.forEach { (_, entry) ->
            val path = "$CATALOG_DIRECTORY/${entry.name}"
            runCatching {
                val bytes = fileSystem.readBytes(treeUri, path)
                val catalog = decode<LibraryCatalog>(bytes)
                validateCatalog(catalog)
                return StoredManifest(catalog, sha256(bytes), path)
            }
        }
        error("No valid Scanly catalog was found.")
    }

    suspend fun writeCatalog(treeUri: Uri, catalog: LibraryCatalog): StoredManifest<LibraryCatalog> {
        validateCatalog(catalog)
        val path = "$CATALOG_DIRECTORY/catalog-r${catalog.generation.toString().padStart(12, '0')}.json"
        val bytes = encode(catalog)
        fileSystem.writeBytes(treeUri, path, bytes, JSON_MIME_TYPE)
        val persisted = fileSystem.readBytes(treeUri, path)
        require(persisted.contentEquals(bytes)) { "Catalog verification failed." }
        pruneOldRevisions(treeUri, CATALOG_DIRECTORY, CATALOG_PATTERN)
        return StoredManifest(catalog, sha256(bytes), path)
    }

    suspend fun readLatestDocument(treeUri: Uri, documentId: String): StoredManifest<DocumentManifest>? {
        requireUuid(documentId)
        return readLatestVersioned(
            treeUri = treeUri,
            directory = "documents/$documentId/manifests",
            pattern = DOCUMENT_PATTERN,
            decode = { bytes -> decode<DocumentManifest>(bytes).also(::validateDocument) },
        )
    }

    suspend fun writeDocument(treeUri: Uri, manifest: DocumentManifest): StoredManifest<DocumentManifest> {
        validateDocument(manifest)
        val directory = "documents/${manifest.id}/manifests"
        val path = "$directory/document-r${manifest.revision.toString().padStart(12, '0')}.json"
        val stored = writeVerified(treeUri, path, manifest, ::validateDocument)
        pruneOldRevisions(treeUri, directory, DOCUMENT_PATTERN)
        return stored
    }

    suspend fun readLatestGroup(treeUri: Uri, groupId: String): StoredManifest<GroupManifest>? {
        requireUuid(groupId)
        return readLatestVersioned(
            treeUri = treeUri,
            directory = "groups/$groupId",
            pattern = GROUP_PATTERN,
            decode = { bytes -> decode<GroupManifest>(bytes).also(::validateGroup) },
        )
    }

    suspend fun writeGroup(treeUri: Uri, manifest: GroupManifest): StoredManifest<GroupManifest> {
        validateGroup(manifest)
        val directory = "groups/${manifest.id}"
        val path = "$directory/group-r${manifest.revision.toString().padStart(12, '0')}.json"
        val stored = writeVerified(treeUri, path, manifest, ::validateGroup)
        pruneOldRevisions(treeUri, directory, GROUP_PATTERN)
        return stored
    }

    suspend fun writeOperation(treeUri: Uri, journal: OperationJournal): String {
        requireUuid(journal.id)
        requireUuid(journal.targetId)
        val path = "$OPERATIONS_DIRECTORY/${journal.id}.json"
        fileSystem.writeBytes(treeUri, path, encode(journal), JSON_MIME_TYPE)
        return path
    }

    suspend fun listOperations(treeUri: Uri): List<OperationJournal> =
        fileSystem.list(treeUri, OPERATIONS_DIRECTORY)
            .filter { entry -> entry.name.endsWith(".json") }
            .mapNotNull { entry ->
                runCatching {
                    decode<OperationJournal>(
                        fileSystem.readBytes(treeUri, "$OPERATIONS_DIRECTORY/${entry.name}"),
                    )
                }.getOrNull()
            }

    suspend fun removeOperation(treeUri: Uri, operationId: String) {
        requireUuid(operationId)
        fileSystem.delete(treeUri, "$OPERATIONS_DIRECTORY/$operationId.json")
    }

    suspend fun writeTombstone(treeUri: Uri, tombstone: TombstoneManifest) {
        requireUuid(tombstone.id)
        require(tombstone.type == "document" || tombstone.type == "group")
        fileSystem.writeBytes(
            treeUri,
            "$TOMBSTONES_DIRECTORY/${tombstone.type}-${tombstone.id}.json",
            encode(tombstone),
            JSON_MIME_TYPE,
        )
    }

    suspend fun scanDocuments(treeUri: Uri): List<StoredManifest<DocumentManifest>> =
        fileSystem.list(treeUri, DOCUMENTS_DIRECTORY)
            .filter { it.mimeType == android.provider.DocumentsContract.Document.MIME_TYPE_DIR }
            .filter { entry -> runCatching { UUID.fromString(entry.name) }.isSuccess }
            .map { entry -> readLatestDocument(treeUri, entry.name) ?: error("Document ${entry.name} has no valid manifest.") }

    suspend fun scanGroups(treeUri: Uri): List<StoredManifest<GroupManifest>> =
        fileSystem.list(treeUri, GROUPS_DIRECTORY)
            .filter { it.mimeType == android.provider.DocumentsContract.Document.MIME_TYPE_DIR }
            .filter { entry -> runCatching { UUID.fromString(entry.name) }.isSuccess }
            .map { entry -> readLatestGroup(treeUri, entry.name) ?: error("Group ${entry.name} has no valid manifest.") }

    suspend fun scanTombstones(treeUri: Uri): List<TombstoneManifest> =
        fileSystem.list(treeUri, TOMBSTONES_DIRECTORY)
            .filter { entry -> entry.name.endsWith(".json") }
            .map { entry ->
                decode<TombstoneManifest>(fileSystem.readBytes(treeUri, "$TOMBSTONES_DIRECTORY/${entry.name}"))
                    .also { tombstone ->
                        require(tombstone.type == "document" || tombstone.type == "group")
                        requireUuid(tombstone.id)
                    }
            }

    private suspend inline fun <reified T> writeVerified(
        treeUri: Uri,
        path: String,
        value: T,
        crossinline validate: (T) -> Unit,
    ): StoredManifest<T> {
        validate(value)
        val bytes = encode(value)
        fileSystem.writeBytes(treeUri, path, bytes, JSON_MIME_TYPE)
        val persisted = fileSystem.readBytes(treeUri, path)
        require(persisted.contentEquals(bytes)) { "Manifest verification failed for $path." }
        return StoredManifest(value, sha256(bytes), path)
    }

    private suspend fun <T> readLatestVersioned(
        treeUri: Uri,
        directory: String,
        pattern: Regex,
        decode: (ByteArray) -> T,
    ): StoredManifest<T>? {
        val candidates = fileSystem.list(treeUri, directory)
            .mapNotNull { entry -> pattern.matchEntire(entry.name)?.groupValues?.get(1)?.toLongOrNull()?.let { it to entry } }
            .sortedByDescending { it.first }
        candidates.forEach { (_, entry) ->
            val path = "$directory/${entry.name}"
            runCatching {
                val bytes = fileSystem.readBytes(treeUri, path)
                return StoredManifest(decode(bytes), sha256(bytes), path)
            }
        }
        return null
    }

    private suspend fun pruneOldRevisions(treeUri: Uri, directory: String, pattern: Regex) {
        fileSystem.list(treeUri, directory)
            .mapNotNull { entry -> pattern.matchEntire(entry.name)?.groupValues?.get(1)?.toLongOrNull()?.let { it to entry.name } }
            .sortedByDescending { it.first }
            .drop(2)
            .forEach { (_, name) -> runCatching { fileSystem.delete(treeUri, "$directory/$name") } }
    }

    private fun validateMarker(marker: LibraryMarker) {
        require(marker.formatVersion in 1..CURRENT_LIBRARY_FORMAT) { "Unsupported Scanly library version." }
        require(marker.minimumReaderVersion <= CURRENT_LIBRARY_FORMAT) { "This Scanly library requires a newer app." }
        requireUuid(marker.libraryId)
    }

    private fun validateCatalog(catalog: LibraryCatalog) {
        require(catalog.formatVersion in 1..CURRENT_LIBRARY_FORMAT)
        requireUuid(catalog.libraryId)
        require(catalog.generation >= 0L)
        require(catalog.documents.size <= MAX_RECORDS && catalog.groups.size <= MAX_RECORDS)
        (catalog.documents + catalog.groups).forEach { record ->
            requireUuid(record.id)
            require(record.revision >= 1L)
            require(SHA256_PATTERN.matches(record.checksum))
        }
    }

    private fun validateDocument(manifest: DocumentManifest) {
        require(manifest.formatVersion in 1..CURRENT_LIBRARY_FORMAT)
        requireUuid(manifest.id)
        manifest.groupId?.let(::requireUuid)
        require(manifest.revision >= 1L)
        require(manifest.title.isNotBlank() && manifest.title.length <= 200)
        require(manifest.pages.size <= MAX_PAGES_PER_DOCUMENT)
        require(manifest.pages.map { it.id }.distinct().size == manifest.pages.size)
        manifest.pages.sortedBy { it.pageIndex }.forEachIndexed { index, page ->
            requireUuid(page.id)
            require(page.pageIndex == index) { "Page indexes must be contiguous." }
            require(page.rotationDegrees in 0..359)
            listOfNotNull(page.rawAsset, page.processedAsset, page.thumbnailAsset).forEach { asset ->
                fileSystem.validateRelativePath(asset.relativePath)
                require(asset.byteCount > 0L)
                require(SHA256_PATTERN.matches(asset.sha256))
                require(asset.mimeType == "image/jpeg")
            }
            page.crop?.let { crop ->
                listOf(
                    crop.topLeftX, crop.topLeftY, crop.topRightX, crop.topRightY,
                    crop.bottomRightX, crop.bottomRightY, crop.bottomLeftX, crop.bottomLeftY,
                ).forEach { value -> require(value in 0f..1f) }
            }
        }
    }

    private fun validateGroup(manifest: GroupManifest) {
        require(manifest.formatVersion in 1..CURRENT_LIBRARY_FORMAT)
        requireUuid(manifest.id)
        require(manifest.revision >= 1L)
        require(manifest.title.isNotBlank() && manifest.title.length <= 200)
    }

    private fun requireUuid(value: String) {
        require(runCatching { UUID.fromString(value) }.isSuccess) { "Invalid library identifier." }
    }

    private inline fun <reified T> decode(bytes: ByteArray): T =
        json.decodeFromString(bytes.toString(Charsets.UTF_8))

    private inline fun <reified T> encode(value: T): ByteArray =
        json.encodeToString(value).toByteArray(Charsets.UTF_8)

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }

    companion object {
        const val MARKER_PATH = "library.json"
        const val CATALOG_DIRECTORY = "catalog"
        const val DOCUMENTS_DIRECTORY = "documents"
        const val GROUPS_DIRECTORY = "groups"
        const val OPERATIONS_DIRECTORY = "operations"
        const val TOMBSTONES_DIRECTORY = "tombstones"
        const val NOMEDIA_PATH = ".nomedia"
        const val JSON_MIME_TYPE = "application/json"
        const val BINARY_MIME_TYPE = "application/octet-stream"
        val REQUIRED_DIRECTORIES = listOf(
            CATALOG_DIRECTORY,
            DOCUMENTS_DIRECTORY,
            GROUPS_DIRECTORY,
            OPERATIONS_DIRECTORY,
            TOMBSTONES_DIRECTORY,
        )
        val CATALOG_PATTERN = Regex("catalog-r(\\d+)\\.json")
        val DOCUMENT_PATTERN = Regex("document-r(\\d+)\\.json")
        val GROUP_PATTERN = Regex("group-r(\\d+)\\.json")
        val SHA256_PATTERN = Regex("[0-9a-f]{64}")
        const val MAX_RECORDS = 100_000
        const val MAX_PAGES_PER_DOCUMENT = 10_000
    }
}
