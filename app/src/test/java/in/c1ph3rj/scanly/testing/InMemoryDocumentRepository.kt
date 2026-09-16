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
) : DocumentRepository {
    override fun observeDocuments(): Flow<List<ScanDocument>> = library.documents

    override fun observeRecentDocuments(limit: Int): Flow<List<ScanDocument>> =
        library.documents.map { documents ->
            documents.sortedByDescending { it.updatedAtMillis }.take(limit)
        }

    override fun observeUngroupedDocuments(): Flow<List<ScanDocument>> =
        library.documents.map { documents -> documents.filter { it.groupId == null } }

    override fun observeDocument(documentId: String): Flow<ScanDocument?> =
        library.documents.map { documents -> documents.firstOrNull { it.id == documentId } }

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

    override suspend fun deleteDocument(documentId: String): ScanlyResult<Unit> =
        runLibraryResult("Could not delete the document.") {
            library.document(documentId) ?: error("Document not found.")
            library.deleteDocument(documentId)
        }

    override suspend fun deleteEmptyDocuments(): ScanlyResult<Int> =
        runLibraryResult("Could not remove empty documents.") {
            val emptyIds = library.documents.value.filter { it.pageCount == 0 }.map { it.id }
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
