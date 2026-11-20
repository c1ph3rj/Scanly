package `in`.c1ph3rj.scanly.data.local.db

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Schema 12 → 13 adds the scan-mode columns. Checks they come out exactly as Room creates them on
 * a fresh install (type, nullability, default — what Room validates on upgrade) and that existing
 * rows become plain documents. Uses throwaway in-memory databases only.
 */
class ScanModeMigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(12) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        db = helper.writableDatabase
        // Only the columns this migration touches matter; the rest of schema 12 is unchanged.
        db.execSQL("CREATE TABLE `documents` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL(
            "CREATE TABLE `scan_pages` (`id` TEXT NOT NULL, `documentId` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("INSERT INTO documents (id, title) VALUES ('d1', 'Receipts')")
        db.execSQL("INSERT INTO scan_pages (id, documentId) VALUES ('p1', 'd1')")
    }

    @After
    fun tearDown() = helper.close()

    @Test
    fun newColumnsMatchFreshInstallAndExistingRowsArePlainDocuments() {
        ScanlyDatabase.MIGRATION_12_13.migrate(db)

        val fresh = Room.inMemoryDatabaseBuilder(context, ScanlyDatabase::class.java).build()
        try {
            val freshDb = fresh.openHelper.writableDatabase
            assertEquals(
                columns(freshDb, "documents", setOf("preferredScanMode")),
                columns(db, "documents", setOf("preferredScanMode")),
            )
            val pageColumns = setOf("scanMode", "idCardPairId", "idCardSide")
            assertEquals(columns(freshDb, "scan_pages", pageColumns), columns(db, "scan_pages", pageColumns))
        } finally {
            fresh.close()
        }

        assertEquals("document", string("SELECT preferredScanMode FROM documents WHERE id = 'd1'"))
        assertEquals("document", string("SELECT scanMode FROM scan_pages WHERE id = 'p1'"))
        assertNull(string("SELECT idCardPairId FROM scan_pages WHERE id = 'p1'"))
        assertNull(string("SELECT idCardSide FROM scan_pages WHERE id = 'p1'"))
    }

    private fun string(sql: String): String? = db.query(sql).use { cursor ->
        cursor.moveToFirst()
        if (cursor.isNull(0)) null else cursor.getString(0)
    }

    /** name, type, notnull and default of the [names] columns of [table], as Room validates them. */
    private fun columns(database: SupportSQLiteDatabase, table: String, names: Set<String>): List<String> =
        database.query("PRAGMA table_info(`$table`)").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    if (cursor.getString(1) !in names) continue
                    add((1..4).joinToString("|") { index -> cursor.getString(index).orEmpty() })
                }
            }.sorted()
        }
}
