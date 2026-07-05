package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.domain.model.DocumentTitleFormat
import `in`.c1ph3rj.scanly.domain.repository.DocumentRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SuggestDocumentTitleUseCaseTest {
    @Test
    fun invoke_delegatesToRepository() = runBlocking {
        val repository = FakeDocumentRepository(
            suggestedTitle = "Scan 2026-06-30",
        )
        val useCase = SuggestDocumentTitleUseCase(repository)

        val title = useCase(DocumentTitleFormat.ScanIsoDate)

        assertEquals("Scan 2026-06-30", title)
        assertEquals(DocumentTitleFormat.ScanIsoDate, repository.lastSuggestedFormat)
    }

    private class FakeDocumentRepository(
        private val suggestedTitle: String,
    ) : DocumentRepository {
        var lastSuggestedFormat: DocumentTitleFormat? = null

        override fun observeDocuments() = error("Not used")

        override fun observeRecentDocuments(limit: Int) = error("Not used")

        override fun observeUngroupedDocuments() = error("Not used")

        override fun observeDocument(documentId: String) = error("Not used")

        override suspend fun getAllDocumentTitles(): List<String> = error("Not used")

        override suspend fun suggestDocumentTitle(format: DocumentTitleFormat): String {
            lastSuggestedFormat = format
            return suggestedTitle
        }

        override suspend fun createDocument(title: String, groupId: String?) = error("Not used")

        override suspend fun createImportedDocument(groupId: String?) = error("Not used")

        override suspend fun renameDocument(documentId: String, title: String) = error("Not used")

        override suspend fun deleteDocument(documentId: String) = error("Not used")
    }
}