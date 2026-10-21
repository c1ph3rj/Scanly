package `in`.c1ph3rj.scanly.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import `in`.c1ph3rj.scanly.data.local.db.entity.DocumentGroupEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.DocumentGroupStats
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentGroupDao {

    @Query(
        """
        SELECT dg.id, dg.title, dg.createdAtMillis, dg.updatedAtMillis,
               COUNT(d.id)                    AS documentCount,
               COALESCE(SUM(d.pageCount), 0)  AS totalPageCount,
               (SELECT d2.coverThumbnailPath
                FROM documents d2
                WHERE d2.groupId = dg.id AND d2.deletedAtMillis IS NULL
                ORDER BY d2.updatedAtMillis DESC
                LIMIT 1)                      AS coverThumbnailPath,
               (SELECT d2.updatedAtMillis
                FROM documents d2
                WHERE d2.groupId = dg.id AND d2.deletedAtMillis IS NULL
                ORDER BY d2.updatedAtMillis DESC
                LIMIT 1)                      AS coverUpdatedAtMillis
        FROM document_groups dg
        LEFT JOIN documents d ON d.groupId = dg.id AND d.deletedAtMillis IS NULL
        WHERE dg.deletedAtMillis IS NULL
        GROUP BY dg.id
        ORDER BY dg.updatedAtMillis DESC
        """,
    )
    fun observeGroupsWithStats(): Flow<List<DocumentGroupStats>>

    @Query(
        """
        SELECT dg.id, dg.title, dg.createdAtMillis, dg.updatedAtMillis,
               COUNT(d.id)                    AS documentCount,
               COALESCE(SUM(d.pageCount), 0)  AS totalPageCount,
               (SELECT d2.coverThumbnailPath
                FROM documents d2
                WHERE d2.groupId = dg.id AND d2.deletedAtMillis IS NULL
                ORDER BY d2.updatedAtMillis DESC
                LIMIT 1)                      AS coverThumbnailPath,
               (SELECT d2.updatedAtMillis
                FROM documents d2
                WHERE d2.groupId = dg.id AND d2.deletedAtMillis IS NULL
                ORDER BY d2.updatedAtMillis DESC
                LIMIT 1)                      AS coverUpdatedAtMillis
        FROM document_groups dg
        LEFT JOIN documents d ON d.groupId = dg.id AND d.deletedAtMillis IS NULL
        WHERE dg.deletedAtMillis IS NULL
        GROUP BY dg.id
        ORDER BY dg.updatedAtMillis DESC
        LIMIT :limit
        """,
    )
    fun observeRecentGroupsWithStats(limit: Int): Flow<List<DocumentGroupStats>>

    @Query(
        """
        SELECT dg.id, dg.title, dg.createdAtMillis, dg.updatedAtMillis,
               COUNT(d.id)                    AS documentCount,
               COALESCE(SUM(d.pageCount), 0)  AS totalPageCount,
               (SELECT d2.coverThumbnailPath
                FROM documents d2
                WHERE d2.groupId = dg.id AND d2.deletedAtMillis IS NULL
                ORDER BY d2.updatedAtMillis DESC
                LIMIT 1)                      AS coverThumbnailPath,
               (SELECT d2.updatedAtMillis
                FROM documents d2
                WHERE d2.groupId = dg.id AND d2.deletedAtMillis IS NULL
                ORDER BY d2.updatedAtMillis DESC
                LIMIT 1)                      AS coverUpdatedAtMillis
        FROM document_groups dg
        LEFT JOIN documents d ON d.groupId = dg.id AND d.deletedAtMillis IS NULL
        WHERE dg.id = :groupId
        GROUP BY dg.id
        """,
    )
    fun observeGroupWithStats(groupId: String): Flow<DocumentGroupStats?>

    @Query("SELECT * FROM document_groups WHERE id = :groupId")
    suspend fun getGroup(groupId: String): DocumentGroupEntity?

    @Query("SELECT title FROM document_groups")
    suspend fun getAllTitles(): List<String>

    @Query("SELECT * FROM document_groups ORDER BY updatedAtMillis DESC")
    suspend fun getAll(): List<DocumentGroupEntity>

    /** Folders/groups that contain zero live documents (trashed ones don't count). */
    @Query(
        """
        SELECT dg.id FROM document_groups dg
        WHERE dg.deletedAtMillis IS NULL AND NOT EXISTS (
            SELECT 1 FROM documents d WHERE d.groupId = dg.id AND d.deletedAtMillis IS NULL
        )
        """,
    )
    suspend fun getEmptyGroupIds(): List<String>

    /** Folders in Recently deleted with the documents that went with them, newest first. */
    @Query(
        """
        SELECT dg.id, dg.title, dg.deletedAtMillis,
               COUNT(d.id)                    AS documentCount,
               COALESCE(SUM(d.pageCount), 0)  AS totalPageCount,
               (SELECT d2.coverThumbnailPath
                FROM documents d2
                WHERE d2.groupId = dg.id AND d2.deletedWithGroup = 1
                ORDER BY d2.updatedAtMillis DESC
                LIMIT 1)                      AS coverThumbnailPath,
               (SELECT COUNT(*)
                FROM documents d3
                WHERE d3.releasedFromGroupId = dg.id
                  AND d3.groupId IS NULL AND d3.deletedAtMillis IS NULL) AS keptDocumentCount
        FROM document_groups dg
        LEFT JOIN documents d ON d.groupId = dg.id AND d.deletedWithGroup = 1
        WHERE dg.deletedAtMillis IS NOT NULL
        GROUP BY dg.id
        ORDER BY dg.deletedAtMillis DESC
        """,
    )
    fun observeTrashedGroups(): Flow<List<TrashedGroupRow>>

    @Query("SELECT id FROM document_groups WHERE deletedAtMillis IS NOT NULL")
    suspend fun getTrashedGroupIds(): List<String>

    @Query("SELECT id FROM document_groups WHERE deletedAtMillis IS NOT NULL AND deletedAtMillis <= :cutoffMillis")
    suspend fun getTrashedGroupIdsBefore(cutoffMillis: Long): List<String>

    @Query("UPDATE document_groups SET deletedAtMillis = :deletedAtMillis WHERE id = :groupId")
    suspend fun setDeletedAt(groupId: String, deletedAtMillis: Long?)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(group: DocumentGroupEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(groups: List<DocumentGroupEntity>)

    @Update
    suspend fun update(group: DocumentGroupEntity)

    @Query("DELETE FROM document_groups WHERE id = :groupId")
    suspend fun deleteById(groupId: String)
}

/** A folder in Recently deleted, with what went to the bin with it. */
data class TrashedGroupRow(
    val id: String,
    val title: String,
    val deletedAtMillis: Long,
    val documentCount: Int,
    val totalPageCount: Int,
    val coverThumbnailPath: String?,
    /** Documents moved to the library when only the folder was deleted; restoring puts them back. */
    val keptDocumentCount: Int,
)
