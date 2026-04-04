package `in`.c1ph3rj.scanly.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import `in`.c1ph3rj.scanly.data.local.db.dao.DocumentDao
import `in`.c1ph3rj.scanly.data.local.db.dao.ScanPageDao
import `in`.c1ph3rj.scanly.data.local.db.entity.DocumentEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.ScanPageEntity

@Database(
    entities = [
        DocumentEntity::class,
        ScanPageEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class ScanlyDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao

    abstract fun scanPageDao(): ScanPageDao
}
