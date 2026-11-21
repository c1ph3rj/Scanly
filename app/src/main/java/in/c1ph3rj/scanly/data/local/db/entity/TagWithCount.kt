package `in`.c1ph3rj.scanly.data.local.db.entity

/**
 * POJO returned by the tag list queries in `TagDao`.
 * Not a Room @Entity — Room maps query column aliases to these field names.
 *
 * @property documentCount Live (not trashed) documents carrying the tag.
 */
data class TagWithCount(
    val id: String,
    val name: String,
    val createdAtMillis: Long,
    val documentCount: Int,
)
