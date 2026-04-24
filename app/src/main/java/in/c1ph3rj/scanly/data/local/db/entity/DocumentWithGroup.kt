package `in`.c1ph3rj.scanly.data.local.db.entity

data class DocumentWithGroup(
    val id: String,
    val title: String,
    val pageCount: Int,
    val coverThumbnailPath: String?,
    val preferredFilterPreset: String?,
    val groupId: String?,
    val groupName: String?,
    val rootDirectoryPath: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
