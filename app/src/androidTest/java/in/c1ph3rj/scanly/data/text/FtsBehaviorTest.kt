package `in`.c1ph3rj.scanly.data.text

import android.database.sqlite.SQLiteDatabase
import `in`.c1ph3rj.scanly.data.local.db.entity.PAGE_TEXT_TOKENIZER_ARG
import `in`.c1ph3rj.scanly.domain.search.SearchQuery
import `in`.c1ph3rj.scanly.domain.search.TextFolding
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pins the behavior of the phone's own SQLite that search relies on: how `unicode61` splits
 * words, the OR-group sub-select used for close matches, `matchinfo` layout, the fts4aux
 * vocabulary's column names, and column filters. Uses a throwaway in-memory database with the
 * same FTS4 options as `page_texts_fts` (including its Indic token characters); no app data is
 * read or written.
 */
class FtsBehaviorTest {
    private lateinit var db: SQLiteDatabase

    @Before
    fun setUp() {
        db = SQLiteDatabase.create(null)
        db.execSQL("CREATE TABLE content (id INTEGER PRIMARY KEY, a TEXT NOT NULL, b TEXT NOT NULL)")
        db.execSQL("CREATE VIRTUAL TABLE fts USING FTS4(a, b, tokenize=unicode61 `$PAGE_TEXT_TOKENIZER_ARG`, content=content)")
        db.execSQL("CREATE VIRTUAL TABLE terms USING fts4aux(fts)")
        listOf(
            "தமிழ்நாடு அரசு உணவுப்பொருள்" to "",
            "प्रधानमंत्री ने कहा" to "",
            "ಕನ್ನಡ ಭಾಷೆ" to "",
            "హైదరాబాద్ నగరం" to "",
            "monthly rent receipt" to "",
            "the recept and rent" to "",
            "Résumé of work" to "d20261102",
        ).forEachIndexed { index, (a, b) -> insert(index + 1L, a, b) }
    }

    @After
    fun tearDown() = db.close()

    private fun insert(id: Long, a: String, b: String) {
        db.execSQL("INSERT INTO content (id, a, b) VALUES (?, ?, ?)", arrayOf<Any>(id, a, b))
        db.execSQL("INSERT INTO fts (docid, a, b) VALUES (?, ?, ?)", arrayOf<Any>(id, a, b))
    }

    private fun matches(expression: String): List<Long> =
        db.rawQuery("SELECT docid FROM fts WHERE fts MATCH ? ORDER BY docid", arrayOf(expression)).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) }
        }

    @Test
    fun typedIndicQueriesReachTheirPages() {
        mapOf(
            "தமிழ்நாடு" to 1L,
            "தமிழ்" to 1L,
            "प्रधानमंत्री" to 2L,
            "ಕನ್ನಡ" to 3L,
            "హైదరాబాద్" to 4L,
            "Résumé" to 7L,
            "resume" to 7L,
        ).forEach { (typed, page) ->
            val match = SearchQuery.parse(typed).toFtsMatch()!!
            assertEquals("$typed → $match", listOf(page), matches(match))
        }
    }

    @Test
    fun indexedWordsEqualFoldedWords() {
        val indexed = db.rawQuery("SELECT term FROM terms WHERE col = '*'", null).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        val folded = listOf("தமிழ்நாடு அரசு உணவுப்பொருள்", "प्रधानमंत्री ने कहा", "ಕನ್ನಡ ಭಾಷೆ", "హైదరాబాద్ నగరం")
            .flatMap { line -> TextFolding.words(TextFolding.fold(line)).map { it.value }.toList() }
        assertTrue("missing from index: ${folded - indexed}", indexed.containsAll(folded))
    }

    @Test
    fun orGroupsIntersectThroughSubSelects() {
        // Same shape as closeMatchQuery: outer MATCH for snippets, one sub-select per group.
        val rows = db.rawQuery(
            "SELECT docid, snippet(fts, '[', ']', '…', 0, 14) FROM fts WHERE fts MATCH ? " +
                "AND docid IN (SELECT docid FROM fts WHERE fts MATCH ?) " +
                "AND docid IN (SELECT docid FROM fts WHERE fts MATCH ?) ORDER BY docid",
            arrayOf("rent* OR receipt OR recept", "rent*", "receipt OR recept"),
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getLong(0) to cursor.getString(1)) } }
        assertEquals(listOf(5L, 6L), rows.map { it.first })
        assertTrue(rows.first().second, rows.first().second.contains("[receipt]"))
    }

    @Test
    fun matchinfoHasThePcnalxLayoutInNativeByteOrder() {
        val blob = db.rawQuery(
            "SELECT matchinfo(fts, 'pcnalx') FROM fts WHERE fts MATCH ?",
            arrayOf("rent"),
        ).use { cursor -> cursor.moveToFirst(); cursor.getBlob(0) }
        val ints = ByteBuffer.wrap(blob).order(ByteOrder.nativeOrder()).asIntBuffer()
        val values = IntArray(ints.remaining()).also(ints::get)
        val phrases = values[0]
        val columns = values[1]
        assertEquals(1, phrases)
        assertEquals(2, columns)
        assertEquals(7, values[2]) // n: rows in the table
        // p + c + n + a(columns) + l(columns) + x(3 * phrases * columns)
        assertEquals(3 + 2 * columns + 3 * phrases * columns, values.size)
    }

    @Test
    fun fts4auxReportsPerColumnRowsAndColumnFiltersWork() {
        // col is '*' for the whole table, else the 0-based column index.
        val columns = db.rawQuery("SELECT DISTINCT col FROM terms", null).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        assertEquals(setOf("*", "0", "1"), columns)
        val derivedColumns = db.rawQuery("SELECT col FROM terms WHERE term = 'd20261102'", null).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        assertEquals(setOf("*", "1"), derivedColumns)
        assertEquals(listOf(7L), matches("b:d2026*"))
        assertEquals(emptyList<Long>(), matches("a:d2026*"))
        assertEquals(listOf(5L, 6L, 7L), matches("rent* OR b:d2026*"))
    }
}
