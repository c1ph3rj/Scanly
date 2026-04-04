package `in`.c1ph3rj.scanly.domain.model

data class ScanDocument(
    val id: String,
    val title: String,
    val pageCount: Int,
    val coverThumbnailPath: String?,
    val rootDirectoryPath: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
