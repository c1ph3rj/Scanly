package `in`.c1ph3rj.scanly.domain.repository

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.DocumentTag
import kotlinx.coroutines.flow.Flow

interface TagRepository {
    /** Every tag with its live document count, sorted by name (case-insensitive). */
    fun observeTags(): Flow<List<DocumentTag>>

    /** Tags on [documentId], sorted by name. */
    fun observeDocumentTags(documentId: String): Flow<List<DocumentTag>>

    /** Tag ids per document id, for every document that has at least one tag (trashed included). */
    fun observeTagIdsByDocument(): Flow<Map<String, Set<String>>>

    /**
     * Creates a tag, or returns the existing one with the same name (case-insensitive).
     * Fails when the normalized name is blank.
     */
    suspend fun createTag(name: String): ScanlyResult<DocumentTag>

    /** Fails when the name is blank or another tag already has it. */
    suspend fun renameTag(tagId: String, name: String): ScanlyResult<Unit>

    /** Deletes the tag and removes it from every document. Documents are kept. */
    suspend fun deleteTag(tagId: String): ScanlyResult<Unit>

    /** Replaces [documentId]'s tags with [tagIds]. Unknown ids are ignored. Doesn't touch `updatedAtMillis`. */
    suspend fun setDocumentTags(documentId: String, tagIds: Set<String>): ScanlyResult<Unit>
}
