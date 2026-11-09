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
 * unicode61 folds case and diacritics, so "resume" matches "Résumé". [derivedText] holds
 * searchable forms of the text written differently from how it is typed ("1,250" → 1250).
 */
@Fts4(
    contentEntity = PageTextEntity::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    tokenizerArgs = [PAGE_TEXT_TOKENIZER_ARG],
)
@Entity(tableName = "page_texts_fts")
data class PageTextFtsEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    val searchText: String,
    /** Column 1: matched like the text, but never shown in snippets. */
    val derivedText: String,
)

/**
 * Older SQLite (3.32 on Android 13) splits words at combining marks, so தமிழ்நாடு would be
 * indexed as தம ழ ந ட. Declaring the vowel signs, viramas and other marks of the Indic scripts
 * (U+0900–U+0DFF) as token characters keeps each word whole, as newer SQLite already does.
 */
const val PAGE_TEXT_TOKENIZER_ARG = "tokenchars=" +
    "\u0900\u0901\u0902\u0903\u093A\u093B\u093C\u093E\u093F\u0940\u0941\u0942\u0943\u0944\u0945\u0946" +
    "\u0947\u0948\u0949\u094A\u094B\u094C\u094D\u094E\u094F\u0951\u0952\u0953\u0954\u0955\u0956\u0957" +
    "\u0962\u0963\u0981\u0982\u0983\u09BC\u09BE\u09BF\u09C0\u09C1\u09C2\u09C3\u09C4\u09C7\u09C8\u09CB" +
    "\u09CC\u09CD\u09D7\u09E2\u09E3\u09FE\u0A01\u0A02\u0A03\u0A3C\u0A3E\u0A3F\u0A40\u0A41\u0A42\u0A47" +
    "\u0A48\u0A4B\u0A4C\u0A4D\u0A51\u0A70\u0A71\u0A75\u0A81\u0A82\u0A83\u0ABC\u0ABE\u0ABF\u0AC0\u0AC1" +
    "\u0AC2\u0AC3\u0AC4\u0AC5\u0AC7\u0AC8\u0AC9\u0ACB\u0ACC\u0ACD\u0AE2\u0AE3\u0AFA\u0AFB\u0AFC\u0AFD" +
    "\u0AFE\u0AFF\u0B01\u0B02\u0B03\u0B3C\u0B3E\u0B3F\u0B40\u0B41\u0B42\u0B43\u0B44\u0B47\u0B48\u0B4B" +
    "\u0B4C\u0B4D\u0B55\u0B56\u0B57\u0B62\u0B63\u0B82\u0BBE\u0BBF\u0BC0\u0BC1\u0BC2\u0BC6\u0BC7\u0BC8" +
    "\u0BCA\u0BCB\u0BCC\u0BCD\u0BD7\u0C00\u0C01\u0C02\u0C03\u0C04\u0C3C\u0C3E\u0C3F\u0C40\u0C41\u0C42" +
    "\u0C43\u0C44\u0C46\u0C47\u0C48\u0C4A\u0C4B\u0C4C\u0C4D\u0C55\u0C56\u0C62\u0C63\u0C81\u0C82\u0C83" +
    "\u0CBC\u0CBE\u0CBF\u0CC0\u0CC1\u0CC2\u0CC3\u0CC4\u0CC6\u0CC7\u0CC8\u0CCA\u0CCB\u0CCC\u0CCD\u0CD5" +
    "\u0CD6\u0CE2\u0CE3\u0CF3\u0D00\u0D01\u0D02\u0D03\u0D3B\u0D3C\u0D3E\u0D3F\u0D40\u0D41\u0D42\u0D43" +
    "\u0D44\u0D46\u0D47\u0D48\u0D4A\u0D4B\u0D4C\u0D4D\u0D57\u0D62\u0D63\u0D81\u0D82\u0D83\u0DCA\u0DCF" +
    "\u0DD0\u0DD1\u0DD2\u0DD3\u0DD4\u0DD6\u0DD8\u0DD9\u0DDA\u0DDB\u0DDC\u0DDD\u0DDE\u0DDF\u0DF2\u0DF3"
