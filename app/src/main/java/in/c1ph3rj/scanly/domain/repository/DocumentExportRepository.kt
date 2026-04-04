package `in`.c1ph3rj.scanly.domain.repository

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.model.PdfExportOptions
import `in`.c1ph3rj.scanly.domain.model.ShareArtifact

interface DocumentExportRepository {
    suspend fun exportPdf(
        documentId: String,
        options: PdfExportOptions,
    ): ScanlyResult<ExportArtifact>

    suspend fun preparePdfShare(
        documentId: String,
        options: PdfExportOptions,
    ): ScanlyResult<ShareArtifact>

    suspend fun exportImageArchive(documentId: String): ScanlyResult<ExportArtifact>

    suspend fun prepareImageShare(documentId: String): ScanlyResult<ShareArtifact>
}
