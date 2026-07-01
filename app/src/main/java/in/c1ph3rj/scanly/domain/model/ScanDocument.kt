package `in`.c1ph3rj.scanly.domain.model

data class ScanDocument(
    val id: String,
    val title: String,
    val pageCount: Int,
    val coverThumbnail: LibraryAssetRef?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val groupId: String? = null,
) {
    @Deprecated("Use coverThumbnail")
    val coverThumbnailPath: String?
        get() = coverThumbnail?.relativePath
}
