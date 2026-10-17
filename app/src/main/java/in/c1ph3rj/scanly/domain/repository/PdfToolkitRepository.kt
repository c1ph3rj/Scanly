package `in`.c1ph3rj.scanly.domain.repository

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

    /**
     * Encrypts with AES-256. When [restrictEditing] is true the owner permissions block
     * editing, annotating, form filling and copying text; opening and printing stay allowed.
     */
    suspend fun setPassword(
        source: PdfToolSource,
        newPassword: String,
        currentPassword: String? = null,
        restrictEditing: Boolean = false,
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

    /** Resolves and decrypts [source] once into a local copy for the sign editor. */
    suspend fun openSignSession(
        source: PdfToolSource,
        password: String? = null,
    ): ScanlyResult<PdfSignSession>

    /** Flattens [marks] into a new PDF built from the session copy. */
    suspend fun sign(
        session: PdfSignSession,
        marks: List<SignMark>,
    ): ScanlyResult<ExportArtifact>

    /** Deletes the session's local copy. */
    suspend fun closeSignSession(session: PdfSignSession)

    /** Resolves and decrypts [source] once into a local copy that later calls read in place. */
    suspend fun openWorkingCopy(
        source: PdfToolSource,
        password: String? = null,
    ): ScanlyResult<PdfWorkingCopy>

    /** Deletes the working copy's local file. */
    suspend fun closeWorkingCopy(copy: PdfWorkingCopy)

    /** Encoded size of one page at [options], used for the "~220 KB each" estimate. */
    suspend fun estimatePageImageBytes(
        copy: PdfWorkingCopy,
        pageIndex: Int,
        options: PdfImageOptions,
    ): ScanlyResult<Long>

    /**
     * Renders [pageIndexes] to images and saves each to the gallery (Pictures/Scanly) as
     * "[baseName] – page N". [onProgress] reports pages finished so far.
     */
    suspend fun exportPagesAsImages(
        copy: PdfWorkingCopy,
        pageIndexes: List<Int>,
        options: PdfImageOptions,
        baseName: String,
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ScanlyResult<PdfImageExport>

    /** Copies a tool result into a shareable path set and returns absolute paths. */
    suspend fun prepareShare(artifact: ExportArtifact): ScanlyResult<List<String>>
}
