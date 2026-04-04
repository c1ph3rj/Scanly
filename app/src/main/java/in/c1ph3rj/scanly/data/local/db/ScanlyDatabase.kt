package `in`.c1ph3rj.scanly.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import `in`.c1ph3rj.scanly.data.local.db.dao.DocumentDao
import `in`.c1ph3rj.scanly.data.local.db.dao.ScanPageDao
import `in`.c1ph3rj.scanly.data.local.db.entity.DocumentEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.ScanPageEntity

@Database(
    entities = [
        DocumentEntity::class,
        ScanPageEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class ScanlyDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao

    abstract fun scanPageDao(): ScanPageDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE documents
                    ADD COLUMN preferredFilterPreset TEXT
                    """.trimIndent(),
                )
            }
        }
    }
}
