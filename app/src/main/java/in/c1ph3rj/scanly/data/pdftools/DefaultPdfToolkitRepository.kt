package `in`.c1ph3rj.scanly.data.pdftools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.model.PdfCompressQuality
import `in`.c1ph3rj.scanly.domain.model.PdfDocumentInfo
import `in`.c1ph3rj.scanly.domain.model.PdfExportOptions
import `in`.c1ph3rj.scanly.domain.model.PdfToolSource
import `in`.c1ph3rj.scanly.domain.model.WatermarkOptions
import `in`.c1ph3rj.scanly.domain.model.WatermarkPageRange
import `in`.c1ph3rj.scanly.domain.repository.DocumentExportRepository
import `in`.c1ph3rj.scanly.domain.repository.PdfToolkitRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultPdfToolkitRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentExportRepository: DocumentExportRepository,
    private val dispatchers: ScanlyDispatchers,
) : PdfToolkitRepository {

    /**
     * Scanly document exports share an output folder. Reader page rendering can happen in
     * parallel, so serialize the export-and-copy step before handing each caller its own file.
     */
    private val libraryExportMutex = Mutex()

    private val previewBaseMutex = Mutex()
    private var previewBase: PreviewBase? = null

    private data class PreviewBase(val key: String, val file: File, val createdAt: Long)

    override suspend fun inspect(
        source: PdfToolSource,
        password: String?,
    ): ScanlyResult<PdfDocumentInfo> = withContext(dispatchers.io) {
        runCatching {
            ensurePdfBox()
            resolveLocalFile(source).useFile { local ->
                openDocument(local, password).use { document ->
                    PdfDocumentInfo(
                        pageCount = document.numberOfPages,
                        isEncrypted = document.isEncrypted,
                        fileSizeBytes = local.length().takeIf { it > 0L },
                        displayName = source.displayLabel(),
                    )
                }
            }
        }.toScanlyResult("Could not open PDF.")
    }

    override suspend fun merge(
        sources: List<PdfToolSource>,
        passwords: Map<Int, String>,
    ): ScanlyResult<ExportArtifact> = withContext(dispatchers.io) {
        runCatching {
            require(sources.size >= 2) { "Select at least two PDFs to merge." }
            ensurePdfBox()
            val output = newOutputFile("merged")
            val resolvedSources = sources.map { resolveLocalFile(it) }
            try {
                val sourceDocuments = mutableListOf<PDDocument>()
                try {
                    resolvedSources.forEachIndexed { index, resolved ->
                        sourceDocuments += openDocument(resolved.file, passwords[index])
                    }
                    PDDocument().use { merged ->
                        sourceDocuments.forEach { document ->
                            for (page in document.pages) {
                                merged.importPage(page)
                            }
                        }
                        // importPage retains page streams until save, so source documents must
                        // remain open for the entire write.
                        merged.save(output)
                    }
                } finally {
                    sourceDocuments.asReversed().forEach(PDDocument::close)
                }
            } finally {
                resolvedSources.forEach(ResolvedFile::cleanup)
            }
            artifact(output)
        }.toScanlyResult("Could not merge PDFs.")
    }

    override suspend fun compress(
        source: PdfToolSource,
        quality: PdfCompressQuality,
        password: String?,
    ): ScanlyResult<ExportArtifact> = withContext(dispatchers.io) {
        runCatching {
            ensurePdfBox()
            resolveLocalFile(source).useFile { local ->
                val renderFile = unlockForRendererIfNeeded(local, password)
                try {
                    val output = newOutputFile("compressed")
                    ParcelFileDescriptor.open(renderFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                        PdfRenderer(pfd).use { renderer ->
                            require(renderer.pageCount > 0) { "PDF has no pages." }
                            PDDocument().use { document ->
                                for (index in 0 until renderer.pageCount) {
                                    renderer.openPage(index).use { page ->
                                        val pageW = page.width.toFloat().coerceAtLeast(1f)
                                        val pageH = page.height.toFloat().coerceAtLeast(1f)
                                        val scale = compressScale(pageW, pageH, quality.maxDimension)
                                        val bmpW = (pageW * scale).toInt().coerceAtLeast(1)
                                        val bmpH = (pageH * scale).toInt().coerceAtLeast(1)
                                        val pixelCount = bmpW.toLong() * bmpH
                                        require(pixelCount <= MaxCompressPagePixels) {
                                            "Page ${index + 1} is too large to compress on this device. Try Smallest quality."
                                        }
                                        val bitmap = Bitmap.createBitmap(
                                            bmpW,
                                            bmpH,
                                            Bitmap.Config.ARGB_8888,
                                        )
                                        try {
                                            bitmap.eraseColor(Color.WHITE)
                                            page.render(
                                                bitmap,
                                                null,
                                                null,
                                                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                                            )
                                            val jpegBytes = bitmap.toJpegBytes(quality.jpegQuality)
                                            val pdPage = com.tom_roush.pdfbox.pdmodel.PDPage(
                                                com.tom_roush.pdfbox.pdmodel.common.PDRectangle(
                                                    pageW,
                                                    pageH,
                                                ),
                                            )
                                            document.addPage(pdPage)
                                            val image = JPEGFactory.createFromByteArray(
                                                document,
                                                jpegBytes,
                                            )
                                            PDPageContentStream(document, pdPage).use { stream ->
                                                stream.drawImage(image, 0f, 0f, pageW, pageH)
                                            }
                                        } finally {
                                            bitmap.recycle()
                                        }
                                    }
                                }
                                document.save(output)
                            }
                        }
                    }
                    artifact(output)
                } catch (oom: OutOfMemoryError) {
                    throw IllegalStateException(
                        "Not enough memory to compress this PDF. Try Smallest quality or a shorter file.",
                        oom,
                    )
                } finally {
                    if (renderFile != local) {
                        renderFile.delete()
                    }
                }
            }
        }.toScanlyResult("Could not compress PDF.")
    }

    override suspend fun setPassword(
        source: PdfToolSource,
        newPassword: String,
        currentPassword: String?,
        restrictEditing: Boolean,
    ): ScanlyResult<ExportArtifact> = withContext(dispatchers.io) {
        runCatching {
            require(newPassword.length in 4..64) { "Password must be 4–64 characters." }
            ensurePdfBox()
            resolveLocalFile(source).useFile { local ->
                val output = newOutputFile("protected")
                openDocument(local, currentPassword).use { document ->
                    if (document.isEncrypted) {
                        document.setAllSecurityToBeRemoved(true)
                    }
                    val permissions = AccessPermission().apply {
                        if (restrictEditing) {
                            setCanModify(false)
                            setCanModifyAnnotations(false)
                            setCanFillInForm(false)
                            setCanAssembleDocument(false)
                            setCanExtractContent(false)
                            // Accessibility extraction stays on so screen readers still work.
                            setCanExtractForAccessibility(true)
                            setCanPrint(true)
                        }
                    }
                    val policy = StandardProtectionPolicy(
                        UUID.randomUUID().toString(),
                        newPassword,
                        permissions,
                    ).apply {
                        encryptionKeyLength = 256
                        setPreferAES(true)
                    }
                    document.protect(policy)
                    document.save(output)
                }
                artifact(output)
            }
        }.toScanlyResult("Could not password-protect PDF.")
    }

    override suspend fun removePassword(
        source: PdfToolSource,
        currentPassword: String,
    ): ScanlyResult<ExportArtifact> = withContext(dispatchers.io) {
        runCatching {
            require(currentPassword.isNotBlank()) { "Enter the current PDF password." }
            ensurePdfBox()
            resolveLocalFile(source).useFile { local ->
                val output = newOutputFile("unlocked")
                openDocument(local, currentPassword).use { document ->
                    document.setAllSecurityToBeRemoved(true)
                    document.save(output)
                }
                artifact(output)
            }
        }.toScanlyResult("Could not remove PDF password.")
    }

    override suspend fun watermark(
        source: PdfToolSource,
        options: WatermarkOptions,
        password: String?,
    ): ScanlyResult<ExportArtifact> = withContext(dispatchers.io) {
        runCatching {
            require(options.text.isNotBlank()) { "Enter watermark text." }
            ensurePdfBox()
            resolveLocalFile(source).useFile { local ->
                val output = newOutputFile("watermarked")
                openDocument(local, password).use { document ->
                    require(document.numberOfPages > 0) { "This PDF has no pages." }
                    if (document.isEncrypted) {
                        document.setAllSecurityToBeRemoved(true)
                    }
                    val stamper = PdfWatermarkStamper.create(document, options)
                    document.pages.forEachIndexed { pageIndex, page ->
                        if (options.pageRange == WatermarkPageRange.FIRST_PAGE && pageIndex != 0) {
                            return@forEachIndexed
                        }
                        stamper.stamp(document, page)
                    }
                    document.save(output)
                }
                artifact(output)
            }
        }.toScanlyResult("Could not add watermark.")
    }

    override suspend fun renderWatermarkPreview(
        source: PdfToolSource,
        options: WatermarkOptions,
        password: String?,
        maxWidth: Int,
    ): ScanlyResult<Bitmap> = withContext(dispatchers.io) {
        runCatching {
            require(options.text.isNotBlank()) { "Enter watermark text." }
            require(maxWidth > 0) { "Preview size is invalid." }
            ensurePdfBox()
            val base = watermarkPreviewBase(source, password)
            val previewFile = File(toolsDir(), "watermark_preview_${UUID.randomUUID()}.pdf")
            try {
                PDDocument.load(base).use { document ->
                    PdfWatermarkStamper.create(document, options).stamp(document, document.getPage(0))
                    document.save(previewFile)
                }
                renderWithPdfRenderer(previewFile, pageIndex = 0, maxWidth = maxWidth)
            } finally {
                previewFile.delete()
            }
        }.toScanlyResult("Could not update watermark preview.")
    }

    /**
     * First page of [source], decrypted, saved once and reused while the editor is open so each
     * preview refresh loads a tiny one-page file instead of re-exporting the whole document.
     */
    private suspend fun watermarkPreviewBase(source: PdfToolSource, password: String?): File =
        previewBaseMutex.withLock {
            val key = "${source.cacheKey()}|${password.orEmpty().hashCode()}"
            val now = System.currentTimeMillis()
            previewBase?.let { cached ->
                if (cached.key == key && cached.file.isFile && now - cached.createdAt < PreviewBaseTtlMs) {
                    return@withLock cached.file
                }
                cached.file.delete()
            }
            previewBase = null
            val file = File(toolsDir(), "watermark_base_${UUID.randomUUID()}.pdf")
            resolveLocalFile(source).useFile { local ->
                openDocument(local, password).use { document ->
                    require(document.numberOfPages > 0) { "This PDF has no pages." }
                    if (document.isEncrypted) {
                        document.setAllSecurityToBeRemoved(true)
                    }
                    // Removing pages (rather than importing page 1 into a new document) keeps
                    // inherited /Rotate, /CropBox and resources intact.
                    while (document.numberOfPages > 1) {
                        document.removePage(document.numberOfPages - 1)
                    }
                    document.save(file)
                }
            }
            previewBase = PreviewBase(key = key, file = file, createdAt = now)
            file
        }

    override suspend fun renderPage(
        source: PdfToolSource,
        pageIndex: Int,
        password: String?,
        maxWidth: Int,
    ): ScanlyResult<Bitmap> = withContext(dispatchers.io) {
        runCatching {
            resolveLocalFile(source).useFile { local ->
                val renderFile = if (password.isNullOrBlank()) {
                    local
                } else {
                    // Decrypt to temp for PdfRenderer when password is required.
                    ensurePdfBox()
                    val unlocked = File(toolsDir(), "render_${UUID.randomUUID()}.pdf")
                    openDocument(local, password).use { document ->
                        document.setAllSecurityToBeRemoved(true)
                        document.save(unlocked)
                    }
                    unlocked
                }
                try {
                    renderWithPdfRenderer(renderFile, pageIndex, maxWidth)
                } finally {
                    if (renderFile != local) {
                        renderFile.delete()
                    }
                }
            }
        }.toScanlyResult("Could not render PDF page.")
    }

    override suspend fun prepareShare(artifact: ExportArtifact): ScanlyResult<List<String>> =
        withContext(dispatchers.io) {
            runCatching {
                val file = File(artifact.filePath)
                require(file.exists()) { "Export file is missing." }
                listOf(file.absolutePath)
            }.toScanlyResult("Could not prepare share.")
        }

    private suspend fun resolveLocalFile(source: PdfToolSource): ResolvedFile {
        return when (source) {
            is PdfToolSource.DeviceUri -> {
                val uri = Uri.parse(source.uriString)
                val target = File(toolsDir(), "src_${UUID.randomUUID()}.pdf")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                } ?: error("Could not read selected PDF.")
                ResolvedFile(file = target, deleteOnClose = true)
            }
            is PdfToolSource.LibraryDocument -> {
                libraryExportMutex.withLock {
                    when (
                        val exported = documentExportRepository.exportPdf(
                            documentId = source.documentId,
                            options = PdfExportOptions(),
                        )
                    ) {
                        is ScanlyResult.Success -> {
                            val exportedFile = File(exported.value.filePath)
                            require(exportedFile.isFile && exportedFile.length() > 0L) {
                                "The Scanly document could not be prepared as a PDF."
                            }
                            val readerCopy = File(toolsDir(), "library_${UUID.randomUUID()}.pdf")
                            exportedFile.copyTo(readerCopy, overwrite = true)
                            ResolvedFile(file = readerCopy, deleteOnClose = true)
                        }
                        is ScanlyResult.Failure -> error(exported.error.message)
                    }
                }
            }
            is PdfToolSource.AppFile -> {
                val file = File(source.filePath).canonicalFile
                val allowedRoots = listOfNotNull(
                    context.cacheDir,
                    context.filesDir,
                    context.externalCacheDir,
                ).map(File::getCanonicalFile)
                require(allowedRoots.any { root -> file.path.startsWith(root.path + File.separator) }) {
                    "This PDF is not stored in Scanly."
                }
                require(file.isFile && file.length() > 0L) { "The preview PDF is missing." }
                ResolvedFile(file = file, deleteOnClose = false)
            }
        }
    }

    private fun openDocument(file: File, password: String?): PDDocument {
        return try {
            if (password.isNullOrBlank()) {
                PDDocument.load(file)
            } else {
                PDDocument.load(file, password)
            }
        } catch (error: InvalidPasswordException) {
            throw IllegalArgumentException("Incorrect PDF password.", error)
        } catch (error: Exception) {
            // Retry with empty password string for some encrypted files.
            if (!password.isNullOrBlank()) throw error
            try {
                PDDocument.load(file, "")
            } catch (encrypted: Exception) {
                if (encrypted is InvalidPasswordException ||
                    encrypted.message?.contains("password", ignoreCase = true) == true
                ) {
                    throw IllegalArgumentException("This PDF is password protected.")
                }
                throw error
            }
        }
    }

    private fun unlockForRendererIfNeeded(file: File, password: String?): File {
        if (!password.isNullOrBlank()) {
            ensurePdfBox()
            val unlocked = File(toolsDir(), "unlock_${UUID.randomUUID()}.pdf")
            openDocument(file, password).use { document ->
                document.setAllSecurityToBeRemoved(true)
                document.save(unlocked)
            }
            return unlocked
        }
        val canOpen = runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { it.pageCount }
            }
            true
        }.getOrDefault(false)
        if (canOpen) return file

        // Encrypted without password or needs re-save for PdfRenderer.
        ensurePdfBox()
        return try {
            val unlocked = File(toolsDir(), "unlock_${UUID.randomUUID()}.pdf")
            openDocument(file, null).use { document ->
                if (document.isEncrypted) {
                    document.setAllSecurityToBeRemoved(true)
                }
                document.save(unlocked)
            }
            unlocked
        } catch (error: Exception) {
            if (error.message?.contains("password", ignoreCase = true) == true ||
                error is InvalidPasswordException
            ) {
                throw IllegalArgumentException("This PDF is password protected.")
            }
            throw error
        }
    }

    private fun compressScale(pageW: Float, pageH: Float, maxDimension: Int): Float {
        val longEdge = maxOf(pageW, pageH).coerceAtLeast(1f)
        // PDF page size is in points; scale so the long edge reaches maxDimension pixels.
        return (maxDimension / longEdge).coerceIn(0.35f, 6f)
    }

    private fun Bitmap.toJpegBytes(quality: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        val ok = compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), stream)
        require(ok && stream.size() > 0) { "Failed to encode page image." }
        return stream.toByteArray()
    }

    private fun renderWithPdfRenderer(file: File, pageIndex: Int, maxWidth: Int): Bitmap {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                require(pageIndex in 0 until renderer.pageCount) { "Page is out of range." }
                return renderWithPdfRenderer(renderer, pageIndex, maxWidth)
            }
        }
    }

    private fun renderWithPdfRenderer(
        renderer: PdfRenderer,
        pageIndex: Int,
        maxWidth: Int,
    ): Bitmap {
        renderer.openPage(pageIndex).use { page ->
            val longEdge = maxOf(page.width, page.height).toFloat().coerceAtLeast(1f)
            val scale = (maxWidth / longEdge).coerceIn(0.25f, 4f)
            val width = (page.width * scale).toInt().coerceAtLeast(1)
            val height = (page.height * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        }
    }

    private fun artifact(file: File): ExportArtifact =
        ExportArtifact(
            filePath = file.absolutePath,
            fileName = file.name,
            mimeType = PdfMimeType,
        )

    private fun newOutputFile(prefix: String): File {
        val dir = toolsDir()
        return File(dir, "${prefix}_${System.currentTimeMillis()}.pdf")
    }

    private fun toolsDir(): File {
        val dir = File(context.cacheDir, "pdf-tools")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun ensurePdfBox() {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    private fun PdfToolSource.cacheKey(): String = when (this) {
        is PdfToolSource.DeviceUri -> "uri:$uriString"
        is PdfToolSource.LibraryDocument -> "lib:$documentId"
        is PdfToolSource.AppFile -> "file:$filePath:${File(filePath).lastModified()}"
    }

    private fun PdfToolSource.displayLabel(): String = when (this) {
        is PdfToolSource.DeviceUri -> displayName
        is PdfToolSource.LibraryDocument -> title
        is PdfToolSource.AppFile -> displayName
    }

    private data class ResolvedFile(
        val file: File,
        val deleteOnClose: Boolean,
    ) {
        inline fun <T> useFile(block: (File) -> T): T {
            return try {
                block(file)
            } finally {
                cleanup()
            }
        }

        fun cleanup() {
            if (deleteOnClose) file.delete()
        }
    }

    private fun <T> Result<T>.toScanlyResult(fallbackMessage: String): ScanlyResult<T> =
        fold(
            onSuccess = { ScanlyResult.Success(it) },
            onFailure = {
                ScanlyResult.Failure(
                    ScanlyError(message = it.message ?: fallbackMessage, cause = it),
                )
            },
        )

    private companion object {
        const val PdfMimeType = "application/pdf"
        /** ~32 MB ARGB ceiling for a single page during compress. */
        const val MaxCompressPagePixels = 8_000_000L
        /** Re-resolve the preview base after this long so edits to a library scan show up. */
        const val PreviewBaseTtlMs = 5 * 60 * 1000L
    }
}
