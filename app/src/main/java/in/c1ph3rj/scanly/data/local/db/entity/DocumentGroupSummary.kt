package `in`.c1ph3rj.scanly.data.local.db.entity

data class DocumentGroupSummary(
    val id: String,
    val name: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val documentCount: Int,
    val pageCount: Int,
    val coverThumbnailPaths: String?,
)
