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
                WHERE d2.groupId = dg.id
                ORDER BY d2.updatedAtMillis DESC
                LIMIT 1)                      AS coverThumbnailPath,
               (SELECT d2.updatedAtMillis
                FROM documents d2
                WHERE d2.groupId = dg.id
                ORDER BY d2.updatedAtMillis DESC
                LIMIT 1)                      AS coverUpdatedAtMillis
        FROM document_groups dg
        LEFT JOIN documents d ON d.groupId = dg.id
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
                WHERE d2.groupId = dg.id
                ORDER BY d2.updatedAtMillis DESC
                LIMIT 1)                      AS coverThumbnailPath,
               (SELECT d2.updatedAtMillis
                FROM documents d2
                WHERE d2.groupId = dg.id
                ORDER BY d2.updatedAtMillis DESC
                LIMIT 1)                      AS coverUpdatedAtMillis
        FROM document_groups dg
        LEFT JOIN documents d ON d.groupId = dg.id
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
                WHERE d2.groupId = dg.id
                ORDER BY d2.updatedAtMillis DESC
                LIMIT 1)                      AS coverThumbnailPath,
               (SELECT d2.updatedAtMillis
                FROM documents d2
                WHERE d2.groupId = dg.id
                ORDER BY d2.updatedAtMillis DESC
                LIMIT 1)                      AS coverUpdatedAtMillis
        FROM document_groups dg
        LEFT JOIN documents d ON d.groupId = dg.id
        WHERE dg.id = :groupId
        GROUP BY dg.id
        """,
    )
    fun observeGroupWithStats(groupId: String): Flow<DocumentGroupStats?>

    @Query("SELECT * FROM document_groups WHERE id = :groupId")
    suspend fun getGroup(groupId: String): DocumentGroupEntity?

    @Query("SELECT title FROM document_groups")
    suspend fun getAllTitles(): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(group: DocumentGroupEntity)

    @Update
    suspend fun update(group: DocumentGroupEntity)

    @Query("DELETE FROM document_groups WHERE id = :groupId")
    suspend fun deleteById(groupId: String)
}
