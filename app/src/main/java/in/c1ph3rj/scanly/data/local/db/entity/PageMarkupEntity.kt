package `in`.c1ph3rj.scanly.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Signature marks stamped onto a Scanly page. The page's processed image holds the composite;
 * [baseImagePath] keeps the clean image so the marks can be edited, moved or removed later.
 */
@Entity(
    tableName = "page_markups",
    foreignKeys = [
        ForeignKey(
            entity = ScanPageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PageMarkupEntity(
    @PrimaryKey val pageId: String,
    /** Serialized marks, see `SignMarkCodec`. */
    val marksJson: String,
    val baseImagePath: String,
    val updatedAtMillis: Long,
)
