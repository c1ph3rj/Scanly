package `in`.c1ph3rj.scanly.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "document_groups",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["updatedAtMillis"]),
    ],
)
data class DocumentGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
