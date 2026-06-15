package `in`.c1ph3rj.scanly.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "documents",
    foreignKeys = [
        ForeignKey(
            entity = DocumentGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["updatedAtMillis"]),
        Index(value = ["groupId"]),
    ],
)
data class DocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val pageCount: Int,
    val coverThumbnailPath: String?,
    val preferredFilterPreset: String?,
    val rootDirectoryPath: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val groupId: String? = null,
)
