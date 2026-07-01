package `in`.c1ph3rj.scanly.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import `in`.c1ph3rj.scanly.data.local.db.entity.ScanPageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanPageDao {
    @Query("SELECT * FROM scan_pages WHERE documentId = :documentId ORDER BY pageIndex ASC")
    fun observePages(documentId: String): Flow<List<ScanPageEntity>>

    @Query("SELECT * FROM scan_pages WHERE documentId = :documentId ORDER BY pageIndex ASC")
    suspend fun getPages(documentId: String): List<ScanPageEntity>

    @Query("SELECT * FROM scan_pages WHERE id = :pageId")
    fun observePage(pageId: String): Flow<ScanPageEntity?>

    @Query("SELECT * FROM scan_pages WHERE id = :pageId")
    suspend fun getPage(pageId: String): ScanPageEntity?

    @Query("SELECT COUNT(*) FROM scan_pages WHERE documentId = :documentId")
    suspend fun countPages(documentId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(page: ScanPageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(pages: List<ScanPageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(page: ScanPageEntity)

    @Update
    suspend fun update(page: ScanPageEntity)

    @Update
    suspend fun updateAll(pages: List<ScanPageEntity>)

    @Query("DELETE FROM scan_pages WHERE id = :pageId")
    suspend fun deleteById(pageId: String)

    @Query("DELETE FROM scan_pages WHERE documentId = :documentId")
    suspend fun deleteByDocumentId(documentId: String)
}
