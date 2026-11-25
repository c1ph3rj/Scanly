package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.testing.InMemoryDocumentRepository
import `in`.c1ph3rj.scanly.testing.InMemoryNamingTemplateRepository
import `in`.c1ph3rj.scanly.testing.InMemoryScanlyLibrary
import `in`.c1ph3rj.scanly.testing.requireSuccess
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Imported and shared images get the default naming template, like every other new document. */
class CreateImportedDocumentNamingTest {
    private val library = InMemoryScanlyLibrary()
    private val documents = InMemoryDocumentRepository(library)
    private val templates = InMemoryNamingTemplateRepository()
    private val createDocument = CreateDocumentUseCase(documents, templates)

    private suspend fun titleOf(id: String): String =
        documents.observeDocuments().first().single { it.id == id }.title

    @Test
    fun createImported_withoutDefaultTemplate_usesImportedName() = runBlocking {
        templates.saveTemplate(null, "Receipt {n}").requireSuccess()

        val id = createDocument.createImported().requireSuccess()

        assertTrue(titleOf(id).startsWith("Imported"))
    }

    @Test
    fun createImported_withDefaultTemplate_usesTemplate() = runBlocking {
        val template = templates.saveTemplate(null, "Receipt {n}").requireSuccess()
        templates.setDefaultTemplate(template.id).requireSuccess()

        val first = createDocument.createImported().requireSuccess()
        val second = createDocument.createImported().requireSuccess()

        assertEquals("Receipt 1", titleOf(first))
        assertEquals("Receipt 2", titleOf(second))
    }
}
