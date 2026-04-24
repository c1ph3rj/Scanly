package `in`.c1ph3rj.scanly.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import `in`.c1ph3rj.scanly.data.local.db.entity.DocumentGroupEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.DocumentGroupSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentGroupDao {
    @Query(
        """
        SELECT
            g.id,
            g.name,
            g.createdAtMillis,
            g.updatedAtMillis,
            COUNT(documents.id) AS documentCount,
            COALESCE(SUM(documents.pageCount), 0) AS pageCount,
            GROUP_CONCAT(documents.coverThumbnailPath, '|') AS coverThumbnailPaths
        FROM document_groups AS g
        LEFT JOIN documents ON documents.groupId = g.id
        GROUP BY g.id
        ORDER BY g.updatedAtMillis DESC
        """,
    )
    fun observeGroupSummaries(): Flow<List<DocumentGroupSummary>>

    @Query("SELECT * FROM document_groups WHERE id = :groupId")
    suspend fun getGroup(groupId: String): DocumentGroupEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(group: DocumentGroupEntity)

    @Update
    suspend fun update(group: DocumentGroupEntity)

    @Query("DELETE FROM document_groups WHERE id = :groupId")
    suspend fun deleteById(groupId: String)
}
