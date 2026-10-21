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
    @Query("SELECT * FROM scan_pages WHERE documentId = :documentId AND deletedAtMillis IS NULL ORDER BY pageIndex ASC")
    fun observePages(documentId: String): Flow<List<ScanPageEntity>>

    @Query("SELECT * FROM scan_pages WHERE documentId = :documentId AND deletedAtMillis IS NULL ORDER BY pageIndex ASC")
    suspend fun getPages(documentId: String): List<ScanPageEntity>

    @Query("SELECT * FROM scan_pages WHERE id = :pageId")
    fun observePage(pageId: String): Flow<ScanPageEntity?>

    @Query("SELECT * FROM scan_pages WHERE id = :pageId")
    suspend fun getPage(pageId: String): ScanPageEntity?

    @Query("SELECT COUNT(*) FROM scan_pages WHERE documentId = :documentId AND deletedAtMillis IS NULL")
    suspend fun countPages(documentId: String): Int

    @Query("SELECT * FROM scan_pages ORDER BY documentId, pageIndex")
    suspend fun getAll(): List<ScanPageEntity>

    /** Lowest index in the document, trashed pages included; trashed pages park below it. */
    @Query("SELECT MIN(pageIndex) FROM scan_pages WHERE documentId = :documentId")
    suspend fun minPageIndex(documentId: String): Int?

    /**
     * Trashed pages of live documents, most recently deleted first. Pages of a trashed document
     * go and come back with it, so they are not listed on their own.
     */
    @Query(
        """
        SELECT p.id AS id, p.documentId AS documentId, d.title AS documentTitle,
               COALESCE(p.deletedFromIndex, 0) AS deletedFromIndex,
               p.thumbnailPath AS thumbnailPath, p.deletedAtMillis AS deletedAtMillis
        FROM scan_pages p
        INNER JOIN documents d ON d.id = p.documentId AND d.deletedAtMillis IS NULL
        WHERE p.deletedAtMillis IS NOT NULL
        ORDER BY p.deletedAtMillis DESC
        """,
    )
    fun observeTrashedPages(): Flow<List<TrashedPageRow>>

    @Query("SELECT * FROM scan_pages WHERE deletedAtMillis IS NOT NULL")
    suspend fun getTrashedPages(): List<ScanPageEntity>

    @Query("SELECT * FROM scan_pages WHERE deletedAtMillis IS NOT NULL AND deletedAtMillis <= :cutoffMillis")
    suspend fun getTrashedPagesBefore(cutoffMillis: Long): List<ScanPageEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(page: ScanPageEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(pages: List<ScanPageEntity>)

    @Update
    suspend fun update(page: ScanPageEntity)

    @Update
    suspend fun updateAll(pages: List<ScanPageEntity>)

    @Query("DELETE FROM scan_pages WHERE id = :pageId")
    suspend fun deleteById(pageId: String)
}

data class TrashedPageRow(
    val id: String,
    val documentId: String,
    val documentTitle: String,
    val deletedFromIndex: Int,
    val thumbnailPath: String?,
    val deletedAtMillis: Long,
)
