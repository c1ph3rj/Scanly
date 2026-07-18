package `in`.c1ph3rj.scanly.domain.repository

import android.graphics.Bitmap
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.model.PdfCompressQuality
import `in`.c1ph3rj.scanly.domain.model.PdfDocumentInfo
import `in`.c1ph3rj.scanly.domain.model.PdfToolSource
import `in`.c1ph3rj.scanly.domain.model.WatermarkOptions

interface PdfToolkitRepository {
    suspend fun inspect(
        source: PdfToolSource,
        password: String? = null,
    ): ScanlyResult<PdfDocumentInfo>

    suspend fun merge(
        sources: List<PdfToolSource>,
        passwords: Map<Int, String> = emptyMap(),
    ): ScanlyResult<ExportArtifact>

    suspend fun compress(
        source: PdfToolSource,
        quality: PdfCompressQuality,
        password: String? = null,
    ): ScanlyResult<ExportArtifact>

    suspend fun setPassword(
        source: PdfToolSource,
        newPassword: String,
        currentPassword: String? = null,
    ): ScanlyResult<ExportArtifact>

    suspend fun removePassword(
        source: PdfToolSource,
        currentPassword: String,
    ): ScanlyResult<ExportArtifact>

    suspend fun watermark(
        source: PdfToolSource,
        options: WatermarkOptions,
        password: String? = null,
    ): ScanlyResult<ExportArtifact>

    /** Renders page one with the production watermark engine for an exact editor preview. */
    suspend fun renderWatermarkPreview(
        source: PdfToolSource,
        options: WatermarkOptions,
        password: String? = null,
        maxWidth: Int = 1080,
    ): ScanlyResult<Bitmap>

    suspend fun renderPage(
        source: PdfToolSource,
        pageIndex: Int,
        password: String? = null,
        maxWidth: Int = 1080,
    ): ScanlyResult<Bitmap>

    /** Copies a tool result into a shareable path set and returns absolute paths. */
    suspend fun prepareShare(artifact: ExportArtifact): ScanlyResult<List<String>>
}
