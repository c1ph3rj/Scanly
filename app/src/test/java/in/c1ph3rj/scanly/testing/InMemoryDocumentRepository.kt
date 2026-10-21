package `in`.c1ph3rj.scanly.testing

import `in`.c1ph3rj.scanly.core.common.DocumentPresentationFormatter
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.DocumentTitleFormat
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class InMemoryDocumentRepository(
    private val library: InMemoryScanlyLibrary,
    private val now: () -> Long = { library.now() },
) : DocumentRepository {
    private val liveDocuments: Flow<List<ScanDocument>> =
        library.documents.map { documents -> documents.filter { it.deletedAtMillis == null } }

    override fun observeDocuments(): Flow<List<ScanDocument>> = liveDocuments

    override fun observeRecentDocuments(limit: Int): Flow<List<ScanDocument>> =
        liveDocuments.map { documents ->
            documents.sortedByDescending { it.updatedAtMillis }.take(limit)
        }

    override fun observeUngroupedDocuments(): Flow<List<ScanDocument>> =
        liveDocuments.map { documents -> documents.filter { it.groupId == null } }

    override fun observeDocument(documentId: String): Flow<ScanDocument?> =
        library.documents.map { documents -> documents.firstOrNull { it.id == documentId } }

    /** Documents deleted on their own; ones that went with a folder are listed under the folder. */
    override fun observeTrashedDocuments(): Flow<List<ScanDocument>> = library.trashedDocuments

    override suspend fun getAllDocumentTitles(): List<String> =
        library.documents.value.map { it.title }

    override suspend fun suggestDocumentTitle(format: DocumentTitleFormat): String =
        DocumentPresentationFormatter.uniqueDocumentTitle(
            format = format,
            existingTitles = getAllDocumentTitles(),
        )

    override suspend fun createDocument(
        title: String,
        groupId: String?,
    ): ScanlyResult<String> = runLibraryResult("Could not create the document.") {
        if (groupId != null) {
            library.group(groupId) ?: error("Group not found.")
        }
        val normalizedTitle = DocumentPresentationFormatter.resolveUniqueTitle(
            baseTitle = DocumentPresentationFormatter.normalizeTitle(title),
            existingTitles = getAllDocumentTitles(),
        )
        val documentId = UUID.randomUUID().toString()
        val timestamp = library.now()
        library.upsertDocument(
            ScanDocument(
                id = documentId,
                title = normalizedTitle,
                pageCount = 0,
                coverThumbnailPath = null,
                rootDirectoryPath = "/documents/$documentId",
                createdAtMillis = timestamp,
                updatedAtMillis = timestamp,
                groupId = groupId,
            ),
        )
        documentId
    }

    override suspend fun createImportedDocument(groupId: String?): ScanlyResult<String> {
        val title = DocumentPresentationFormatter.uniqueImportedDocumentTitle(
            existingTitles = getAllDocumentTitles(),
        )
        return createDocument(title = title, groupId = groupId)
    }

    override suspend fun renameDocument(
        documentId: String,
        title: String,
    ): ScanlyResult<Unit> = runLibraryResult("Could not rename the document.") {
        val existing = library.document(documentId) ?: error("Document not found.")
        library.upsertDocument(
            existing.copy(
                title = DocumentPresentationFormatter.normalizeTitle(title),
                updatedAtMillis = library.now(),
            ),
        )
    }

    override suspend fun moveDocumentToTrash(documentId: String): ScanlyResult<Unit> =
        runLibraryResult("Could not move the document to Recently deleted.") {
            val existing = library.document(documentId) ?: error("Document not found.")
            library.upsertDocument(existing.copy(deletedAtMillis = now()))
        }

    /**
     * Like the DB: a document whose folder is still in Recently deleted comes back outside it.
     * The deleted-with-folder flag is left as it is (the real setDeletedAt does not clear it).
     */
    override suspend fun restoreDocument(documentId: String): ScanlyResult<Unit> =
        runLibraryResult("Could not restore the document.") {
            val existing = library.document(documentId) ?: error("Document not found.")
            library.upsertDocument(
                existing.copy(
                    deletedAtMillis = null,
                    groupId = existing.groupId?.takeUnless { library.isGroupTrashed(it) },
                ),
            )
        }

    override suspend fun deleteDocument(documentId: String): ScanlyResult<Unit> =
        runLibraryResult("Could not delete the document.") {
            library.document(documentId) ?: error("Document not found.")
            library.deleteDocument(documentId)
        }

    /** Only documents deleted on their own, like the DB query (folder documents go with the folder). */
    override suspend fun emptyTrash(): ScanlyResult<Int> =
        runLibraryResult("Could not empty Recently deleted.") {
            val trashedIds = ownTrashedIds { true }
            trashedIds.forEach(library::deleteDocument)
            trashedIds.size
        }

    override suspend fun purgeTrashDeletedBefore(cutoffMillis: Long): ScanlyResult<Int> =
        runLibraryResult("Could not clear old deleted documents.") {
            val dueIds = ownTrashedIds { deletedAt -> deletedAt <= cutoffMillis }
            dueIds.forEach(library::deleteDocument)
            dueIds.size
        }

    private fun ownTrashedIds(predicate: (deletedAtMillis: Long) -> Boolean): List<String> =
        library.documents.value
            .filter { document ->
                val deletedAt = document.deletedAtMillis ?: return@filter false
                predicate(deletedAt) && !library.isDocumentDeletedWithGroup(document.id)
            }
            .map { it.id }

    override suspend fun deleteEmptyDocuments(): ScanlyResult<Int> =
        runLibraryResult("Could not remove empty documents.") {
            val emptyIds = library.documents.value
                .filter { it.pageCount == 0 && it.deletedAtMillis == null }
                .map { it.id }
            emptyIds.forEach(library::deleteDocument)
            emptyIds.size
        }
}

internal inline fun <T> runLibraryResult(
    fallback: String,
    block: () -> T,
): ScanlyResult<T> = runCatching(block).fold(
    onSuccess = { ScanlyResult.Success(it) },
    onFailure = { throwable ->
        ScanlyResult.Failure(
            ScanlyError(message = throwable.message ?: fallback, cause = throwable),
        )
    },
)
