package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.model.GroupTitleFormat
import `in`.c1ph3rj.scanly.domain.model.ImportImagesProgress
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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aggregate facade for document-detail flows: observe, mutate pages, export/share,
 * import, and folder moves. Prefer this over injecting a dozen pass-through use cases.
 */
@Singleton
class DocumentWorkspace @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val pageRepository: PageRepository,
    private val groupRepository: GroupRepository,
    private val documentExportRepository: DocumentExportRepository,
    private val exportStorageRepository: ExportStorageRepository,
    private val imageImportRepository: ImageImportRepository,
) {
    fun observeDocument(documentId: String): Flow<ScanDocument?> =
        documentRepository.observeDocument(documentId)

    fun observePages(documentId: String): Flow<List<ScanPage>> =
        pageRepository.observePages(documentId)

    fun observeGroups(): Flow<List<DocumentGroup>> =
        groupRepository.observeGroupsWithStats()

    suspend fun movePage(pageId: String, targetIndex: Int): ScanlyResult<Unit> =
        pageRepository.movePage(pageId, targetIndex)

    suspend fun deletePage(pageId: String): ScanlyResult<Unit> =
        pageRepository.deletePage(pageId)

    suspend fun renameDocument(documentId: String, title: String): ScanlyResult<Unit> =
        documentRepository.renameDocument(documentId, title)

    suspend fun deleteDocument(documentId: String): ScanlyResult<Unit> =
        documentRepository.deleteDocument(documentId)

    suspend fun setDocumentGroup(documentId: String, groupId: String?): ScanlyResult<Unit> =
        groupRepository.setDocumentGroup(documentId, groupId)

    suspend fun createGroup(title: String): ScanlyResult<String> =
        groupRepository.createGroup(title)

    suspend fun suggestGroupTitle(format: GroupTitleFormat): String =
        groupRepository.suggestGroupTitle(format)

    suspend fun importImages(
        documentId: String,
        imageUriStrings: List<String>,
        onProgress: suspend (ImportImagesProgress) -> Unit = {},
    ): ScanlyResult<Unit> = imageImportRepository.importImages(
        documentId = documentId,
        imageUriStrings = imageUriStrings,
        onProgress = onProgress,
    )

    suspend fun exportPdf(
        documentId: String,
        options: PdfExportOptions,
    ): ScanlyResult<ExportArtifact> =
        documentExportRepository.exportPdf(documentId, options)

    suspend fun exportPdfAndSave(
        documentId: String,
        options: PdfExportOptions,
    ): ScanlyResult<SavedExport> = when (
        val generated = documentExportRepository.exportPdf(documentId, options)
    ) {
        is ScanlyResult.Success -> exportStorageRepository.saveExport(generated.value)
        is ScanlyResult.Failure -> generated
    }

    suspend fun preparePdfShare(
        documentId: String,
        options: PdfExportOptions,
    ): ScanlyResult<ShareArtifact> =
        documentExportRepository.preparePdfShare(documentId, options)

    suspend fun exportImageArchiveAndSave(documentId: String): ScanlyResult<SavedExport> = when (
        val generated = documentExportRepository.exportImageArchive(documentId)
    ) {
        is ScanlyResult.Success -> exportStorageRepository.saveExport(generated.value)
        is ScanlyResult.Failure -> generated
    }

    suspend fun prepareImageShare(documentId: String): ScanlyResult<ShareArtifact> =
        documentExportRepository.prepareImageShare(documentId)
}
