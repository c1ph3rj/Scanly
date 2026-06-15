package `in`.c1ph3rj.scanly.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import `in`.c1ph3rj.scanly.data.local.db.dao.DocumentDao
import `in`.c1ph3rj.scanly.data.local.db.dao.DocumentGroupDao
import `in`.c1ph3rj.scanly.data.local.db.dao.ScanPageDao
import `in`.c1ph3rj.scanly.data.local.db.entity.DocumentEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.DocumentGroupEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.ScanPageEntity

@Database(
    entities = [
        DocumentEntity::class,
        ScanPageEntity::class,
        DocumentGroupEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class ScanlyDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao

    abstract fun scanPageDao(): ScanPageDao

    abstract fun documentGroupDao(): DocumentGroupDao

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

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Disable FK enforcement while we rebuild the table.
                // SQLite ALTER TABLE ADD COLUMN cannot attach FK constraints, so we
                // must create a new table with the FK, copy data, swap names, then
                // recreate indexes. This is the canonical Room table-recreation pattern.
                db.execSQL("PRAGMA foreign_keys=OFF")

                // 1. Create document_groups first (referenced by the new FK).
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS document_groups (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        createdAtMillis INTEGER NOT NULL,
                        updatedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_document_groups_updatedAtMillis ON document_groups(updatedAtMillis)",
                )

                // 2. Create the replacement documents table with the FK constraint.
                db.execSQL(
                    """
                    CREATE TABLE documents_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        pageCount INTEGER NOT NULL,
                        coverThumbnailPath TEXT,
                        preferredFilterPreset TEXT,
                        rootDirectoryPath TEXT NOT NULL,
                        createdAtMillis INTEGER NOT NULL,
                        updatedAtMillis INTEGER NOT NULL,
                        groupId TEXT,
                        FOREIGN KEY(groupId) REFERENCES document_groups(id) ON DELETE SET NULL
                    )
                    """.trimIndent(),
                )

                // 3. Copy all existing rows; groupId is NULL for every existing document.
                db.execSQL(
                    """
                    INSERT INTO documents_new
                        (id, title, pageCount, coverThumbnailPath, preferredFilterPreset,
                         rootDirectoryPath, createdAtMillis, updatedAtMillis, groupId)
                    SELECT id, title, pageCount, coverThumbnailPath, preferredFilterPreset,
                           rootDirectoryPath, createdAtMillis, updatedAtMillis, NULL
                    FROM documents
                    """.trimIndent(),
                )

                // 4. Swap tables.
                db.execSQL("DROP TABLE documents")
                db.execSQL("ALTER TABLE documents_new RENAME TO documents")

                // 5. Recreate indexes that Room expects on the new table.
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_documents_updatedAtMillis ON documents(updatedAtMillis)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_documents_groupId ON documents(groupId)",
                )

                db.execSQL("PRAGMA foreign_keys=ON")
            }
        }
    }
}
