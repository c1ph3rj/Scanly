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
 * Schema 11 → 12 rebuilds the page-text index with a second column. Checks the migrated tables
 * match what Room creates on a fresh install (so Room's validation passes on upgrade), and
 * that existing text stays searchable. Uses throwaway in-memory databases only.
 */
class PageTextMigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(11) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        db = helper.writableDatabase
        // page_texts as of schema 11: MIGRATION_5_6 plus MIGRATION_10_11.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `page_texts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`pageId` TEXT NOT NULL, `documentId` TEXT NOT NULL, `recognizedText` TEXT NOT NULL, " +
                "`userText` TEXT, `searchText` TEXT NOT NULL, `blocksJson` TEXT NOT NULL, " +
                "`imageWidth` INTEGER NOT NULL, `imageHeight` INTEGER NOT NULL, " +
                "`sourceUpdatedAtMillis` INTEGER NOT NULL, `recognizedAtMillis` INTEGER NOT NULL, `kind` TEXT, " +
                "FOREIGN KEY(`pageId`) REFERENCES `scan_pages`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("ALTER TABLE `page_texts` ADD COLUMN `engine` TEXT")
        db.execSQL("ALTER TABLE `page_texts` ADD COLUMN `requestedEngine` TEXT")
        db.execSQL("ALTER TABLE `page_texts` ADD COLUMN `fallbackReason` TEXT")
        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `page_texts_fts` USING FTS4(`searchText` TEXT NOT NULL, " +
                "tokenize=unicode61, content=`page_texts`)",
        )
        listOf("BEFORE_UPDATE", "BEFORE_DELETE").forEach { timing ->
            db.execSQL(
                "CREATE TRIGGER room_fts_content_sync_page_texts_fts_$timing ${timing.replace('_', ' ')} ON `page_texts` " +
                    "BEGIN DELETE FROM `page_texts_fts` WHERE `docid`=OLD.`rowid`; END",
            )
        }
        listOf("AFTER_UPDATE", "AFTER_INSERT").forEach { timing ->
            db.execSQL(
                "CREATE TRIGGER room_fts_content_sync_page_texts_fts_$timing ${timing.replace('_', ' ')} ON `page_texts` " +
                    "BEGIN INSERT INTO `page_texts_fts`(`docid`, `searchText`) VALUES (NEW.`rowid`, NEW.`searchText`); END",
            )
        }
        db.execSQL(ScanlyDatabase.CREATE_PAGE_TEXT_TERMS)
        db.execSQL(
            "INSERT INTO page_texts (pageId, documentId, recognizedText, searchText, blocksJson, imageWidth, " +
                "imageHeight, sourceUpdatedAtMillis, recognizedAtMillis) " +
                "VALUES ('p1', 'd1', 'Rent 1,250 due', 'Rent 1,250 due', '[]', 1, 1, 1, 1)",
        )
    }

    @After
    fun tearDown() = helper.close()

    @Test
    fun migratedSchemaMatchesFreshInstallAndKeepsText() {
        ScanlyDatabase.MIGRATION_11_12.migrate(db)
        db.execSQL(ScanlyDatabase.CREATE_PAGE_TEXT_TERMS)

        val fresh = Room.inMemoryDatabaseBuilder(context, ScanlyDatabase::class.java).build()
        try {
            val freshDb = fresh.openHelper.writableDatabase
            assertEquals(ftsSql(freshDb), ftsSql(db))
            assertEquals(columns(freshDb), columns(db))
        } finally {
            fresh.close()
        }

        assertEquals(1, count("SELECT COUNT(*) FROM page_texts_fts WHERE page_texts_fts MATCH 'rent'"))
        assertEquals(0, count("SELECT derivedVersion FROM page_texts WHERE pageId = 'p1'"))
        db.execSQL("UPDATE page_texts SET derivedText = '1250', derivedVersion = 1 WHERE pageId = 'p1'")
        assertEquals(1, count("SELECT COUNT(*) FROM page_texts_fts WHERE page_texts_fts MATCH 'derivedText:1250'"))
        assertEquals(1, count("SELECT COUNT(*) FROM page_texts_fts WHERE page_texts_fts MATCH 'rent'"))
    }

    private fun count(sql: String): Int = db.query(sql).use { it.moveToFirst(); it.getInt(0) }

    private fun ftsSql(database: SupportSQLiteDatabase): String =
        database.query("SELECT sql FROM sqlite_master WHERE name = 'page_texts_fts'").use { it.moveToFirst(); it.getString(0) }

    /** name, type, notnull, default and primary-key flag of every column, as Room validates them. */
    private fun columns(database: SupportSQLiteDatabase): List<String> =
        database.query("PRAGMA table_info(`page_texts`)").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add((1..5).joinToString("|") { index -> cursor.getString(index).orEmpty() })
                }
            }.sorted()
        }
}
