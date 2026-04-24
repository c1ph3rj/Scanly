package `in`.c1ph3rj.scanly.domain.model

data class DocumentGroup(
    val id: String,
    val name: String,
    val documentCount: Int,
    val pageCount: Int,
    val coverThumbnailPaths: List<String>,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
