package `in`.c1ph3rj.scanly.testing

import android.graphics.Bitmap
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.model.PdfCompressQuality
import `in`.c1ph3rj.scanly.domain.model.PdfDocumentInfo
import `in`.c1ph3rj.scanly.domain.model.PdfToolSource
import `in`.c1ph3rj.scanly.domain.model.WatermarkOptions
import `in`.c1ph3rj.scanly.domain.repository.PdfToolkitRepository
import java.io.File

class InMemoryPdfToolkitRepository(
    private val outputDirectory: File,
) : PdfToolkitRepository {
    var lastInspectedSource: PdfToolSource? = null
        private set
    var lastInspectedPassword: String? = null
        private set
    var lastMergeSources: List<PdfToolSource> = emptyList()
        private set
    var lastCompressQuality: PdfCompressQuality? = null
        private set
    var lastSetPassword: String? = null
        private set
    var lastRemovedPassword: String? = null
        private set
    var lastWatermarkOptions: WatermarkOptions? = null
        private set

    override suspend fun inspect(
        source: PdfToolSource,
        password: String?,
    ): ScanlyResult<PdfDocumentInfo> = runLibraryResult("Could not inspect PDF.") {
        lastInspectedSource = source
        lastInspectedPassword = password
        PdfDocumentInfo(
            pageCount = 2,
            isEncrypted = password != null,
            fileSizeBytes = 4_096L,
            displayName = source.displayName(),
        )
    }

    override suspend fun merge(
        sources: List<PdfToolSource>,
        passwords: Map<Int, String>,
    ): ScanlyResult<ExportArtifact> = runLibraryResult("Could not merge PDFs.") {
        require(sources.size >= 2) { "Select at least two PDFs to merge." }
        lastMergeSources = sources
        writeArtifact("merged.pdf", "application/pdf", "MERGED ${sources.size} files")
    }

    override suspend fun compress(
        source: PdfToolSource,
        quality: PdfCompressQuality,
        password: String?,
    ): ScanlyResult<ExportArtifact> = runLibraryResult("Could not compress PDF.") {
        lastCompressQuality = quality
        writeArtifact(
            "compressed.pdf",
            "application/pdf",
            "COMPRESS ${source.displayName()} quality=${quality.name}",
        )
    }

    override suspend fun setPassword(
        source: PdfToolSource,
        newPassword: String,
        currentPassword: String?,
    ): ScanlyResult<ExportArtifact> = runLibraryResult("Could not protect PDF.") {
        lastSetPassword = newPassword
        writeArtifact("protected.pdf", "application/pdf", "PASSWORD ${newPassword.length}")
    }

    override suspend fun removePassword(
        source: PdfToolSource,
        currentPassword: String,
    ): ScanlyResult<ExportArtifact> = runLibraryResult("Could not remove PDF password.") {
        lastRemovedPassword = currentPassword
        writeArtifact("unlocked.pdf", "application/pdf", "UNLOCKED")
    }

    override suspend fun watermark(
        source: PdfToolSource,
        options: WatermarkOptions,
        password: String?,
    ): ScanlyResult<ExportArtifact> = runLibraryResult("Could not watermark PDF.") {
        lastWatermarkOptions = options
        writeArtifact(
            "watermarked.pdf",
            "application/pdf",
            "WATERMARK ${options.text} layout=${options.layout} range=${options.pageRange}",
        )
    }

    override suspend fun renderWatermarkPreview(
        source: PdfToolSource,
        options: WatermarkOptions,
        password: String?,
        maxWidth: Int,
    ): ScanlyResult<Bitmap> = runLibraryResult("Could not render watermark preview.") {
        error("Bitmap preview is unavailable on the JVM.")
    }

    override suspend fun renderPage(
        source: PdfToolSource,
        pageIndex: Int,
        password: String?,
        maxWidth: Int,
    ): ScanlyResult<Bitmap> = runLibraryResult("Could not render PDF page.") {
        error("Bitmap preview is unavailable on the JVM.")
    }

    override suspend fun prepareShare(artifact: ExportArtifact): ScanlyResult<List<String>> =
        runLibraryResult("Could not prepare share.") {
            listOf(artifact.filePath)
        }

    private fun writeArtifact(fileName: String, mimeType: String, body: String): ExportArtifact {
        outputDirectory.mkdirs()
        val file = File(outputDirectory, fileName)
        file.writeText(body)
        check(file.length() > 0L) { "PDF tool artifact is empty." }
        return ExportArtifact(file.absolutePath, fileName, mimeType)
    }

    private fun PdfToolSource.displayName(): String = when (this) {
        is PdfToolSource.DeviceUri -> displayName
        is PdfToolSource.LibraryDocument -> title
        is PdfToolSource.AppFile -> displayName
    }
}
