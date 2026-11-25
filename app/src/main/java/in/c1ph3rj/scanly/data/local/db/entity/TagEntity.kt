package `in`.c1ph3rj.scanly.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-made document tag. [nameKey] is `DocumentTagRules.key(name)`, unique so two tags can't
 * differ only by case or spacing.
 */
@Entity(
    tableName = "tags",
    indices = [
        Index(value = ["nameKey"], unique = true),
    ],
)
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val nameKey: String,
    val createdAtMillis: Long,
)
