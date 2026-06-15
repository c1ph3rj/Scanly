package `in`.c1ph3rj.scanly.data.local.db.entity

/**
 * POJO returned by the aggregate JOIN query in [DocumentGroupDao].
 * Not a Room @Entity — Room maps query column aliases to these field names.
 */
data class DocumentGroupStats(
    val id: String,
    val title: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val documentCount: Int,
    val totalPageCount: Int,
    val coverThumbnailPath: String?,
)
