package `in`.c1ph3rj.scanly.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scan_pages",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["documentId", "pageIndex"], unique = true),
        Index(value = ["documentId"]),
    ],
)
data class ScanPageEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val pageIndex: Int,
    val rawImagePath: String?,
    val processedImagePath: String?,
    val thumbnailPath: String?,
    val rotationDegrees: Int,
    val cropTopLeftX: Float?,
    val cropTopLeftY: Float?,
    val cropTopRightX: Float?,
    val cropTopRightY: Float?,
    val cropBottomRightX: Float?,
    val cropBottomRightY: Float?,
    val cropBottomLeftX: Float?,
    val cropBottomLeftY: Float?,
    val filterPreset: String,
    val processingState: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
