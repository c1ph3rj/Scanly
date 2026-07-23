package `in`.c1ph3rj.scanly.data.local.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScanlyDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "scanly-migration-4-5-test.db"

    @After
    fun cleanUp() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migration4To5AddsModeColumnsWithBackwardCompatibleDefaults() {
        openHelper(version = 4) { database ->
            database.execSQL("CREATE TABLE documents (id TEXT NOT NULL PRIMARY KEY)")
            database.execSQL("CREATE TABLE scan_pages (id TEXT NOT NULL PRIMARY KEY)")
            database.execSQL("INSERT INTO documents (id) VALUES ('document-1')")
            database.execSQL("INSERT INTO scan_pages (id) VALUES ('page-1')")
        }.use { it.writableDatabase }

        val migrated = openHelper(version = 5) { error("Version 5 database should be migrated.") }
        migrated.writableDatabase.use { database ->
            database.query(
                """
                SELECT preferredScanMode, preferredIdFilterPreset, preferredBookFilterPreset
                FROM documents WHERE id = 'document-1'
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("document", cursor.getString(0))
                assertTrue(cursor.isNull(1))
                assertTrue(cursor.isNull(2))
            }
            database.query(
                "SELECT scanMode, idCardPairId, idCardSide FROM scan_pages WHERE id = 'page-1'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("document", cursor.getString(0))
                assertTrue(cursor.isNull(1))
                assertTrue(cursor.isNull(2))
            }
        }
        migrated.close()
    }

    @Test
    fun migration5To6AddsExtendedAdjustmentsWithZeroDefaults() {
        openHelper(version = 5) { database ->
            database.execSQL("CREATE TABLE scan_pages (id TEXT NOT NULL PRIMARY KEY)")
            database.execSQL("INSERT INTO scan_pages (id) VALUES ('page-1')")
        }.use { it.writableDatabase }

        val migrated = openHelper(
            version = 6,
            expectedOldVersion = 5,
            migration = ScanlyDatabase.MIGRATION_5_6,
        ) { error("Version 6 database should be migrated.") }
        migrated.writableDatabase.use { database ->
            database.query(
                """
                SELECT filterHighlights, filterShadows, filterWarmth, filterVignette
                FROM scan_pages WHERE id = 'page-1'
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0f, cursor.getFloat(0))
                assertEquals(0f, cursor.getFloat(1))
                assertEquals(0f, cursor.getFloat(2))
                assertEquals(0f, cursor.getFloat(3))
            }
        }
        migrated.close()
    }

    private fun openHelper(
        version: Int,
        expectedOldVersion: Int = 4,
        migration: androidx.room.migration.Migration = ScanlyDatabase.MIGRATION_4_5,
        onCreate: (SupportSQLiteDatabase) -> Unit,
    ): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) = onCreate(db)

            override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int,
            ) {
                assertEquals(expectedOldVersion, oldVersion)
                assertEquals(version, newVersion)
                migration.migrate(db)
            }
        }
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(callback)
                .build(),
        )
    }
}
