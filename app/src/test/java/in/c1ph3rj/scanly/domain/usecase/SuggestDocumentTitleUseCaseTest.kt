package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.domain.model.DocumentTitleFormat
import `in`.c1ph3rj.scanly.domain.model.TitleSuggestionOption
import `in`.c1ph3rj.scanly.domain.repository.DocumentRepository
import `in`.c1ph3rj.scanly.testing.InMemoryNamingTemplateRepository
import `in`.c1ph3rj.scanly.testing.requireSuccess
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SuggestDocumentTitleUseCaseTest {
    @Test
    fun invoke_delegatesToRepository() = runBlocking {
        val repository = FakeDocumentRepository(
            suggestedTitle = "Scan 2026-06-30",
        )
        val useCase = SuggestDocumentTitleUseCase(repository, InMemoryNamingTemplateRepository())

        val title = useCase(DocumentTitleFormat.ScanIsoDate)

        assertEquals("Scan 2026-06-30", title)
        assertEquals(DocumentTitleFormat.ScanIsoDate, repository.lastSuggestedFormat)
    }

    @Test
    fun invoke_withoutDefaultTemplate_usesBuiltInDefaultFormat() = runBlocking {
        val repository = FakeDocumentRepository(suggestedTitle = "Scan built-in")
        val templates = InMemoryNamingTemplateRepository()
        templates.saveTemplate(null, "Receipt {n}").requireSuccess()
        val useCase = SuggestDocumentTitleUseCase(repository, templates)

        assertEquals("Scan built-in", useCase())
        assertEquals(DocumentTitleFormat.default, repository.lastSuggestedFormat)
        assertNull(repository.lastPattern)
    }

    @Test
    fun invoke_withDefaultTemplate_usesItsPattern() = runBlocking {
        val repository = FakeDocumentRepository(suggestedTitle = "unused")
        val templates = InMemoryNamingTemplateRepository()
        val template = templates.saveTemplate(null, "  Receipt {n}  ").requireSuccess()
        templates.setDefaultTemplate(template.id).requireSuccess()
        val useCase = SuggestDocumentTitleUseCase(repository, templates)

        assertEquals("pattern:Receipt {n}", useCase())
        assertEquals("Receipt {n}", repository.lastPattern)
        assertNull(repository.lastSuggestedFormat)
    }

    @Test
    fun invoke_afterDefaultCleared_fallsBackToBuiltIn() = runBlocking {
        val repository = FakeDocumentRepository(suggestedTitle = "Scan built-in")
        val templates = InMemoryNamingTemplateRepository()
        val template = templates.saveTemplate(null, "Invoice {iso}").requireSuccess()
        templates.setDefaultTemplate(template.id).requireSuccess()
        templates.setDefaultTemplate(null).requireSuccess()

        assertEquals("Scan built-in", SuggestDocumentTitleUseCase(repository, templates)())
    }

    @Test
    fun invoke_option_routesBuiltInAndTemplate() = runBlocking {
        val repository = FakeDocumentRepository(suggestedTitle = "Scan built-in")
        val templates = InMemoryNamingTemplateRepository()
        val template = templates.saveTemplate(null, "Invoice {n}").requireSuccess()
        val useCase = SuggestDocumentTitleUseCase(repository, templates)

        assertEquals("Scan built-in", useCase(TitleSuggestionOption.BuiltIn(DocumentTitleFormat.ScanIsoDate)))
        assertEquals(DocumentTitleFormat.ScanIsoDate, repository.lastSuggestedFormat)
        assertEquals("pattern:Invoice {n}", useCase(TitleSuggestionOption.Template(template)))
        assertEquals("Invoice {n}", repository.lastPattern)
    }

    private class FakeDocumentRepository(
        private val suggestedTitle: String,
    ) : DocumentRepository {
        var lastSuggestedFormat: DocumentTitleFormat? = null
        var lastPattern: String? = null

        override fun observeDocuments() = error("Not used")

        override fun observeRecentDocuments(limit: Int) = error("Not used")

        override fun observeUngroupedDocuments() = error("Not used")

        override fun observeDocument(documentId: String) = error("Not used")

        override suspend fun getAllDocumentTitles(): List<String> = error("Not used")

        override suspend fun suggestDocumentTitle(format: DocumentTitleFormat): String {
            lastSuggestedFormat = format
            return suggestedTitle
        }

        override suspend fun suggestDocumentTitleFromPattern(pattern: String): String {
            lastPattern = pattern
            return "pattern:$pattern"
        }

        override suspend fun createDocument(title: String, groupId: String?) = error("Not used")

        override suspend fun createImportedDocument(groupId: String?) = error("Not used")

        override suspend fun setPreferredScanMode(
            documentId: String,
            scanMode: `in`.c1ph3rj.scanly.domain.model.ScanMode,
        ) = error("Not used")

        override suspend fun renameDocument(documentId: String, title: String) = error("Not used")

        override suspend fun moveDocumentToTrash(documentId: String) = error("Not used")

        override suspend fun restoreDocument(documentId: String) = error("Not used")

        override fun observeTrashedDocuments() = error("Not used")

        override suspend fun deleteDocument(documentId: String) = error("Not used")

        override suspend fun emptyTrash() = error("Not used")

        override suspend fun purgeTrashDeletedBefore(cutoffMillis: Long) = error("Not used")

        override suspend fun deleteEmptyDocuments() = error("Not used")
    }
}