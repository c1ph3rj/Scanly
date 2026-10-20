package `in`.c1ph3rj.scanly.domain.usecase.pdftools

import android.graphics.Bitmap
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.model.PdfCompressQuality
import `in`.c1ph3rj.scanly.domain.model.PdfDocumentInfo
import `in`.c1ph3rj.scanly.domain.model.PdfImageExport
import `in`.c1ph3rj.scanly.domain.model.PdfImageOptions
import `in`.c1ph3rj.scanly.domain.model.PdfSignSession
import `in`.c1ph3rj.scanly.domain.model.PdfToolSource
import `in`.c1ph3rj.scanly.domain.model.PdfWorkingCopy
import `in`.c1ph3rj.scanly.domain.model.SignMark
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
        restrictEditing: Boolean = false,
    ): ScanlyResult<ExportArtifact> =
        repository.setPassword(source, newPassword, currentPassword, restrictEditing)
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

class OpenPdfSignSessionUseCase @Inject constructor(
    private val repository: PdfToolkitRepository,
) {
    suspend operator fun invoke(
        source: PdfToolSource,
        password: String? = null,
    ): ScanlyResult<PdfSignSession> = repository.openSignSession(source, password)
}

class SignPdfUseCase @Inject constructor(
    private val repository: PdfToolkitRepository,
) {
    suspend operator fun invoke(
        session: PdfSignSession,
        marks: List<SignMark>,
    ): ScanlyResult<ExportArtifact> = repository.sign(session, marks)
}

class ClosePdfSignSessionUseCase @Inject constructor(
    private val repository: PdfToolkitRepository,
) {
    suspend operator fun invoke(session: PdfSignSession) = repository.closeSignSession(session)
}

class OpenPdfWorkingCopyUseCase @Inject constructor(
    private val repository: PdfToolkitRepository,
) {
    suspend operator fun invoke(
        source: PdfToolSource,
        password: String? = null,
    ): ScanlyResult<PdfWorkingCopy> = repository.openWorkingCopy(source, password)
}

class ClosePdfWorkingCopyUseCase @Inject constructor(
    private val repository: PdfToolkitRepository,
) {
    suspend operator fun invoke(copy: PdfWorkingCopy) = repository.closeWorkingCopy(copy)
}

class EstimatePdfPageImageSizeUseCase @Inject constructor(
    private val repository: PdfToolkitRepository,
) {
    suspend operator fun invoke(
        copy: PdfWorkingCopy,
        pageIndex: Int,
        options: PdfImageOptions,
    ): ScanlyResult<Long> = repository.estimatePageImageBytes(copy, pageIndex, options)
}

class ExportPdfPagesAsImagesUseCase @Inject constructor(
    private val repository: PdfToolkitRepository,
) {
    suspend operator fun invoke(
        copy: PdfWorkingCopy,
        pageIndexes: List<Int>,
        options: PdfImageOptions,
        baseName: String,
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ScanlyResult<PdfImageExport> =
        repository.exportPagesAsImages(copy, pageIndexes, options, baseName, onProgress)
}
