package `in`.c1ph3rj.scanly.domain.usecase.pdftools

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import `in`.c1ph3rj.scanly.domain.model.PdfToolSource
import `in`.c1ph3rj.scanly.domain.model.PdfWorkingCopy
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentPagesUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveRecentlyDeletedUseCase
import `in`.c1ph3rj.scanly.testing.InMemoryDocumentRepository
import `in`.c1ph3rj.scanly.testing.InMemoryGroupRepository
import `in`.c1ph3rj.scanly.testing.InMemoryPageRepository
import `in`.c1ph3rj.scanly.testing.InMemoryPdfToolkitRepository
import `in`.c1ph3rj.scanly.testing.InMemoryScanlyLibrary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ImportPdfToLibraryUseCaseTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun fixture(): Fixture = Fixture(InMemoryScanlyLibrary(), tempFolder.root)

    private class Fixture(
        val library: InMemoryScanlyLibrary,
        root: File,
    ) {
        val documents = InMemoryDocumentRepository(library)
        val pages = InMemoryPageRepository(library, storageRoot = File(root, "library").absolutePath)
        val pdf = InMemoryPdfToolkitRepository(File(root, "exports"))
        val useCase = ImportPdfToLibraryUseCase(documents, pages, pdf)
        val copy = PdfWorkingCopy(
            file = PdfToolSource.AppFile(File(root, "working.pdf").absolutePath, "Contract.pdf"),
            pageCount = 3,
        )
        val observeDocuments = ObserveDocumentsUseCase(documents)
        val observeTrashed = ObserveRecentlyDeletedUseCase(
            documentRepository = documents,
            groupRepository = InMemoryGroupRepository(library),
            pageRepository = pages,
        )
        val observePages = ObserveDocumentPagesUseCase(pages)
    }

    @Test
    fun success_addsAllSelectedPagesAsOneDocument() = runBlocking {
        val fixture = fixture()

        val result = fixture.useCase(
            copy = fixture.copy,
            pageIndexes = listOf(0, 1, 2),
            title = "Contract",
            look = PdfImportLook.ORIGINAL,
        )

        val imported = (result as ScanlyResult.Success).value
        assertEquals(3, imported.pageCount)
        assertEquals(listOf(0, 1, 2), fixture.pdf.renderedPages.map { it.pageIndex })
        assertTrue(fixture.pdf.renderedPages.all { File(it.targetPath).length() > 0L })

        val pages = fixture.observePages(imported.documentId).first()
        assertEquals(3, pages.size)
        assertEquals(3, fixture.observeDocuments().first().single { it.id == imported.documentId }.pageCount)
    }

    @Test
    fun success_keepAsIs_usesOriginalFilterOnEveryPage() = runBlocking {
        val fixture = fixture()

        fixture.useCase(
            copy = fixture.copy,
            pageIndexes = listOf(0, 1, 2),
            title = "Contract",
            look = PdfImportLook.ORIGINAL,
        ).let { assertTrue(it is ScanlyResult.Success) }

        assertEquals(3, fixture.pages.receivedFlatPages.size)
        fixture.pages.receivedFlatPages.forEach { flatPage ->
            assertNotNull(flatPage)
            assertEquals(PageFilterPreset.ORIGINAL, flatPage!!.filterPreset)
        }
    }

    @Test
    fun success_enhance_usesAutoFilterOnEveryPage() = runBlocking {
        val fixture = fixture()

        fixture.useCase(
            copy = fixture.copy,
            pageIndexes = listOf(0, 1, 2),
            title = "Contract",
            look = PdfImportLook.ENHANCE,
        ).let { assertTrue(it is ScanlyResult.Success) }

        assertEquals(3, fixture.pages.receivedFlatPages.size)
        fixture.pages.receivedFlatPages.forEach { flatPage ->
            assertNotNull(flatPage)
            assertEquals(PageFilterPreset.AUTO, flatPage!!.filterPreset)
        }
    }

    @Test
    fun failureOnSecondPage_failsAndRemovesHalfBuiltDocumentPermanently() = runBlocking {
        val fixture = fixture()
        fixture.pdf.failRenderAtPage = 1

        val result = fixture.useCase(
            copy = fixture.copy,
            pageIndexes = listOf(0, 1, 2),
            title = "Contract",
            look = PdfImportLook.ORIGINAL,
        )

        assertTrue(result is ScanlyResult.Failure)
        assertTrue(fixture.observeDocuments().first().isEmpty())
        assertTrue(fixture.observeTrashed().first().isEmpty())
        assertTrue(fixture.library.documents.value.isEmpty())
        assertTrue(fixture.library.pages.value.isEmpty())
    }

    @Test
    fun emptyPageList_failsAndCreatesNoDocument() = runBlocking {
        val fixture = fixture()

        val result = fixture.useCase(
            copy = fixture.copy,
            pageIndexes = emptyList(),
            title = "Contract",
            look = PdfImportLook.ORIGINAL,
        )

        assertTrue(result is ScanlyResult.Failure)
        assertTrue(fixture.observeDocuments().first().isEmpty())
        assertTrue(fixture.library.documents.value.isEmpty())
        assertTrue(fixture.pdf.renderedPages.isEmpty())
    }
}
