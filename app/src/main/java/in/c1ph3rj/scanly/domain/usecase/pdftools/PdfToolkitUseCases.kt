package `in`.c1ph3rj.scanly.domain.usecase.pdftools

import android.graphics.Bitmap
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.model.PdfCompressQuality
import `in`.c1ph3rj.scanly.domain.model.PdfDocumentInfo
import `in`.c1ph3rj.scanly.domain.model.PdfToolSource
import `in`.c1ph3rj.scanly.domain.model.WatermarkOptions
import `in`.c1ph3rj.scanly.domain.repository.PdfToolkitRepository
import javax.inject.Inject

class InspectPdfUseCase @Inject constructor(
    private val repository: PdfToolkitRepository,
) {
    suspend operator fun invoke(
        source: PdfToolSource,
        password: String? = null,
    ): ScanlyResult<PdfDocumentInfo> = repository.inspect(source, password)
}

class MergePdfsUseCase @Inject constructor(
    private val repository: PdfToolkitRepository,
) {
    suspend operator fun invoke(
        sources: List<PdfToolSource>,
        passwords: Map<Int, String> = emptyMap(),
    ): ScanlyResult<ExportArtifact> = repository.merge(sources, passwords)
}

class CompressPdfUseCase @Inject constructor(
    private val repository: PdfToolkitRepository,
) {
    suspend operator fun invoke(
        source: PdfToolSource,
        quality: PdfCompressQuality,
        password: String? = null,
    ): ScanlyResult<ExportArtifact> = repository.compress(source, quality, password)
}

class SetPdfPasswordUseCase @Inject constructor(
    private val repository: PdfToolkitRepository,
) {
    suspend operator fun invoke(
        source: PdfToolSource,
        newPassword: String,
        currentPassword: String? = null,
    ): ScanlyResult<ExportArtifact> = repository.setPassword(source, newPassword, currentPassword)
}

class RemovePdfPasswordUseCase @Inject constructor(
    private val repository: PdfToolkitRepository,
) {
    suspend operator fun invoke(
        source: PdfToolSource,
        currentPassword: String,
    ): ScanlyResult<ExportArtifact> = repository.removePassword(source, currentPassword)
}

class WatermarkPdfUseCase @Inject constructor(
    private val repository: PdfToolkitRepository,
) {
    suspend operator fun invoke(
        source: PdfToolSource,
        options: WatermarkOptions,
        password: String? = null,
    ): ScanlyResult<ExportArtifact> = repository.watermark(source, options, password)
}

class RenderWatermarkPreviewUseCase @Inject constructor(
    private val repository: PdfToolkitRepository,
) {
    suspend operator fun invoke(
        source: PdfToolSource,
        options: WatermarkOptions,
        password: String? = null,
        maxWidth: Int = 1080,
    ): ScanlyResult<Bitmap> =
        repository.renderWatermarkPreview(source, options, password, maxWidth)
}

class RenderPdfPageUseCase @Inject constructor(
    private val repository: PdfToolkitRepository,
) {
    suspend operator fun invoke(
        source: PdfToolSource,
        pageIndex: Int,
        password: String? = null,
        maxWidth: Int = 1080,
    ): ScanlyResult<Bitmap> = repository.renderPage(source, pageIndex, password, maxWidth)
}

class PreparePdfToolShareUseCase @Inject constructor(
    private val repository: PdfToolkitRepository,
) {
    suspend operator fun invoke(artifact: ExportArtifact): ScanlyResult<List<String>> =
        repository.prepareShare(artifact)
}
