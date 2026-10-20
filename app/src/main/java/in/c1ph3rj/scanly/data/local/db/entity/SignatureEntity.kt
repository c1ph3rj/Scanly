package `in`.c1ph3rj.scanly.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A saved signature or initials; [imagePath] is an ink-mask PNG under `files/signatures/`. */
@Entity(
    tableName = "signatures",
    indices = [Index(value = ["updatedAtMillis"])],
)
data class SignatureEntity(
    @PrimaryKey val id: String,
    /** [`in`.c1ph3rj.scanly.domain.model.SignatureKind] storage value. */
    val kind: String,
    /** [`in`.c1ph3rj.scanly.domain.model.SignatureSource] storage value. */
    val source: String,
    val imagePath: String,
    val widthPx: Int,
    val heightPx: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
