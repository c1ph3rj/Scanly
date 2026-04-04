package `in`.c1ph3rj.scanly.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "documents",
    indices = [
        Index(value = ["updatedAtMillis"]),
    ],
)
data class DocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val pageCount: Int,
    val coverThumbnailPath: String?,
    val rootDirectoryPath: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
