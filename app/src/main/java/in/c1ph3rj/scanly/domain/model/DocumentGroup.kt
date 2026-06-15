package `in`.c1ph3rj.scanly.domain.model

data class DocumentGroup(
    val id: String,
    val title: String,
    val documentCount: Int,
    val totalPageCount: Int,
    val coverThumbnailPath: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val coverUpdatedAtMillis: Long = updatedAtMillis,
)
