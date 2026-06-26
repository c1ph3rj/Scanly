package `in`.c1ph3rj.scanly.data.storage

data class StoredDocumentSnapshot(
    val id: String,
    val rootDirectoryPath: String,
    val coverThumbnailPath: String?,
    val pages: List<StoredPageSnapshot>,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

data class StoredPageSnapshot(
    val pageIndex: Int,
    val rawImagePath: String?,
    val processedImagePath: String?,
    val thumbnailPath: String?,
    val updatedAtMillis: Long,
)
