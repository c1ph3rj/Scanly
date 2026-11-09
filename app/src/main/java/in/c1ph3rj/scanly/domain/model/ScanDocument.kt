package `in`.c1ph3rj.scanly.domain.model

data class ScanDocument(
    val id: String,
    val title: String,
    val pageCount: Int,
    val coverThumbnailPath: String?,
    val rootDirectoryPath: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val groupId: String? = null,
    /** When the document was moved to Recently deleted; null for live documents. */
    val deletedAtMillis: Long? = null,
)
