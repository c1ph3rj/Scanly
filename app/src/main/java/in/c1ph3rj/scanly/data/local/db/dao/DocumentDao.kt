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
    @Query("SELECT * FROM documents ORDER BY updatedAtMillis DESC")
    fun observeDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents ORDER BY updatedAtMillis DESC LIMIT :limit")
    fun observeRecentDocuments(limit: Int): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE groupId IS NULL ORDER BY updatedAtMillis DESC")
    fun observeUngroupedDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE groupId = :groupId ORDER BY title ASC")
    fun observeDocumentsByGroup(groupId: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE groupId = :groupId ORDER BY title ASC")
    suspend fun getDocumentsByGroup(groupId: String): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE id = :documentId")
    fun observeDocument(documentId: String): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE id = :documentId")
    suspend fun getDocument(documentId: String): DocumentEntity?

    @Query("SELECT title FROM documents")
    suspend fun getAllTitles(): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(document: DocumentEntity)

    @Update
    suspend fun update(document: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :documentId")
    suspend fun deleteById(documentId: String)
}
