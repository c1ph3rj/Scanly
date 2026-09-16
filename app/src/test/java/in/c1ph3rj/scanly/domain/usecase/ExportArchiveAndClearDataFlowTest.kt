package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.PdfExportOptions
import `in`.c1ph3rj.scanly.domain.model.PdfPageNumber
import `in`.c1ph3rj.scanly.domain.model.PdfPageOrientation
import `in`.c1ph3rj.scanly.domain.model.PdfPageSize
import `in`.c1ph3rj.scanly.domain.model.RestoreMode
import `in`.c1ph3rj.scanly.testing.InMemoryAppDataRepository
import `in`.c1ph3rj.scanly.testing.InMemoryDocumentExportRepository
import `in`.c1ph3rj.scanly.testing.InMemoryDocumentRepository
import `in`.c1ph3rj.scanly.testing.InMemoryGroupRepository
import `in`.c1ph3rj.scanly.testing.InMemoryLibraryArchiveRepository
import `in`.c1ph3rj.scanly.testing.InMemoryPageRepository
import `in`.c1ph3rj.scanly.testing.InMemoryScanlyLibrary
import `in`.c1ph3rj.scanly.testing.RecordedExport
import `in`.c1ph3rj.scanly.testing.requireSuccess
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportArchiveAndClearDataFlowTest {
    private val library = InMemoryScanlyLibrary()
    private val documents = InMemoryDocumentRepository(library)
    private val groups = InMemoryGroupRepository(library)
    private val pages = InMemoryPageRepository(library)
    private val exportDirectory = createTempDirectory("scanly-export-flow").toFile()
    private val exports = InMemoryDocumentExportRepository(library, exportDirectory)
    private val archive = InMemoryLibraryArchiveRepository(library)
    private val appData = InMemoryAppDataRepository(library)

    private val createDocument = CreateDocumentUseCase(documents)
    private val renameDocument = RenameDocumentUseCase(documents)
    private val createGroup = CreateGroupUseCase(groups)
    private val setDocumentGroup = SetDocumentGroupUseCase(groups)
    private val prepareCapture = PreparePageCaptureUseCase(pages)
    private val finalizeCapture = FinalizeCapturedPageUseCase(pages)
    private val observeDocuments = ObserveDocumentsUseCase(documents)
    private val observeGroups = ObserveGroupsUseCase(groups)
    private val observePages = ObserveDocumentPagesUseCase(pages)

    private val exportDocumentPdf = ExportDocumentPdfUseCase(exports)
    private val exportDocumentImages = ExportDocumentImageArchiveUseCase(exports)
    private val exportGroupPdf = ExportGroupPdfUseCase(exports)
    private val exportGroupZip = ExportGroupZippedPdfsUseCase(exports)
    private val startBackup = StartLibraryBackupUseCase(archive)
    private val startRestore = StartLibraryRestoreUseCase(archive)
    private val clearAll = ClearAllAppDataUseCase(appData)

    @Test
    fun documentPdfAndImageZipExport_recordRequestAndWriteNonEmptyArtifacts() = runBlocking {
        val documentId = createDocument("Lease").requireSuccess()
        capturePage(documentId)
        val options = PdfExportOptions(
            orientation = PdfPageOrientation.PORTRAIT,
            pageSize = PdfPageSize.A4,
            pageNumber = PdfPageNumber.BOTTOM_CENTER,
            password = "open",
        )

        val pdf = exportDocumentPdf(documentId, options).requireSuccess()
        assertEquals("application/pdf", pdf.mimeType)
        assertTrue(File(pdf.filePath).length() > 0L)
        assertEquals(
            RecordedExport.DocumentPdf(documentId, options),
            exports.recordedRequests.single { it is RecordedExport.DocumentPdf },
        )

        val zip = exportDocumentImages(documentId).requireSuccess()
        assertEquals("application/zip", zip.mimeType)
        assertTrue(File(zip.filePath).length() > 0L)
        assertEquals(
            RecordedExport.DocumentImages(documentId),
            exports.recordedRequests.single { it is RecordedExport.DocumentImages },
        )
    }

    @Test
    fun groupMergedPdfAndZippedPdfSetExport_succeedWithRecordedOptions() = runBlocking {
        val groupId = createGroup("Tax").requireSuccess()
        val firstId = createDocument("W2").requireSuccess()
        val secondId = createDocument("1099").requireSuccess()
        setDocumentGroup(firstId, groupId).requireSuccess()
        setDocumentGroup(secondId, groupId).requireSuccess()
        capturePage(firstId)
        capturePage(secondId)
        val options = PdfExportOptions(pageSize = PdfPageSize.US_LETTER)

        val merged = exportGroupPdf(groupId, options) { _, _ -> }.requireSuccess()
        assertEquals("application/pdf", merged.mimeType)
        assertTrue(File(merged.filePath).length() > 0L)
        assertEquals(
            RecordedExport.GroupPdf(groupId, options),
            exports.recordedRequests.single { it is RecordedExport.GroupPdf },
        )

        val zipped = exportGroupZip(groupId, options) { _, _ -> }.requireSuccess()
        assertEquals("application/zip", zipped.mimeType)
        assertTrue(File(zipped.filePath).length() > 0L)
        assertEquals(
            RecordedExport.GroupZippedPdfs(groupId, options),
            exports.recordedRequests.single { it is RecordedExport.GroupZippedPdfs },
        )
    }

    @Test
    fun exportMissingDocument_returnsFailure() = runBlocking {
        val result = exportDocumentPdf("missing", PdfExportOptions())
        assertTrue(result is ScanlyResult.Failure)
    }

    @Test
    fun backupThenRestoreReplace_replacesCurrentLibrary() = runBlocking {
        val originalId = createDocument("Lease").requireSuccess()
        capturePage(originalId)
        startBackup().requireSuccess()
        val backupUri = archive.lastBackupUri!!

        renameDocument(originalId, "Lease updated").requireSuccess()
        createDocument("Tax").requireSuccess()
        assertEquals(2, observeDocuments().first().size)

        startRestore(backupUri, RestoreMode.REPLACE).requireSuccess()
        assertEquals(RestoreMode.REPLACE, archive.lastRestoreMode)
        assertEquals(backupUri, archive.lastRestoreUri)

        val restored = observeDocuments().first()
        assertEquals(listOf("Lease"), restored.map { it.title })
        assertNotEquals(originalId, restored.single().id)
        assertEquals(1, observePages(restored.single().id).first().size)
    }

    @Test
    fun backupThenRestoreMerge_keepsCurrentItemsAndAddsUniqueCopies() = runBlocking {
        val groupId = createGroup("Home").requireSuccess()
        val originalId = createDocument("Lease").requireSuccess()
        setDocumentGroup(originalId, groupId).requireSuccess()
        capturePage(originalId)
        startBackup().requireSuccess()
        val backupUri = archive.lastBackupUri!!

        createDocument("Receipt").requireSuccess()
        startRestore(backupUri, RestoreMode.MERGE).requireSuccess()
        assertEquals(RestoreMode.MERGE, archive.lastRestoreMode)

        val titles = observeDocuments().first().map { it.title }.sorted()
        assertEquals(listOf("Lease", "Lease (Restored)", "Receipt"), titles)
        val groupTitles = observeGroups().first().map { it.title }.sorted()
        assertEquals(listOf("Home", "Home (Restored)"), groupTitles)
        assertEquals(2, observeDocuments().first().sumOf { it.pageCount })
    }

    @Test
    fun clearAllAppData_wipesDocumentsGroupsAndPages() = runBlocking {
        val groupId = createGroup("Work").requireSuccess()
        val documentId = createDocument("Invoice").requireSuccess()
        setDocumentGroup(documentId, groupId).requireSuccess()
        capturePage(documentId)

        clearAll().requireSuccess()

        assertTrue(observeDocuments().first().isEmpty())
        assertTrue(observeGroups().first().isEmpty())
        assertTrue(library.pages.value.isEmpty())
    }

    private suspend fun capturePage(documentId: String): String {
        val draft = prepareCapture(documentId).requireSuccess()
        return finalizeCapture(draft).requireSuccess()
    }
}
