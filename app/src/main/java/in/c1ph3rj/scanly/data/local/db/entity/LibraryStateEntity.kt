package `in`.c1ph3rj.scanly.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "library_state")
data class LibraryStateEntity(
    @PrimaryKey val singletonId: Int = 1,
    val libraryId: String,
    val appliedGeneration: Long,
    val lastSynchronizedAtMillis: Long,
    val healthState: String,
)

