package `in`.c1ph3rj.scanly.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Text recognized on one page, plus the layout needed to draw it over the page image.
 *
 * [id] is an INTEGER PRIMARY KEY so it aliases SQLite's rowid, which [PageTextFtsEntity]
 * uses as its external-content key (a TEXT key's implicit rowid can change on VACUUM).
 * Rows are always replaced with delete + insert so Room's FTS sync triggers fire.
 */
@Entity(
    tableName = "page_texts",
    foreignKeys = [
        ForeignKey(
            entity = ScanPageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["pageId"], unique = true),
        Index(value = ["documentId"]),
    ],
)
data class PageTextEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val pageId: String,
    val documentId: String,
    /** Text exactly as the recognizer returned it. */
    val recognizedText: String,
    /** The user's corrected text, kept across re-recognition; null when never edited. */
    val userText: String?,
    /** What search indexes and what copy/share use: [userText] when present. */
    val searchText: String,
    /** Serialized [`in`.c1ph3rj.scanly.domain.model.PageTextLayout] blocks with normalized boxes. */
    val blocksJson: String,
    val imageWidth: Int,
    val imageHeight: Int,
    /** The page's `updatedAtMillis` when it was read; a mismatch marks the row stale. */
    val sourceUpdatedAtMillis: Long,
    val recognizedAtMillis: Long,
    /** Storage value of the detected [`in`.c1ph3rj.scanly.domain.model.DocumentKind], if any. */
    val kind: String?,
    /** [`in`.c1ph3rj.scanly.domain.model.OcrEngine] name that produced the text; null when nothing was recognized. */
    val engine: String? = null,
    /** [`in`.c1ph3rj.scanly.domain.model.OcrEngine] name the user had selected when the page was read. */
    val requestedEngine: String? = null,
    /** [`in`.c1ph3rj.scanly.domain.model.OcrFallbackReason] name when the requested engine was not used. */
    val fallbackReason: String? = null,
)
