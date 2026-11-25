package `in`.c1ph3rj.scanly.data.local.db

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Schema 13 → 14 adds `tags`, `document_tags` and `naming_templates`. Checks the tables and
 * indexes come out exactly as Room creates them on a fresh install, and that the document FK
 * cascades. Uses throwaway in-memory databases only.
 */
class TagsAndNamingTemplatesMigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(13) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        db = helper.writableDatabase
        // Only the referenced table matters; the rest of schema 13 is unchanged.
        db.execSQL("CREATE TABLE `documents` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("INSERT INTO documents (id, title) VALUES ('d1', 'Receipts')")
    }

    @After
    fun tearDown() = helper.close()

    @Test
    fun newTablesMatchFreshInstall() {
        ScanlyDatabase.MIGRATION_13_14.migrate(db)

        val fresh = Room.inMemoryDatabaseBuilder(context, ScanlyDatabase::class.java).build()
        try {
            val freshDb = fresh.openHelper.writableDatabase
            assertEquals(schema(freshDb), schema(db))
        } finally {
            fresh.close()
        }
    }

    @Test
    fun deletingADocumentRemovesItsTagLinks() {
        ScanlyDatabase.MIGRATION_13_14.migrate(db)
        db.execSQL("PRAGMA foreign_keys=ON")
        db.execSQL("INSERT INTO tags (id, name, nameKey, createdAtMillis) VALUES ('t1', 'Travel', 'travel', 1)")
        db.execSQL("INSERT INTO document_tags (documentId, tagId) VALUES ('d1', 't1')")

        db.execSQL("DELETE FROM documents WHERE id = 'd1'")

        assertEquals(0L, db.query("SELECT COUNT(*) FROM document_tags").use { it.moveToFirst(); it.getLong(0) })
    }

    /** CREATE statements of the new tables and their indexes, as SQLite stored them. */
    private fun schema(database: SupportSQLiteDatabase): List<String> =
        database.query(
            "SELECT type || ' ' || name || ': ' || sql FROM sqlite_master " +
                "WHERE tbl_name IN ('tags', 'document_tags', 'naming_templates') AND sql IS NOT NULL ORDER BY name",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
}
