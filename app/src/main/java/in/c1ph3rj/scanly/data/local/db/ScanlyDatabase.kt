package `in`.c1ph3rj.scanly.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import `in`.c1ph3rj.scanly.data.local.db.dao.DocumentDao
import `in`.c1ph3rj.scanly.data.local.db.dao.DocumentGroupDao
import `in`.c1ph3rj.scanly.data.local.db.dao.PageMarkupDao
import `in`.c1ph3rj.scanly.data.local.db.dao.PageTextDao
import `in`.c1ph3rj.scanly.data.local.db.dao.ScanPageDao
import `in`.c1ph3rj.scanly.data.local.db.dao.SignatureDao
import `in`.c1ph3rj.scanly.data.local.db.entity.DocumentEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.DocumentGroupEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.PageMarkupEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.PageTextEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.PageTextFtsEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.ScanPageEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.SignatureEntity
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset

@Database(
    entities = [
        DocumentEntity::class,
        ScanPageEntity::class,
        DocumentGroupEntity::class,
        PageTextEntity::class,
        PageTextFtsEntity::class,
        SignatureEntity::class,
        PageMarkupEntity::class,
    ],
    version = 11,
    exportSchema = false,
)
abstract class ScanlyDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao

    abstract fun scanPageDao(): ScanPageDao

    abstract fun documentGroupDao(): DocumentGroupDao

    abstract fun pageTextDao(): PageTextDao

    abstract fun signatureDao(): SignatureDao

    abstract fun pageMarkupDao(): PageMarkupDao

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

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE scan_pages ADD COLUMN filterBrightness REAL NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE scan_pages ADD COLUMN filterContrast REAL NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE scan_pages ADD COLUMN filterSaturation REAL NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE scan_pages ADD COLUMN filterSharpness REAL NOT NULL DEFAULT 0",
                )
            }
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE scan_pages ADD COLUMN filterIntensity REAL NOT NULL DEFAULT 1",
                )
                // Presets removed in the filter overhaul are rewritten to their closest replacement.
                PageFilterPreset.legacyStorageMigrations.forEach { (legacy, replacement) ->
                    db.execSQL(
                        "UPDATE scan_pages SET filterPreset = ? WHERE filterPreset = ?",
                        arrayOf<Any>(replacement, legacy),
                    )
                    db.execSQL(
                        "UPDATE documents SET preferredFilterPreset = ? WHERE preferredFilterPreset = ?",
                        arrayOf<Any>(replacement, legacy),
                    )
                }
            }
        }

        /**
         * Adds on-device page text (ML Kit) and its FTS4 external-content index. Existing pages
         * have no rows yet; the text indexer picks them up in the background after upgrade.
         * DDL mirrors Room's generated schema exactly so validation passes.
         */
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `page_texts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`pageId` TEXT NOT NULL, `documentId` TEXT NOT NULL, `recognizedText` TEXT NOT NULL, " +
                        "`userText` TEXT, `searchText` TEXT NOT NULL, `blocksJson` TEXT NOT NULL, " +
                        "`imageWidth` INTEGER NOT NULL, `imageHeight` INTEGER NOT NULL, " +
                        "`sourceUpdatedAtMillis` INTEGER NOT NULL, `recognizedAtMillis` INTEGER NOT NULL, `kind` TEXT, " +
                        "FOREIGN KEY(`pageId`) REFERENCES `scan_pages`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_page_texts_pageId` ON `page_texts` (`pageId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_page_texts_documentId` ON `page_texts` (`documentId`)",
                )
                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS `page_texts_fts` USING FTS4(`searchText` TEXT NOT NULL, " +
                        "tokenize=unicode61, content=`page_texts`)",
                )
                PAGE_TEXT_FTS_TRIGGERS.forEach(db::execSQL)
            }
        }

        /**
         * Adds saved signatures and per-page sign markups. DDL mirrors Room's generated schema
         * exactly so validation passes.
         */
        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `signatures` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                        "`source` TEXT NOT NULL, `imagePath` TEXT NOT NULL, `widthPx` INTEGER NOT NULL, " +
                        "`heightPx` INTEGER NOT NULL, `createdAtMillis` INTEGER NOT NULL, " +
                        "`updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_signatures_updatedAtMillis` ON `signatures` (`updatedAtMillis`)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `page_markups` (`pageId` TEXT NOT NULL, `marksJson` TEXT NOT NULL, " +
                        "`baseImagePath` TEXT NOT NULL, `updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`pageId`), " +
                        "FOREIGN KEY(`pageId`) REFERENCES `scan_pages`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
            }
        }

        /**
         * Adds Recently deleted: a document with a non-null `deletedAtMillis` is in the trash
         * (hidden from the library, restorable) until it is purged.
         */
        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `documents` ADD COLUMN `deletedAtMillis` INTEGER")
            }
        }

        /**
         * Recently deleted for folders and pages. A trashed folder takes its documents with it
         * (`deletedWithGroup`); a trashed page keeps its row at a negative index and remembers
         * where it was (`deletedFromIndex`).
         */
        val MIGRATION_8_9: Migration = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `document_groups` ADD COLUMN `deletedAtMillis` INTEGER")
                db.execSQL("ALTER TABLE `documents` ADD COLUMN `deletedWithGroup` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `scan_pages` ADD COLUMN `deletedAtMillis` INTEGER")
                db.execSQL("ALTER TABLE `scan_pages` ADD COLUMN `deletedFromIndex` INTEGER")
            }
        }

        /** "Delete folder only": documents remember the folder they left, so restoring it can bring them back. */
        val MIGRATION_9_10: Migration = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `documents` ADD COLUMN `releasedFromGroupId` TEXT")
            }
        }

        /**
         * Records which OCR engine read each page. Every page read before this version used ML Kit,
         * so non-empty rows are backfilled; empty rows stay null (nothing was recognized). The
         * UPDATE re-fires the FTS sync triggers, which re-index the unchanged `searchText`.
         */
        val MIGRATION_10_11: Migration = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `page_texts` ADD COLUMN `engine` TEXT")
                db.execSQL("ALTER TABLE `page_texts` ADD COLUMN `requestedEngine` TEXT")
                db.execSQL("ALTER TABLE `page_texts` ADD COLUMN `fallbackReason` TEXT")
                db.execSQL(
                    "UPDATE `page_texts` SET `engine` = 'ML_KIT', `requestedEngine` = 'ML_KIT' " +
                        "WHERE `recognizedText` != ''",
                )
            }
        }

        /**
         * Read-only view of the FTS vocabulary (word → pages containing it), used for
         * "did you mean" suggestions. Room doesn't manage fts4aux tables, so it is created
         * idempotently on every open, which covers fresh installs and upgrades alike.
         */
        const val PAGE_TEXT_TERMS_TABLE = "page_texts_terms"
        const val CREATE_PAGE_TEXT_TERMS =
            "CREATE VIRTUAL TABLE IF NOT EXISTS `$PAGE_TEXT_TERMS_TABLE` USING fts4aux(page_texts_fts)"

        private val PAGE_TEXT_FTS_TRIGGERS = listOf(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_page_texts_fts_BEFORE_UPDATE BEFORE UPDATE ON `page_texts` " +
                "BEGIN DELETE FROM `page_texts_fts` WHERE `docid`=OLD.`rowid`; END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_page_texts_fts_BEFORE_DELETE BEFORE DELETE ON `page_texts` " +
                "BEGIN DELETE FROM `page_texts_fts` WHERE `docid`=OLD.`rowid`; END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_page_texts_fts_AFTER_UPDATE AFTER UPDATE ON `page_texts` " +
                "BEGIN INSERT INTO `page_texts_fts`(`docid`, `searchText`) VALUES (NEW.`rowid`, NEW.`searchText`); END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_page_texts_fts_AFTER_INSERT AFTER INSERT ON `page_texts` " +
                "BEGIN INSERT INTO `page_texts_fts`(`docid`, `searchText`) VALUES (NEW.`rowid`, NEW.`searchText`); END",
        )
    }
}
