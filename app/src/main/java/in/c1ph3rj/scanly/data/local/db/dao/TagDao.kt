package `in`.c1ph3rj.scanly.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import `in`.c1ph3rj.scanly.data.local.db.entity.DocumentTagEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.TagEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.TagWithCount
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    /** Every tag with its live document count, by name ignoring case. */
    @Query(
        """
        SELECT t.id, t.name, t.createdAtMillis,
            (SELECT COUNT(*) FROM document_tags dt
                INNER JOIN documents d ON d.id = dt.documentId
                WHERE dt.tagId = t.id AND d.deletedAtMillis IS NULL) AS documentCount
        FROM tags t
        ORDER BY t.name COLLATE NOCASE ASC, t.createdAtMillis ASC
        """,
    )
    fun observeTagsWithCounts(): Flow<List<TagWithCount>>

    /** Tags on [documentId] with their live document counts, by name ignoring case. */
    @Query(
        """
        SELECT t.id, t.name, t.createdAtMillis,
            (SELECT COUNT(*) FROM document_tags dt
                INNER JOIN documents d ON d.id = dt.documentId
                WHERE dt.tagId = t.id AND d.deletedAtMillis IS NULL) AS documentCount
        FROM tags t
        WHERE t.id IN (SELECT tagId FROM document_tags WHERE documentId = :documentId)
        ORDER BY t.name COLLATE NOCASE ASC, t.createdAtMillis ASC
        """,
    )
    fun observeTagsForDocument(documentId: String): Flow<List<TagWithCount>>

    /** Every document-tag link, trashed documents included. */
    @Query("SELECT * FROM document_tags")
    fun observeDocumentTags(): Flow<List<DocumentTagEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM document_tags dt
        INNER JOIN documents d ON d.id = dt.documentId
        WHERE dt.tagId = :tagId AND d.deletedAtMillis IS NULL
        """,
    )
    suspend fun getLiveDocumentCount(tagId: String): Int

    @Query("SELECT * FROM tags WHERE id = :tagId")
    suspend fun getTag(tagId: String): TagEntity?

    @Query("SELECT * FROM tags WHERE nameKey = :nameKey")
    suspend fun getTagByKey(nameKey: String): TagEntity?

    @Query("SELECT id FROM tags WHERE id IN (:tagIds)")
    suspend fun getExistingTagIds(tagIds: List<String>): List<String>

    @Query("SELECT tagId FROM document_tags WHERE documentId = :documentId")
    suspend fun getTagIdsForDocument(documentId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTag(tag: TagEntity)

    @Update
    suspend fun updateTag(tag: TagEntity)

    @Query("DELETE FROM tags WHERE id = :tagId")
    suspend fun deleteTag(tagId: String)

    /** Links to a missing document or tag fail the FK; links that already exist are kept. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDocumentTags(links: List<DocumentTagEntity>)

    @Query("DELETE FROM document_tags WHERE documentId = :documentId")
    suspend fun deleteDocumentTags(documentId: String)

    @Query("DELETE FROM document_tags WHERE tagId = :tagId")
    suspend fun deleteTagLinks(tagId: String)
}
