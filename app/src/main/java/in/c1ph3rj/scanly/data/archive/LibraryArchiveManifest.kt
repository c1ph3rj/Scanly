package `in`.c1ph3rj.scanly.data.archive

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

internal object LibraryArchiveConstants {
    const val SIGNATURE = "SCANLY_LIBRARY_BACKUP"
    const val ARCHIVE_VERSION = 1
    const val MANIFEST_ENTRY = "manifest.json"
    const val ARCHIVE_WORK_DIRECTORY = "library-archive"
    const val ARCHIVE_JOURNAL_DIRECTORY = "library-archive-journal"
    const val SHA_256 = "SHA-256"
    const val BUFFER_SIZE = 64 * 1024
    const val MAX_MANIFEST_BYTES = 10 * 1024 * 1024
    const val MAX_ASSET_COUNT = 100_000
}

internal data class ArchiveManifest(
        val createdAtMillis: Long,
        val sourceAppVersion: String,
        val groups: List<ArchiveGroup>,
        val documents: List<ArchiveDocument>,
        val pages: List<ArchivePage>,
        val assets: List<ArchiveAssetRecord>,
    ) {
        val sourceBytes: Long = assets.sumOf { it.size }

        fun validate() {
            check(assets.size <= LibraryArchiveConstants.MAX_ASSET_COUNT) { "Backup contains too many files." }
            check(groups.map { it.id }.toSet().size == groups.size) { "Duplicate group IDs in backup." }
            check(documents.map { it.id }.toSet().size == documents.size) { "Duplicate document IDs in backup." }
            check(pages.map { it.id }.toSet().size == pages.size) { "Duplicate page IDs in backup." }
            (groups.map { it.id } + documents.map { it.id } + pages.map { it.id }).forEach { id ->
                runCatching { UUID.fromString(id) }.getOrElse { error("Backup contains an invalid ID.") }
            }
            val groupIds = groups.mapTo(hashSetOf()) { it.id }
            val documentIds = documents.mapTo(hashSetOf()) { it.id }
            documents.forEach { document ->
                check(document.groupId == null || document.groupId in groupIds) {
                    "Document references an unknown group."
                }
                check(pages.count { it.documentId == document.id } == document.pageCount) {
                    "Document page count does not match backup metadata."
                }
                val indexes = pages.filter { it.documentId == document.id }.map { it.pageIndex }.sorted()
                check(indexes == (0 until document.pageCount).toList()) {
                    "Document page order is invalid."
                }
            }
            pages.forEach { check(it.documentId in documentIds) { "Page references an unknown document." } }
            val paths = assets.map { it.archivePath }
            check(paths.toSet().size == paths.size) { "Duplicate asset paths in backup." }
            assets.forEach { asset ->
                check(asset.size >= 0L) { "Invalid backup file size." }
                check(asset.sha256.matches(Regex("[0-9a-f]{64}"))) { "Invalid backup checksum." }
                check(asset.archivePath.startsWith("documents/")) { "Invalid backup asset path." }
                check(LibraryArchivePolicy.isSafeArchivePath(asset.archivePath)) {
                    "Unsafe backup asset path."
                }
            }
            val assetPaths = paths.toSet()
            val referencedPaths = mutableSetOf<String>()
            documents.forEach { document ->
                document.coverPath?.let {
                    check(it in assetPaths) { "Missing document cover." }
                    referencedPaths += it
                }
            }
            pages.forEach { page ->
                listOfNotNull(page.rawPath, page.processedPath, page.thumbnailPath).forEach {
                    check(it in assetPaths) { "Missing page asset." }
                    referencedPaths += it
                }
            }
            check(assetPaths == referencedPaths) { "Backup contains unreferenced files." }
        }

        fun toJson(): JSONObject = JSONObject().apply {
            put("signature", LibraryArchiveConstants.SIGNATURE)
            put("archiveVersion", LibraryArchiveConstants.ARCHIVE_VERSION)
            put("createdAtMillis", createdAtMillis)
            put("sourceAppVersion", sourceAppVersion)
            put("sourceBytes", sourceBytes)
            put("groups", JSONArray().apply { groups.forEach { put(it.toJson()) } })
            put("documents", JSONArray().apply { documents.forEach { put(it.toJson()) } })
            put("pages", JSONArray().apply { pages.forEach { put(it.toJson()) } })
            put("assets", JSONArray().apply { assets.forEach { put(it.toJson()) } })
        }

        companion object {
            fun fromJson(json: JSONObject): ArchiveManifest {
                check(json.optString("signature") == LibraryArchiveConstants.SIGNATURE) { "This is not a Scanly backup." }
                check(json.optInt("archiveVersion", -1) == LibraryArchiveConstants.ARCHIVE_VERSION) {
                    "This backup version is not supported by this Scanly release."
                }
                return ArchiveManifest(
                    createdAtMillis = json.getLong("createdAtMillis"),
                    sourceAppVersion = json.getString("sourceAppVersion"),
                    groups = json.getJSONArray("groups").mapObjects(ArchiveGroup::fromJson),
                    documents = json.getJSONArray("documents").mapObjects(ArchiveDocument::fromJson),
                    pages = json.getJSONArray("pages").mapObjects(ArchivePage::fromJson),
                    assets = json.getJSONArray("assets").mapObjects(ArchiveAssetRecord::fromJson),
                ).also { manifest ->
                    check(json.getLong("sourceBytes") == manifest.sourceBytes) {
                        "Backup size metadata is invalid."
                    }
                }
            }
        }
    }

    internal data class ArchiveGroup(
        val id: String,
        val title: String,
        val createdAtMillis: Long,
        val updatedAtMillis: Long,
    ) {
        fun toJson() = JSONObject().apply {
            put("id", id); put("title", title); put("createdAtMillis", createdAtMillis)
            put("updatedAtMillis", updatedAtMillis)
        }

        companion object {
            fun fromJson(json: JSONObject) = ArchiveGroup(
                json.getString("id"), json.getString("title"),
                json.getLong("createdAtMillis"), json.getLong("updatedAtMillis"),
            )
        }
    }

    internal data class ArchiveDocument(
        val id: String,
        val title: String,
        val pageCount: Int,
        val coverPath: String?,
        val preferredFilterPreset: String?,
        val createdAtMillis: Long,
        val updatedAtMillis: Long,
        val groupId: String?,
    ) {
        fun toJson() = JSONObject().apply {
            put("id", id); put("title", title); put("pageCount", pageCount)
            putNullable("coverPath", coverPath); putNullable("preferredFilterPreset", preferredFilterPreset)
            put("createdAtMillis", createdAtMillis); put("updatedAtMillis", updatedAtMillis)
            putNullable("groupId", groupId)
        }

        companion object {
            fun fromJson(json: JSONObject) = ArchiveDocument(
                id = json.getString("id"), title = json.getString("title"),
                pageCount = json.getInt("pageCount"), coverPath = json.nullableString("coverPath"),
                preferredFilterPreset = json.nullableString("preferredFilterPreset"),
                createdAtMillis = json.getLong("createdAtMillis"),
                updatedAtMillis = json.getLong("updatedAtMillis"),
                groupId = json.nullableString("groupId"),
            )
        }
    }

    internal data class ArchivePage(
        val id: String,
        val documentId: String,
        val pageIndex: Int,
        val rawPath: String?,
        val processedPath: String?,
        val thumbnailPath: String?,
        val rotationDegrees: Int,
        val cropTopLeftX: Float?, val cropTopLeftY: Float?,
        val cropTopRightX: Float?, val cropTopRightY: Float?,
        val cropBottomRightX: Float?, val cropBottomRightY: Float?,
        val cropBottomLeftX: Float?, val cropBottomLeftY: Float?,
        val filterPreset: String,
        val filterBrightness: Float = 0f,
        val filterContrast: Float = 0f,
        val filterSaturation: Float = 0f,
        val filterSharpness: Float = 0f,
        val processingState: String,
        val createdAtMillis: Long,
        val updatedAtMillis: Long,
    ) {
        fun toJson() = JSONObject().apply {
            put("id", id); put("documentId", documentId); put("pageIndex", pageIndex)
            putNullable("rawPath", rawPath); putNullable("processedPath", processedPath)
            putNullable("thumbnailPath", thumbnailPath); put("rotationDegrees", rotationDegrees)
            putNullable("cropTopLeftX", cropTopLeftX); putNullable("cropTopLeftY", cropTopLeftY)
            putNullable("cropTopRightX", cropTopRightX); putNullable("cropTopRightY", cropTopRightY)
            putNullable("cropBottomRightX", cropBottomRightX); putNullable("cropBottomRightY", cropBottomRightY)
            putNullable("cropBottomLeftX", cropBottomLeftX); putNullable("cropBottomLeftY", cropBottomLeftY)
            put("filterPreset", filterPreset); put("processingState", processingState)
            put("filterBrightness", filterBrightness.toDouble())
            put("filterContrast", filterContrast.toDouble())
            put("filterSaturation", filterSaturation.toDouble())
            put("filterSharpness", filterSharpness.toDouble())
            put("createdAtMillis", createdAtMillis); put("updatedAtMillis", updatedAtMillis)
        }

        companion object {
            fun fromJson(json: JSONObject) = ArchivePage(
                id = json.getString("id"), documentId = json.getString("documentId"),
                pageIndex = json.getInt("pageIndex"), rawPath = json.nullableString("rawPath"),
                processedPath = json.nullableString("processedPath"),
                thumbnailPath = json.nullableString("thumbnailPath"),
                rotationDegrees = json.getInt("rotationDegrees"),
                cropTopLeftX = json.nullableFloat("cropTopLeftX"),
                cropTopLeftY = json.nullableFloat("cropTopLeftY"),
                cropTopRightX = json.nullableFloat("cropTopRightX"),
                cropTopRightY = json.nullableFloat("cropTopRightY"),
                cropBottomRightX = json.nullableFloat("cropBottomRightX"),
                cropBottomRightY = json.nullableFloat("cropBottomRightY"),
                cropBottomLeftX = json.nullableFloat("cropBottomLeftX"),
                cropBottomLeftY = json.nullableFloat("cropBottomLeftY"),
                filterPreset = json.getString("filterPreset"),
                filterBrightness = json.optDouble("filterBrightness", 0.0).toFloat(),
                filterContrast = json.optDouble("filterContrast", 0.0).toFloat(),
                filterSaturation = json.optDouble("filterSaturation", 0.0).toFloat(),
                filterSharpness = json.optDouble("filterSharpness", 0.0).toFloat(),
                processingState = json.getString("processingState"),
                createdAtMillis = json.getLong("createdAtMillis"),
                updatedAtMillis = json.getLong("updatedAtMillis"),
            )
        }
    }

    internal data class ArchiveAssetRecord(
        val archivePath: String,
        val size: Long,
        val sha256: String,
    ) {
        fun toJson() = JSONObject().apply {
            put("path", archivePath); put("size", size); put("sha256", sha256)
        }

        companion object {
            fun fromJson(json: JSONObject) = ArchiveAssetRecord(
                json.getString("path"), json.getLong("size"), json.getString("sha256"),
            )
        }
    }

    internal class RestoreJournal private constructor(
        private val file: File,
        val oldRoots: List<File>,
        val newRoots: List<File>,
        val newDocumentIds: List<String>,
        val newGroupIds: List<String>,
        committed: Boolean,
    ) {
        var committed: Boolean = committed
            private set

        fun markCommitted() {
            committed = true
            write()
        }

        fun delete() = file.delete()

        internal fun write() {
            file.parentFile?.mkdirs()
            file.writeText(JSONObject().apply {
                put("committed", committed)
                put("oldRoots", JSONArray(oldRoots.map { it.absolutePath }))
                put("newRoots", JSONArray(newRoots.map { it.absolutePath }))
                put("newDocumentIds", JSONArray(newDocumentIds))
                put("newGroupIds", JSONArray(newGroupIds))
            }.toString())
        }

        companion object {
            fun create(
                context: Context,
                workId: String,
                oldRoots: List<File>,
                newRoots: List<File>,
                newDocumentIds: Collection<String>,
                newGroupIds: Collection<String>,
            ) = RestoreJournal(
                file = File(
                    context.filesDir,
                    "${LibraryArchiveConstants.ARCHIVE_JOURNAL_DIRECTORY}/restore-journal-$workId.json",
                ),
                oldRoots = oldRoots,
                newRoots = newRoots,
                newDocumentIds = newDocumentIds.toList(),
                newGroupIds = newGroupIds.toList(),
                committed = false,
            ).also { it.write() }

            fun read(file: File): RestoreJournal {
                val json = JSONObject(file.readText())
                return RestoreJournal(
                    file = file,
                    oldRoots = json.getJSONArray("oldRoots").mapStrings(::File),
                    newRoots = json.getJSONArray("newRoots").mapStrings(::File),
                    newDocumentIds = json.getJSONArray("newDocumentIds").mapStrings { it },
                    newGroupIds = json.optJSONArray("newGroupIds")?.mapStrings { it }.orEmpty(),
                    committed = json.optBoolean("committed", false),
                )
            }
        }
    }

internal fun JSONObject.putNullable(key: String, value: Any?) {
    put(key, value ?: JSONObject.NULL)
}

internal fun JSONObject.nullableString(key: String): String? =
    if (isNull(key)) null else getString(key)

internal fun JSONObject.nullableFloat(key: String): Float? =
    if (isNull(key)) null else getDouble(key).toFloat()

internal fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
    List(length()) { index -> transform(getJSONObject(index)) }

internal fun <T> JSONArray.mapStrings(transform: (String) -> T): List<T> =
    List(length()) { index -> transform(getString(index)) }
