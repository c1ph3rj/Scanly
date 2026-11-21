package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.testing.InMemoryDocumentRepository
import `in`.c1ph3rj.scanly.testing.InMemoryNamingTemplateRepository
import `in`.c1ph3rj.scanly.testing.InMemoryScanlyLibrary
import `in`.c1ph3rj.scanly.testing.InMemoryTagRepository
import `in`.c1ph3rj.scanly.testing.requireSuccess
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tag and naming-template flows through the use cases, on the in-memory fakes the ViewModel tests share. */
class TagsAndNamingTemplatesFlowTest {
    private val library = InMemoryScanlyLibrary()
    private val documents = InMemoryDocumentRepository(library)
    private val tags = InMemoryTagRepository(library)
    private val templates = InMemoryNamingTemplateRepository()

    private val createTag = CreateTagUseCase(tags)
    private val renameTag = RenameTagUseCase(tags)
    private val deleteTag = DeleteTagUseCase(tags)
    private val setDocumentTags = SetDocumentTagsUseCase(tags)
    private val observeTags = ObserveTagsUseCase(tags)
    private val observeDocumentTags = ObserveDocumentTagsUseCase(tags)
    private val observeTagIdsByDocument = ObserveTagIdsByDocumentUseCase(tags)

    private val saveTemplate = SaveNamingTemplateUseCase(templates)
    private val deleteTemplate = DeleteNamingTemplateUseCase(templates)
    private val setDefaultTemplate = SetDefaultNamingTemplateUseCase(templates)
    private val observeTemplates = ObserveNamingTemplatesUseCase(templates)
    private val suggestTitle = SuggestDocumentTitleUseCase(documents, templates)

    @Test
    fun createTag_normalizesAndReturnsExistingForSameName() = runBlocking {
        val travel = createTag("  Travel   plans ").requireSuccess()
        assertEquals("Travel plans", travel.name)
        assertEquals(travel.id, createTag("travel PLANS").requireSuccess().id)
        assertTrue(createTag("   ") is ScanlyResult.Failure)
        assertEquals(listOf("Travel plans"), observeTags().first().map { it.name })
    }

    @Test
    fun tags_areSortedByNameIgnoringCase() = runBlocking {
        createTag("beta").requireSuccess()
        createTag("Alpha").requireSuccess()
        createTag("gamma").requireSuccess()
        assertEquals(listOf("Alpha", "beta", "gamma"), observeTags().first().map { it.name })
    }

    @Test
    fun renameTag_rejectsBlankAndNamesOfOtherTags() = runBlocking {
        val work = createTag("Work").requireSuccess()
        createTag("Home").requireSuccess()
        assertTrue(renameTag(work.id, " ") is ScanlyResult.Failure)
        assertTrue(renameTag(work.id, "home") is ScanlyResult.Failure)
        renameTag(work.id, "WORK").requireSuccess()
        assertEquals(listOf("Home", "WORK"), observeTags().first().map { it.name })
    }

    @Test
    fun setDocumentTags_replacesLinksCountsLiveDocumentsAndKeepsUpdatedAt() = runBlocking {
        val first = documents.createDocument("First").requireSuccess()
        val second = documents.createDocument("Second").requireSuccess()
        val updatedAt = library.document(first)!!.updatedAtMillis
        val travel = createTag("Travel").requireSuccess()
        val receipts = createTag("Receipts").requireSuccess()

        setDocumentTags(first, setOf(travel.id, receipts.id, "missing")).requireSuccess()
        setDocumentTags(second, setOf(travel.id)).requireSuccess()
        assertEquals(setOf(travel.id, receipts.id), observeTagIdsByDocument().first()[first])
        assertEquals(listOf("Receipts", "Travel"), observeDocumentTags(first).first().map { it.name })
        assertEquals(2, observeTags().first().single { it.id == travel.id }.documentCount)
        assertEquals(updatedAt, library.document(first)!!.updatedAtMillis)

        setDocumentTags(first, setOf(receipts.id)).requireSuccess()
        assertEquals(setOf(receipts.id), observeTagIdsByDocument().first()[first])

        documents.moveDocumentToTrash(second).requireSuccess()
        assertEquals(0, observeTags().first().single { it.id == travel.id }.documentCount)
        // Trashed documents keep their tags for a restore.
        assertEquals(setOf(travel.id), observeTagIdsByDocument().first()[second])

        documents.deleteDocument(second).requireSuccess()
        assertNull(observeTagIdsByDocument().first()[second])
        assertTrue(setDocumentTags("missing-document", setOf(travel.id)) is ScanlyResult.Failure)
    }

    @Test
    fun deleteTag_removesItFromDocuments() = runBlocking {
        val document = documents.createDocument("Doc").requireSuccess()
        val tag = createTag("Temp").requireSuccess()
        setDocumentTags(document, setOf(tag.id)).requireSuccess()

        deleteTag(tag.id).requireSuccess()

        assertTrue(observeTags().first().isEmpty())
        assertNull(observeTagIdsByDocument().first()[document])
        assertTrue(library.document(document) != null)
    }

    @Test
    fun saveTemplate_validatesTrimsAndRejectsDuplicates() = runBlocking {
        val receipt = saveTemplate(null, "  Receipt {n} ").requireSuccess()
        assertEquals("Receipt {n}", receipt.pattern)
        assertTrue(saveTemplate(null, "receipt {N}") is ScanlyResult.Failure)
        assertTrue(saveTemplate(null, "Bad {token}") is ScanlyResult.Failure)
        assertTrue(saveTemplate(null, "  ") is ScanlyResult.Failure)
        // Saving a template with its own pattern is not a clash.
        saveTemplate(receipt.id, "RECEIPT {n}").requireSuccess()

        val invoice = saveTemplate(null, "Invoice {iso}").requireSuccess()
        assertEquals(listOf(receipt.id, invoice.id), observeTemplates().first().map { it.id })
    }

    @Test
    fun defaultTemplate_isUniqueAndDrivesSuggestions() = runBlocking {
        val receipt = saveTemplate(null, "Receipt {n}").requireSuccess()
        val invoice = saveTemplate(null, "Invoice {n}").requireSuccess()

        setDefaultTemplate(receipt.id).requireSuccess()
        setDefaultTemplate(invoice.id).requireSuccess()
        assertEquals(listOf(invoice.id), observeTemplates().first().filter { it.isDefault }.map { it.id })
        assertTrue(setDefaultTemplate("missing") is ScanlyResult.Failure)

        assertEquals("Invoice 1", suggestTitle())
        documents.createDocument("Invoice 1").requireSuccess()
        assertEquals("Invoice 2", suggestTitle())

        deleteTemplate(invoice.id).requireSuccess()
        assertNull(templates.getDefaultTemplate())
        assertTrue(observeTemplates().first().none { it.isDefault })
    }

    @Test
    fun suggestFromPatternWithoutNumber_fallsBackToSuffixDeduplication() = runBlocking {
        val plain = saveTemplate(null, "Contract").requireSuccess()
        setDefaultTemplate(plain.id).requireSuccess()
        documents.createDocument("Contract").requireSuccess()

        assertEquals("Contract (2)", suggestTitle())
    }
}
