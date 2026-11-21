package `in`.c1ph3rj.scanly.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A saved document name pattern; see `NamingTemplateRenderer`. At most one row has [isDefault]. */
@Entity(tableName = "naming_templates")
data class NamingTemplateEntity(
    @PrimaryKey val id: String,
    val pattern: String,
    val isDefault: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
