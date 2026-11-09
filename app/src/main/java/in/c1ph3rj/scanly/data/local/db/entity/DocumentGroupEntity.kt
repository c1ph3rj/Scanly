package `in`.c1ph3rj.scanly.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "document_groups",
    indices = [
        Index(value = ["updatedAtMillis"]),
    ],
)
data class DocumentGroupEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    /** Set when the folder (with its documents) is in Recently deleted; null for live folders. */
    val deletedAtMillis: Long? = null,
)
