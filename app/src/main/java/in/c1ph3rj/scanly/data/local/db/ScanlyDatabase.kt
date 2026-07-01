package `in`.c1ph3rj.scanly.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import `in`.c1ph3rj.scanly.data.local.db.dao.DocumentDao
import `in`.c1ph3rj.scanly.data.local.db.dao.DocumentGroupDao
import `in`.c1ph3rj.scanly.data.local.db.dao.LibraryStateDao
import `in`.c1ph3rj.scanly.data.local.db.dao.ScanPageDao
import `in`.c1ph3rj.scanly.data.local.db.entity.DocumentEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.DocumentGroupEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.LibraryStateEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.ManifestFingerprintEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.ScanPageEntity

@Database(
    entities = [
        DocumentEntity::class,
        ScanPageEntity::class,
        DocumentGroupEntity::class,
        LibraryStateEntity::class,
        ManifestFingerprintEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(LibraryAssetConverters::class)
abstract class ScanlyDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun scanPageDao(): ScanPageDao
    abstract fun documentGroupDao(): DocumentGroupDao
    abstract fun libraryStateDao(): LibraryStateDao
}
