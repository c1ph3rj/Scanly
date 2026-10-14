package `in`.c1ph3rj.scanly.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.PrimaryKey

/**
 * Full-text index over [PageTextEntity.searchText]. External content: the text lives once in
 * `page_texts` and Room-generated triggers keep this index in sync on insert/update/delete,
 * including cascade deletes when a page or document is removed.
 *
 * unicode61 folds case and diacritics, so "resume" matches "Résumé".
 */
@Fts4(
    contentEntity = PageTextEntity::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
)
@Entity(tableName = "page_texts_fts")
data class PageTextFtsEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    val searchText: String,
)
