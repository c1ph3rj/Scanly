package `in`.c1ph3rj.scanly.testing

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.model.PdfExportOptions
import `in`.c1ph3rj.scanly.domain.model.ShareArtifact
import `in`.c1ph3rj.scanly.domain.repository.DocumentExportRepository
import java.io.File

class InMemoryDocumentExportRepository(
    private val library: InMemoryScanlyLibrary,
    private val exportDirectory: File,
) : DocumentExportRepository {
    val recordedRequests = mutableListOf<RecordedExport>()

    override suspend fun exportPdf(
        documentId: String,
        options: PdfExportOptions,
    ): ScanlyResult<ExportArtifact> = runLibraryResult("Could not export PDF.") {
        val document = library.document(documentId) ?: error("Document not found.")
        recordedRequests += RecordedExport.DocumentPdf(documentId, options)
        writeArtifact(
            fileName = "${safeStem(document.title)}.pdf",
            mimeType = "application/pdf",
            body = pdfBody(documentId, options, pageCount = document.pageCount),
        )
    }

    override suspend fun preparePdfShare(
        documentId: String,
        options: PdfExportOptions,
    ): ScanlyResult<ShareArtifact> = runLibraryResult("Could not prepare PDF share.") {
        val artifact = exportPdf(documentId, options).requireSuccess()
        ShareArtifact(
            mimeType = artifact.mimeType,
            title = artifact.fileName,
            filePaths = listOf(artifact.filePath),
        )
    }

    override suspend fun exportImageArchive(documentId: String): ScanlyResult<ExportArtifact> =
        runLibraryResult("Could not export images.") {
            val document = library.document(documentId) ?: error("Document not found.")
            recordedRequests += RecordedExport.DocumentImages(documentId)
            writeArtifact(
                fileName = "${safeStem(document.title)}.zip",
                mimeType = "application/zip",
                body = "ZIP pages=${document.pageCount} document=$documentId",
            )
        }

    override suspend fun prepareImageShare(documentId: String): ScanlyResult<ShareArtifact> =
        runLibraryResult("Could not prepare image share.") {
            val artifact = exportImageArchive(documentId).requireSuccess()
            ShareArtifact(
                mimeType = artifact.mimeType,
                title = artifact.fileName,
                filePaths = listOf(artifact.filePath),
            )
        }

    override suspend fun exportGroupAsSinglePdf(
        groupId: String,
        options: PdfExportOptions,
        onProgress: (current: Int, total: Int) -> Unit,
    ): ScanlyResult<ExportArtifact> = runLibraryResult("Could not export group PDF.") {
        val group = library.group(groupId) ?: error("Group not found.")
        val members = library.documents.value.filter { it.groupId == groupId }
        val totalPages = members.sumOf { it.pageCount }.coerceAtLeast(1)
        onProgress(totalPages, totalPages)
        recordedRequests += RecordedExport.GroupPdf(groupId, options)
        writeArtifact(
            fileName = "${safeStem(group.title)}-merged.pdf",
            mimeType = "application/pdf",
            body = pdfBody(groupId, options, pageCount = members.sumOf { it.pageCount }),
        )
    }

    override suspend fun exportGroupAsZippedPdfs(
        groupId: String,
        options: PdfExportOptions,
        onProgress: (currentDoc: Int, totalDocs: Int) -> Unit,
    ): ScanlyResult<ExportArtifact> = runLibraryResult("Could not export group ZIP.") {
        val group = library.group(groupId) ?: error("Group not found.")
        val members = library.documents.value.filter { it.groupId == groupId }
        val totalDocs = members.size.coerceAtLeast(1)
        onProgress(totalDocs, totalDocs)
        recordedRequests += RecordedExport.GroupZippedPdfs(groupId, options)
        writeArtifact(
            fileName = "${safeStem(group.title)}-pdfs.zip",
            mimeType = "application/zip",
            body = "ZIP pdfs=${members.size} group=$groupId password=${options.password != null}",
        )
    }

    override suspend fun prepareGroupSinglePdfShare(
        groupId: String,
        options: PdfExportOptions,
        onProgress: (current: Int, total: Int) -> Unit,
    ): ScanlyResult<ShareArtifact> = runLibraryResult("Could not prepare group PDF share.") {
        val artifact = exportGroupAsSinglePdf(groupId, options, onProgress).requireSuccess()
        ShareArtifact(artifact.mimeType, artifact.fileName, listOf(artifact.filePath))
    }

    override suspend fun prepareGroupZippedPdfsShare(
        groupId: String,
        options: PdfExportOptions,
        onProgress: (currentDoc: Int, totalDocs: Int) -> Unit,
    ): ScanlyResult<ShareArtifact> = runLibraryResult("Could not prepare group ZIP share.") {
        val artifact = exportGroupAsZippedPdfs(groupId, options, onProgress).requireSuccess()
        ShareArtifact(artifact.mimeType, artifact.fileName, listOf(artifact.filePath))
    }

    private fun writeArtifact(fileName: String, mimeType: String, body: String): ExportArtifact {
        exportDirectory.mkdirs()
        val file = File(exportDirectory, fileName)
        file.writeText(body)
        check(file.exists() && file.length() > 0L) { "Export artifact is empty." }
        return ExportArtifact(
            filePath = file.absolutePath,
            fileName = fileName,
            mimeType = mimeType,
        )
    }

    private fun pdfBody(id: String, options: PdfExportOptions, pageCount: Int): String =
        "PDF id=$id pages=$pageCount size=${options.pageSize} " +
            "orientation=${options.orientation} numbers=${options.pageNumber} " +
            "password=${options.password != null}"

    private fun safeStem(title: String): String =
        title.lowercase().replace("[^a-z0-9]+".toRegex(), "_").trim('_').ifBlank { "export" }
}

sealed interface RecordedExport {
    data class DocumentPdf(val documentId: String, val options: PdfExportOptions) : RecordedExport
    data class DocumentImages(val documentId: String) : RecordedExport
    data class GroupPdf(val groupId: String, val options: PdfExportOptions) : RecordedExport
    data class GroupZippedPdfs(val groupId: String, val options: PdfExportOptions) : RecordedExport
}
