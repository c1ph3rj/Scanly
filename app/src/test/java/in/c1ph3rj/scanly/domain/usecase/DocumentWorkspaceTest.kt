package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.model.DocumentTitleFormat
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.model.GroupTitleFormat
import `in`.c1ph3rj.scanly.domain.model.ImportImagesProgress
import `in`.c1ph3rj.scanly.domain.model.ImportStage
import `in`.c1ph3rj.scanly.domain.model.PageCaptureDraft
import `in`.c1ph3rj.scanly.domain.model.PageFilterAdjustments
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import `in`.c1ph3rj.scanly.domain.model.PdfExportOptions
import `in`.c1ph3rj.scanly.domain.model.SavedExport
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import `in`.c1ph3rj.scanly.domain.model.ShareArtifact
import `in`.c1ph3rj.scanly.domain.repository.DocumentExportRepository
import `in`.c1ph3rj.scanly.domain.repository.DocumentRepository
import `in`.c1ph3rj.scanly.domain.repository.ExportStorageRepository
import `in`.c1ph3rj.scanly.domain.repository.GroupRepository
import `in`.c1ph3rj.scanly.domain.repository.ImageImportRepository
import `in`.c1ph3rj.scanly.domain.repository.PageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Critical-path composition tests for document detail export/import orchestration.
 */
class DocumentWorkspaceTest {

    @Test
    fun exportPdfAndSave_savesGeneratedArtifact() = runBlocking {
        val export = FakeDocumentExportRepository(
            pdfArtifact = ExportArtifact(
                filePath = "/cache/doc.pdf",
                fileName = "doc.pdf",
                mimeType = "application/pdf",
            ),
        )
        val storage = FakeExportStorageRepository()
        val workspace = DocumentWorkspace(
            documentRepository = UnusedDocumentRepository,
            pageRepository = UnusedPageRepository,
            groupRepository = UnusedGroupRepository,
            documentExportRepository = export,
            exportStorageRepository = storage,
            imageImportRepository = UnusedImageImportRepository,
        )

        val result = workspace.exportPdfAndSave("doc-1", PdfExportOptions())

        assertTrue(result is ScanlyResult.Success)
        val saved = (result as ScanlyResult.Success).value
        assertEquals("doc.pdf", saved.fileName)
        assertEquals("Downloads/Scanly", saved.destinationLabel)
        assertEquals(1, storage.saveCount)
        assertEquals("/cache/doc.pdf", storage.lastSavedPath)
    }

    @Test
    fun exportPdfAndSave_doesNotSaveWhenExportFails() = runBlocking {
        val export = FakeDocumentExportRepository(
            pdfFailure = ScanlyError("export failed"),
        )
        val storage = FakeExportStorageRepository()
        val workspace = DocumentWorkspace(
            documentRepository = UnusedDocumentRepository,
            pageRepository = UnusedPageRepository,
            groupRepository = UnusedGroupRepository,
            documentExportRepository = export,
            exportStorageRepository = storage,
            imageImportRepository = UnusedImageImportRepository,
        )

        val result = workspace.exportPdfAndSave("doc-1", PdfExportOptions())

        assertTrue(result is ScanlyResult.Failure)
        assertEquals("export failed", (result as ScanlyResult.Failure).error.message)
        assertEquals(0, storage.saveCount)
    }

    @Test
    fun exportImageArchiveAndSave_composesGenerateThenSave() = runBlocking {
        val export = FakeDocumentExportRepository(
            zipArtifact = ExportArtifact(
                filePath = "/cache/pages.zip",
                fileName = "pages.zip",
                mimeType = "application/zip",
            ),
        )
        val storage = FakeExportStorageRepository()
        val workspace = DocumentWorkspace(
            documentRepository = UnusedDocumentRepository,
            pageRepository = UnusedPageRepository,
            groupRepository = UnusedGroupRepository,
            documentExportRepository = export,
            exportStorageRepository = storage,
            imageImportRepository = UnusedImageImportRepository,
        )

        val result = workspace.exportImageArchiveAndSave("doc-1")

        assertTrue(result is ScanlyResult.Success)
        assertEquals("pages.zip", (result as ScanlyResult.Success).value.fileName)
        assertEquals(1, storage.saveCount)
    }

    @Test
    fun importImages_forwardsUriStringsAndProgress() = runBlocking {
        val importer = RecordingImageImportRepository()
        val workspace = DocumentWorkspace(
            documentRepository = UnusedDocumentRepository,
            pageRepository = UnusedPageRepository,
            groupRepository = UnusedGroupRepository,
            documentExportRepository = FakeDocumentExportRepository(),
            exportStorageRepository = FakeExportStorageRepository(),
            imageImportRepository = importer,
        )
        val progress = mutableListOf<ImportImagesProgress>()

        val result = workspace.importImages(
            documentId = "doc-1",
            imageUriStrings = listOf("content://a", "content://b"),
            onProgress = { progress += it },
        )

        assertTrue(result is ScanlyResult.Success)
        assertEquals(listOf("content://a", "content://b"), importer.lastUris)
        assertEquals("doc-1", importer.lastDocumentId)
        assertEquals(2, progress.size)
        assertEquals(ImportStage.Preparing, progress.first().stage)
        assertEquals(ImportStage.Finalizing, progress.last().stage)
    }

    @Test
    fun renameDocument_delegatesToDocumentRepository() = runBlocking {
        val documents = RecordingDocumentRepository()
        val workspace = DocumentWorkspace(
            documentRepository = documents,
            pageRepository = UnusedPageRepository,
            groupRepository = UnusedGroupRepository,
            documentExportRepository = FakeDocumentExportRepository(),
            exportStorageRepository = FakeExportStorageRepository(),
            imageImportRepository = UnusedImageImportRepository,
        )

        val result = workspace.renameDocument("doc-1", "Invoice")

        assertTrue(result is ScanlyResult.Success)
        assertEquals("doc-1" to "Invoice", documents.lastRename)
    }

    private class FakeDocumentExportRepository(
        private val pdfArtifact: ExportArtifact? = null,
        private val zipArtifact: ExportArtifact? = null,
        private val pdfFailure: ScanlyError? = null,
    ) : DocumentExportRepository {
        override suspend fun exportPdf(
            documentId: String,
            options: PdfExportOptions,
        ): ScanlyResult<ExportArtifact> {
            pdfFailure?.let { return ScanlyResult.Failure(it) }
            return ScanlyResult.Success(
                pdfArtifact ?: error("pdfArtifact required for success path"),
            )
        }

        override suspend fun preparePdfShare(
            documentId: String,
            options: PdfExportOptions,
        ): ScanlyResult<ShareArtifact> = error("Not used")

        override suspend fun exportImageArchive(documentId: String): ScanlyResult<ExportArtifact> =
            ScanlyResult.Success(zipArtifact ?: error("zipArtifact required"))

        override suspend fun prepareImageShare(documentId: String): ScanlyResult<ShareArtifact> =
            error("Not used")

        override suspend fun exportGroupAsSinglePdf(
            groupId: String,
            options: PdfExportOptions,
            onProgress: (Int, Int) -> Unit,
        ): ScanlyResult<ExportArtifact> = error("Not used")

        override suspend fun exportGroupAsZippedPdfs(
            groupId: String,
            options: PdfExportOptions,
            onProgress: (Int, Int) -> Unit,
        ): ScanlyResult<ExportArtifact> = error("Not used")

        override suspend fun prepareGroupSinglePdfShare(
            groupId: String,
            options: PdfExportOptions,
            onProgress: (Int, Int) -> Unit,
        ): ScanlyResult<ShareArtifact> = error("Not used")

        override suspend fun prepareGroupZippedPdfsShare(
            groupId: String,
            options: PdfExportOptions,
            onProgress: (Int, Int) -> Unit,
        ): ScanlyResult<ShareArtifact> = error("Not used")
    }

    private class FakeExportStorageRepository : ExportStorageRepository {
        var saveCount: Int = 0
        var lastSavedPath: String? = null

        override suspend fun saveExport(artifact: ExportArtifact): ScanlyResult<SavedExport> {
            saveCount += 1
            lastSavedPath = artifact.filePath
            return ScanlyResult.Success(
                SavedExport(
                    fileName = artifact.fileName,
                    destinationLabel = "Downloads/Scanly",
                    uriString = "file://${artifact.filePath}",
                ),
            )
        }
    }

    private class RecordingImageImportRepository : ImageImportRepository {
        var lastDocumentId: String? = null
        var lastUris: List<String>? = null

        override suspend fun importImages(
            documentId: String,
            imageUriStrings: List<String>,
            onProgress: suspend (ImportImagesProgress) -> Unit,
        ): ScanlyResult<Unit> {
            lastDocumentId = documentId
            lastUris = imageUriStrings
            onProgress(ImportImagesProgress(1, imageUriStrings.size, ImportStage.Preparing))
            onProgress(
                ImportImagesProgress(
                    imageUriStrings.size,
                    imageUriStrings.size,
                    ImportStage.Finalizing,
                ),
            )
            return ScanlyResult.Success(Unit)
        }
    }

    private class RecordingDocumentRepository : DocumentRepository {
        var lastRename: Pair<String, String>? = null

        override fun observeDocuments(): Flow<List<ScanDocument>> = flowOf(emptyList())
        override fun observeRecentDocuments(limit: Int): Flow<List<ScanDocument>> = flowOf(emptyList())
        override fun observeUngroupedDocuments(): Flow<List<ScanDocument>> = flowOf(emptyList())
        override fun observeDocument(documentId: String): Flow<ScanDocument?> = flowOf(null)
        override suspend fun getAllDocumentTitles(): List<String> = emptyList()
        override suspend fun suggestDocumentTitle(format: DocumentTitleFormat): String = "x"
        override suspend fun createDocument(title: String, groupId: String?) =
            ScanlyResult.Success("id")
        override suspend fun createImportedDocument(groupId: String?) = ScanlyResult.Success("id")
        override suspend fun renameDocument(documentId: String, title: String): ScanlyResult<Unit> {
            lastRename = documentId to title
            return ScanlyResult.Success(Unit)
        }
        override suspend fun deleteDocument(documentId: String) = ScanlyResult.Success(Unit)
        override suspend fun deleteEmptyDocuments() = ScanlyResult.Success(0)
    }

    private object UnusedDocumentRepository : DocumentRepository by RecordingDocumentRepository()

    private object UnusedPageRepository : PageRepository {
        override fun observePages(documentId: String): Flow<List<ScanPage>> = flowOf(emptyList())
        override fun observePage(pageId: String): Flow<ScanPage?> = flowOf(null)
        override suspend fun prepareCapture(documentId: String): ScanlyResult<PageCaptureDraft> =
            error("Not used")
        override suspend fun prepareReplacementCapture(pageId: String): ScanlyResult<PageCaptureDraft> =
            error("Not used")
        override suspend fun finalizeCapture(draft: PageCaptureDraft): ScanlyResult<String> =
            error("Not used")
        override suspend fun movePage(pageId: String, targetIndex: Int): ScanlyResult<Unit> =
            error("Not used")
        override suspend fun deletePage(pageId: String): ScanlyResult<Unit> = error("Not used")
        override suspend fun updatePageEdits(
            pageId: String,
            cropQuad: DocumentCornerQuad,
            rotationDegrees: Int,
            filterPreset: PageFilterPreset,
            filterAdjustments: PageFilterAdjustments,
            applyFilterToAllPages: Boolean,
        ): ScanlyResult<Unit> = error("Not used")
    }

    private object UnusedGroupRepository : GroupRepository {
        override fun observeGroupsWithStats(): Flow<List<DocumentGroup>> = flowOf(emptyList())
        override fun observeRecentGroups(limit: Int): Flow<List<DocumentGroup>> = flowOf(emptyList())
        override fun observeGroupWithStats(groupId: String): Flow<DocumentGroup?> = flowOf(null)
        override fun observeGroupDocuments(groupId: String): Flow<List<ScanDocument>> =
            flowOf(emptyList())
        override suspend fun getAllGroupTitles(): List<String> = emptyList()
        override suspend fun suggestGroupTitle(format: GroupTitleFormat): String = "folder"
        override suspend fun createGroup(title: String): ScanlyResult<String> =
            ScanlyResult.Success("g1")
        override suspend fun renameGroup(groupId: String, title: String): ScanlyResult<Unit> =
            ScanlyResult.Success(Unit)
        override suspend fun deleteGroup(groupId: String): ScanlyResult<Unit> =
            ScanlyResult.Success(Unit)
        override suspend fun deleteEmptyGroups(): ScanlyResult<Int> = ScanlyResult.Success(0)
        override suspend fun setDocumentGroup(
            documentId: String,
            groupId: String?,
        ): ScanlyResult<Unit> = ScanlyResult.Success(Unit)
    }

    private object UnusedImageImportRepository : ImageImportRepository {
        override suspend fun importImages(
            documentId: String,
            imageUriStrings: List<String>,
            onProgress: suspend (ImportImagesProgress) -> Unit,
        ): ScanlyResult<Unit> = error("Not used")
    }
}
