package `in`.c1ph3rj.scanly.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import `in`.c1ph3rj.scanly.data.local.db.entity.DocumentEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.DocumentWithGroup
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY updatedAtMillis DESC")
    fun observeDocuments(): Flow<List<DocumentEntity>>

    @Query(
        """
        SELECT
            documents.id,
            documents.title,
            documents.pageCount,
            documents.coverThumbnailPath,
            documents.preferredFilterPreset,
            documents.groupId,
            document_groups.name AS groupName,
            documents.rootDirectoryPath,
            documents.createdAtMillis,
            documents.updatedAtMillis
        FROM documents
        LEFT JOIN document_groups ON document_groups.id = documents.groupId
        ORDER BY documents.updatedAtMillis DESC
        """,
    )
    fun observeDocumentsWithGroup(): Flow<List<DocumentWithGroup>>

    @Query("SELECT * FROM documents WHERE id = :documentId")
    fun observeDocument(documentId: String): Flow<DocumentEntity?>

    @Query(
        """
        SELECT
            documents.id,
            documents.title,
            documents.pageCount,
            documents.coverThumbnailPath,
            documents.preferredFilterPreset,
            documents.groupId,
            document_groups.name AS groupName,
            documents.rootDirectoryPath,
            documents.createdAtMillis,
            documents.updatedAtMillis
        FROM documents
        LEFT JOIN document_groups ON document_groups.id = documents.groupId
        WHERE documents.id = :documentId
        """,
    )
    fun observeDocumentWithGroup(documentId: String): Flow<DocumentWithGroup?>

    @Query(
        """
        SELECT
            documents.id,
            documents.title,
            documents.pageCount,
            documents.coverThumbnailPath,
            documents.preferredFilterPreset,
            documents.groupId,
            document_groups.name AS groupName,
            documents.rootDirectoryPath,
            documents.createdAtMillis,
            documents.updatedAtMillis
        FROM documents
        LEFT JOIN document_groups ON document_groups.id = documents.groupId
        WHERE documents.groupId = :groupId
        ORDER BY documents.updatedAtMillis DESC
        """,
    )
    fun observeDocumentsInGroup(groupId: String): Flow<List<DocumentWithGroup>>

    @Query("SELECT * FROM documents WHERE id = :documentId")
    suspend fun getDocument(documentId: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(document: DocumentEntity)

    @Update
    suspend fun update(document: DocumentEntity)

    @Query("UPDATE documents SET groupId = :groupId, updatedAtMillis = :updatedAtMillis WHERE id = :documentId")
    suspend fun updateDocumentGroup(
        documentId: String,
        groupId: String?,
        updatedAtMillis: Long,
    )

    @Query("UPDATE documents SET groupId = NULL, updatedAtMillis = :updatedAtMillis WHERE groupId = :groupId")
    suspend fun clearGroupFromDocuments(
        groupId: String,
        updatedAtMillis: Long,
    )

    @Query("DELETE FROM documents WHERE id = :documentId")
    suspend fun deleteById(documentId: String)
}
