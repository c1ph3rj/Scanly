package `in`.c1ph3rj.scanly.domain.model

data class DocumentGroup(
    val id: String,
    val title: String,
    val documentCount: Int,
    val totalPageCount: Int,
    val coverThumbnail: LibraryAssetRef?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val coverUpdatedAtMillis: Long = updatedAtMillis,
) {
    @Deprecated("Use coverThumbnail")
    val coverThumbnailPath: String? get() = coverThumbnail?.relativePath
}
