package `in`.c1ph3rj.scanly.data.library.manifest

import `in`.c1ph3rj.scanly.domain.model.LibraryAssetRef
import kotlinx.serialization.Serializable

@Serializable
data class LibraryMarker(
    val formatVersion: Int = CURRENT_LIBRARY_FORMAT,
    val libraryId: String,
    val createdAtMillis: Long,
    val minimumReaderVersion: Int = CURRENT_LIBRARY_FORMAT,
)

@Serializable
data class LibraryCatalog(
    val formatVersion: Int = CURRENT_LIBRARY_FORMAT,
    val libraryId: String,
    val generation: Long,
    val documents: List<CatalogRecord> = emptyList(),
    val groups: List<CatalogRecord> = emptyList(),
    val tombstones: List<TombstoneManifest> = emptyList(),
)

@Serializable
data class CatalogRecord(
    val id: String,
    val revision: Long,
    val checksum: String,
)

@Serializable
data class DocumentManifest(
    val formatVersion: Int = CURRENT_LIBRARY_FORMAT,
    val id: String,
    val revision: Long,
    val title: String,
    val preferredFilterPreset: String? = null,
    val groupId: String? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val pages: List<PageManifest> = emptyList(),
)

@Serializable
data class PageManifest(
    val id: String,
    val pageIndex: Int,
    val rawAsset: LibraryAssetRef? = null,
    val processedAsset: LibraryAssetRef? = null,
    val thumbnailAsset: LibraryAssetRef? = null,
    val rotationDegrees: Int,
    val crop: CropManifest? = null,
    val filterPreset: String,
    val processingState: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Serializable
data class CropManifest(
    val topLeftX: Float,
    val topLeftY: Float,
    val topRightX: Float,
    val topRightY: Float,
    val bottomRightX: Float,
    val bottomRightY: Float,
    val bottomLeftX: Float,
    val bottomLeftY: Float,
)

@Serializable
data class GroupManifest(
    val formatVersion: Int = CURRENT_LIBRARY_FORMAT,
    val id: String,
    val revision: Long,
    val title: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Serializable
data class TombstoneManifest(
    val type: String,
    val id: String,
    val deletedAtMillis: Long,
    val generation: Long,
)

@Serializable
data class OperationJournal(
    val id: String,
    val type: String,
    val targetId: String,
    val targetRevision: Long,
    val catalogGeneration: Long,
    val createdAtMillis: Long,
)

const val CURRENT_LIBRARY_FORMAT = 1

