package `in`.c1ph3rj.scanly.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import `in`.c1ph3rj.scanly.data.local.db.entity.DocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents WHERE deletedAtMillis IS NULL ORDER BY updatedAtMillis DESC")
    fun observeDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE deletedAtMillis IS NULL ORDER BY updatedAtMillis DESC LIMIT :limit")
    fun observeRecentDocuments(limit: Int): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE groupId IS NULL AND deletedAtMillis IS NULL ORDER BY updatedAtMillis DESC")
    fun observeUngroupedDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE groupId = :groupId AND deletedAtMillis IS NULL ORDER BY title ASC")
    fun observeDocumentsByGroup(groupId: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE groupId = :groupId AND deletedAtMillis IS NULL ORDER BY title ASC")
    suspend fun getDocumentsByGroup(groupId: String): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE id = :documentId")
    fun observeDocument(documentId: String): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE id = :documentId")
    suspend fun getDocument(documentId: String): DocumentEntity?

    @Query("SELECT title FROM documents")
    suspend fun getAllTitles(): List<String>

    @Query("SELECT * FROM documents ORDER BY updatedAtMillis DESC")
    suspend fun getAll(): List<DocumentEntity>

    /**
     * Live documents with no pages (including pageCount drift / abandoned captures). Trashed
     * ones are left to Recently deleted, which removes them when their time is up.
     */
    @Query(
        """
        SELECT d.id FROM documents d
        WHERE d.deletedAtMillis IS NULL AND NOT EXISTS (
            SELECT 1 FROM scan_pages p WHERE p.documentId = d.id
        )
        """,
    )
    suspend fun getEmptyDocumentIds(): List<String>

    /**
     * Documents deleted on their own, most recently deleted first. Documents that went with
     * their folder are listed under that folder instead.
     */
    @Query("SELECT * FROM documents WHERE deletedAtMillis IS NOT NULL AND deletedWithGroup = 0 ORDER BY deletedAtMillis DESC")
    fun observeTrashedDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT id FROM documents WHERE deletedAtMillis IS NOT NULL AND deletedWithGroup = 0")
    suspend fun getTrashedDocumentIds(): List<String>

    /** Documents deleted on their own at or before [cutoffMillis], due for permanent removal. */
    @Query(
        "SELECT id FROM documents WHERE deletedAtMillis IS NOT NULL AND deletedWithGroup = 0 " +
            "AND deletedAtMillis <= :cutoffMillis",
    )
    suspend fun getTrashedDocumentIdsBefore(cutoffMillis: Long): List<String>

    /** Sends a folder's live documents to Recently deleted with it. */
    @Query(
        "UPDATE documents SET deletedAtMillis = :deletedAtMillis, deletedWithGroup = 1 " +
            "WHERE groupId = :groupId AND deletedAtMillis IS NULL",
    )
    suspend fun trashWithGroup(groupId: String, deletedAtMillis: Long)

    /** Brings back the documents that went to Recently deleted with their folder. */
    @Query(
        "UPDATE documents SET deletedAtMillis = NULL, deletedWithGroup = 0 " +
            "WHERE groupId = :groupId AND deletedWithGroup = 1",
    )
    suspend fun restoreWithGroup(groupId: String)

    /** "Delete folder only": the folder's live documents move to the library and remember it. */
    @Query(
        "UPDATE documents SET groupId = NULL, releasedFromGroupId = :groupId " +
            "WHERE groupId = :groupId AND deletedAtMillis IS NULL",
    )
    suspend fun releaseFromGroup(groupId: String)

    /** Puts released documents back in their restored folder, unless they have moved on since. */
    @Query(
        "UPDATE documents SET groupId = :groupId WHERE releasedFromGroupId = :groupId " +
            "AND groupId IS NULL AND deletedAtMillis IS NULL",
    )
    suspend fun returnReleasedToGroup(groupId: String)

    @Query("UPDATE documents SET releasedFromGroupId = NULL WHERE releasedFromGroupId = :groupId")
    suspend fun forgetReleasedGroup(groupId: String)

    @Query("SELECT id FROM documents WHERE groupId = :groupId AND deletedWithGroup = 1")
    suspend fun getIdsDeletedWithGroup(groupId: String): List<String>

    /** Takes a document out of its folder (used when restoring it while the folder is in the bin). */
    @Query("UPDATE documents SET groupId = NULL WHERE id = :documentId")
    suspend fun clearGroup(documentId: String)

    /** Trashes or restores one document on its own, detaching it from any folder-level delete. */
    @Query("UPDATE documents SET deletedAtMillis = :deletedAtMillis, deletedWithGroup = 0 WHERE id = :documentId")
    suspend fun setDeletedAt(documentId: String, deletedAtMillis: Long?)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(document: DocumentEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(documents: List<DocumentEntity>)

    @Update
    suspend fun update(document: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :documentId")
    suspend fun deleteById(documentId: String)
}
