package `in`.c1ph3rj.scanly.data.pdftools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
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
import `in`.c1ph3rj.scanly.domain.model.PdfImageExport
import `in`.c1ph3rj.scanly.domain.model.PdfImageFormat
import `in`.c1ph3rj.scanly.domain.model.PdfImageOptions
import `in`.c1ph3rj.scanly.domain.model.PdfPageImage
import `in`.c1ph3rj.scanly.domain.model.PdfSignSession
import `in`.c1ph3rj.scanly.domain.model.PdfToolSource
import `in`.c1ph3rj.scanly.domain.model.PdfWorkingCopy
import `in`.c1ph3rj.scanly.domain.model.SignMark
import `in`.c1ph3rj.scanly.domain.model.SignPageSize
import `in`.c1ph3rj.scanly.domain.model.WatermarkOptions
import `in`.c1ph3rj.scanly.domain.model.WatermarkPageRange
import `in`.c1ph3rj.scanly.data.storage.SharedStorageDestinationManager
import `in`.c1ph3rj.scanly.domain.repository.DocumentExportRepository
import `in`.c1ph3rj.scanly.domain.repository.PdfToolkitRepository
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlin.math.sqrt
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultPdfToolkitRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentExportRepository: DocumentExportRepository,
    private val storageDestinationManager: SharedStorageDestinationManager,
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
            // Resolved one by one so a failure part-way still cleans up the copies made so far.
            val resolvedSources = mutableListOf<ResolvedFile>()
            output.deleteOnFailure {
                try {
                    sources.forEach { resolvedSources += resolveLocalFile(it) }
                    val sourceDocuments = mutableListOf<PDDocument>()
                    try {
                        resolvedSources.forEachIndexed { index, resolved ->
                            val password = passwords[index]
                            sourceDocuments += try {
                                openDocument(resolved.file, password)
                            } catch (error: IllegalArgumentException) {
                                // Merge has no password prompt, so say which file needs unlocking.
                                if (password.isNullOrBlank() && error.message == PasswordProtectedMessage) {
                                    val label = sources[index].displayLabel().ifBlank { "One of the PDFs" }
                                    throw IllegalArgumentException(
                                        "\"$label\" is password protected. Remove its password first.",
                                        error,
                                    )
                                }
                                throw error
                            }
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
                val output = newOutputFile("compressed")
                try {
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
                    // Re-rendering already-lean PDFs can grow them; keep the original in that case.
                    check(output.length() < local.length()) {
                        "This PDF is already well optimised. Compressing it would not make it smaller."
                    }
                    artifact(output)
                } catch (oom: OutOfMemoryError) {
                    output.delete()
                    throw IllegalStateException(
                        "Not enough memory to compress this PDF. Try Smallest quality or a shorter file.",
                        oom,
                    )
                } catch (error: Throwable) {
                    output.delete()
                    throw error
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
                output.deleteOnFailure {
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
                output.deleteOnFailure {
                    openDocument(local, currentPassword).use { document ->
                        document.setAllSecurityToBeRemoved(true)
                        document.save(output)
                    }
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
                output.deleteOnFailure {
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
            file.deleteOnFailure {
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
            }
            previewBase = PreviewBase(key = key, file = file, createdAt = now)
            file
        }

    override suspend fun openSignSession(
        source: PdfToolSource,
        password: String?,
    ): ScanlyResult<PdfSignSession> = withContext(dispatchers.io) {
        runCatching {
            ensurePdfBox()
            val file = File(toolsDir(), "sign_${UUID.randomUUID()}.pdf")
            // A cancelled open would otherwise return its session to nobody (withContext throws
            // on return), so the check inside this block deletes the copy before that happens.
            file.deleteOnFailure {
                val imageSource = (source as? PdfToolSource.DeviceUri)?.takeIf { it.isImage() }
                if (imageSource != null) {
                    val size = writeImageAsPdf(Uri.parse(imageSource.uriString), file)
                    coroutineContext.ensureActive()
                    return@runCatching PdfSignSession(
                        file = PdfToolSource.AppFile(file.absolutePath, source.displayLabel()),
                        pageSizes = listOf(size),
                    )
                }
                val pageSizes = resolveLocalFile(source).useFile { local ->
                    openDocument(local, password).use { document ->
                        require(document.numberOfPages > 0) { "This PDF has no pages." }
                        if (document.isEncrypted) {
                            document.setAllSecurityToBeRemoved(true)
                        }
                        val sizes = document.pages.map { page ->
                            val box = page.cropBox ?: page.mediaBox
                            val (width, height) = WatermarkGeometry.visualSize(box.width, box.height, page.rotation)
                            SignPageSize(width, height)
                        }
                        // One decrypted local copy for the whole editing session: page renders read
                        // it in place instead of re-copying or re-exporting the source each time.
                        document.save(file)
                        sizes
                    }
                }
                coroutineContext.ensureActive()
                PdfSignSession(
                    file = PdfToolSource.AppFile(file.absolutePath, source.displayLabel()),
                    pageSizes = pageSizes,
                )
            }
        }.toScanlyResult("Could not open this PDF for signing.")
    }

    private fun PdfToolSource.DeviceUri.isImage(): Boolean =
        context.contentResolver.getType(Uri.parse(uriString))?.startsWith("image/") == true

    /**
     * Wraps a picked photo/scan in a one-page PDF so it can be signed like any PDF. The page is
     * sized like Scanly's PDF export (long edge 842 pt, A4 height) with the image filling it.
     */
    private fun writeImageAsPdf(uri: Uri, target: File): SignPageSize {
        val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
        val decoded = android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            val longEdge = maxOf(info.size.width, info.size.height)
            if (longEdge > MaxImagePageEdgePx) {
                val scale = MaxImagePageEdgePx.toFloat() / longEdge
                decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1),
                )
            }
        }
        // Flatten any transparency onto white paper before JPEG encoding.
        val flat = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(flat).apply {
            drawColor(Color.WHITE)
            drawBitmap(decoded, 0f, 0f, null)
        }
        decoded.recycle()
        val scale = ImagePageLongEdgePt / maxOf(flat.width, flat.height)
        val width = flat.width * scale
        val height = flat.height * scale
        try {
            PDDocument().use { document ->
                val page = PDPage(PDRectangle(width, height))
                document.addPage(page)
                val image = JPEGFactory.createFromByteArray(document, flat.toJpegBytes(ImagePageJpegQuality))
                PDPageContentStream(document, page).use { stream -> stream.drawImage(image, 0f, 0f, width, height) }
                document.save(target)
            }
        } finally {
            flat.recycle()
        }
        return SignPageSize(width, height)
    }

    override suspend fun sign(
        session: PdfSignSession,
        marks: List<SignMark>,
    ): ScanlyResult<ExportArtifact> = withContext(dispatchers.io) {
        runCatching {
            require(marks.isNotEmpty()) { "Place a signature or another mark first." }
            ensurePdfBox()
            resolveLocalFile(session.file).useFile { local ->
                val output = newOutputFile("signed")
                output.deleteOnFailure {
                    openDocument(local, null).use { document ->
                        val stamper = PdfSignatureStamper(document)
                        stamper.validate(marks)
                        marks.groupBy { it.pageIndex }.forEach { (pageIndex, pageMarks) ->
                            require(pageIndex in 0 until document.numberOfPages) { "A mark is on a missing page." }
                            stamper.stamp(document.getPage(pageIndex), pageMarks)
                        }
                        document.save(output)
                    }
                }
                artifact(output)
            }
        }.toScanlyResult("Could not sign this PDF.")
    }

    override suspend fun closeSignSession(session: PdfSignSession) {
        withContext(dispatchers.io) {
            val file = File(session.file.filePath)
            if (file.parentFile?.canonicalFile == toolsDir().canonicalFile && file.name.startsWith("sign_")) {
                file.delete()
            }
        }
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
                    unlocked.deleteOnFailure {
                        openDocument(local, password).use { document ->
                            document.setAllSecurityToBeRemoved(true)
                            document.save(unlocked)
                        }
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

    override suspend fun openWorkingCopy(
        source: PdfToolSource,
        password: String?,
    ): ScanlyResult<PdfWorkingCopy> = withContext(dispatchers.io) {
        runCatching {
            ensurePdfBox()
            val file = File(toolsDir(), "work_${UUID.randomUUID()}.pdf")
            try {
                val pageCount = resolveLocalFile(source).useFile { local ->
                    openDocument(local, password).use { document ->
                        require(document.numberOfPages > 0) { "This PDF has no pages." }
                        if (document.isEncrypted) {
                            document.setAllSecurityToBeRemoved(true)
                            document.save(file)
                        } else {
                            // A byte copy keeps the file exactly as authored and skips a slow re-save.
                            local.copyTo(file, overwrite = true)
                        }
                        document.numberOfPages
                    }
                }
                // Some files PDFBox accepts still trip PdfRenderer; a PDFBox re-save fixes those.
                val renderable = unlockForRendererIfNeeded(file, null)
                if (renderable != file) {
                    file.delete()
                    check(renderable.renameTo(file)) { "Could not prepare this PDF." }
                }
                // A cancelled open can no longer hand its copy to anyone (withContext throws on
                // return), so bail out here and let the catch below delete it.
                coroutineContext.ensureActive()
                PdfWorkingCopy(
                    file = PdfToolSource.AppFile(file.absolutePath, source.displayLabel()),
                    pageCount = pageCount,
                )
            } catch (error: Throwable) {
                file.delete()
                throw error
            }
        }.toScanlyResult("Could not open this PDF.")
    }

    override suspend fun closeWorkingCopy(copy: PdfWorkingCopy) {
        withContext(dispatchers.io) {
            val file = File(copy.file.filePath)
            if (file.parentFile?.canonicalFile == toolsDir().canonicalFile && file.name.startsWith("work_")) {
                file.delete()
            }
        }
    }

    override suspend fun estimatePageImageBytes(
        copy: PdfWorkingCopy,
        pageIndex: Int,
        options: PdfImageOptions,
    ): ScanlyResult<Long> = withContext(dispatchers.io) {
        runCatching {
            resolveLocalFile(copy.file).useFile { local ->
                ParcelFileDescriptor.open(local, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        require(pageIndex in 0 until renderer.pageCount) { "Page is out of range." }
                        val bitmap = renderPageForImage(renderer, pageIndex, options.resolution.widthPx)
                        try {
                            val stream = ByteArrayOutputStream()
                            bitmap.encodeTo(options.format, stream)
                            stream.size().toLong()
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
        }.toScanlyResult("Could not estimate the image size.")
    }

    override suspend fun exportPagesAsImages(
        copy: PdfWorkingCopy,
        pageIndexes: List<Int>,
        options: PdfImageOptions,
        baseName: String,
        onProgress: suspend (done: Int, total: Int) -> Unit,
    ): ScanlyResult<PdfImageExport> = withContext(dispatchers.io) {
        runCatching {
            require(pageIndexes.isNotEmpty()) { "Choose at least one page." }
            val format = options.format
            val base = baseName.ifBlank { "Scanly" }
            // Only the latest export's share copies are kept; the gallery holds the real files.
            val imagesRoot = File(toolsDir(), "images")
            imagesRoot.listFiles()?.forEach(File::deleteRecursively)
            val outDir = File(imagesRoot, System.currentTimeMillis().toString()).apply { mkdirs() }
            val images = mutableListOf<PdfPageImage>()
            try {
                resolveLocalFile(copy.file).useFile { local ->
                    ParcelFileDescriptor.open(local, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                        PdfRenderer(pfd).use { renderer ->
                            pageIndexes.forEachIndexed { done, pageIndex ->
                                coroutineContext.ensureActive()
                                require(pageIndex in 0 until renderer.pageCount) { "Page ${pageIndex + 1} is missing." }
                                val name = "$base – page ${pageIndex + 1}.${format.extension}"
                                val target = File(outDir, name)
                                val bitmap = renderPageForImage(renderer, pageIndex, options.resolution.widthPx)
                                try {
                                    FileOutputStream(target).use { output -> bitmap.encodeTo(format, output) }
                                } finally {
                                    bitmap.recycle()
                                }
                                val saved = storageDestinationManager.saveImageToGallery(target, name, format.mimeType)
                                images += PdfPageImage(
                                    pageIndex = pageIndex,
                                    fileName = saved.fileName,
                                    filePath = target.absolutePath,
                                    contentUri = saved.uriString,
                                    sizeBytes = target.length(),
                                )
                                onProgress(done + 1, pageIndexes.size)
                            }
                        }
                    }
                }
            } catch (oom: OutOfMemoryError) {
                throw IllegalStateException(
                    "Not enough memory for this page size. Try the \"For sharing\" resolution.",
                    oom,
                )
            }
            PdfImageExport(
                images = images,
                destinationLabel = SharedStorageDestinationManager.GALLERY_LABEL,
                options = options,
            )
        }.toScanlyResult("Could not save the images.")
    }

    override suspend fun renderPageToJpeg(
        copy: PdfWorkingCopy,
        pageIndex: Int,
        longEdgePx: Int,
        targetPath: String,
    ): ScanlyResult<Unit> = withContext(dispatchers.io) {
        runCatching {
            resolveLocalFile(copy.file).useFile { local ->
                ParcelFileDescriptor.open(local, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        require(pageIndex in 0 until renderer.pageCount) { "Page ${pageIndex + 1} is missing." }
                        // Landscape pages get the long edge as their width; portrait pages as height.
                        val widthPx = renderer.openPage(pageIndex).use { page ->
                            val pageW = page.width.coerceAtLeast(1)
                            val pageH = page.height.coerceAtLeast(1)
                            if (pageW >= pageH) longEdgePx else (longEdgePx.toLong() * pageW / pageH).toInt()
                        }
                        val bitmap = try {
                            renderPageForImage(renderer, pageIndex, widthPx.coerceAtLeast(1))
                        } catch (oom: OutOfMemoryError) {
                            throw IllegalStateException("Not enough memory to read page ${pageIndex + 1}.", oom)
                        }
                        try {
                            val target = File(targetPath)
                            target.parentFile?.mkdirs()
                            FileOutputStream(target).use { output ->
                                require(bitmap.compress(Bitmap.CompressFormat.JPEG, LibraryPageJpegQuality, output)) {
                                    "Failed to encode page ${pageIndex + 1}."
                                }
                            }
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
            Unit
        }.toScanlyResult("Could not read this page.")
    }

    /**
     * Renders a page exactly [widthPx] wide (height follows the page), capped at
     * [MaxImagePagePixels] so a very tall page cannot exhaust memory.
     */
    private fun renderPageForImage(renderer: PdfRenderer, pageIndex: Int, widthPx: Int): Bitmap {
        renderer.openPage(pageIndex).use { page ->
            val pageW = page.width.coerceAtLeast(1).toDouble()
            val pageH = page.height.coerceAtLeast(1).toDouble()
            var width = widthPx.toDouble()
            var height = width * pageH / pageW
            val pixels = width * height
            if (pixels > MaxImagePagePixels) {
                val shrink = sqrt(MaxImagePagePixels / pixels)
                width *= shrink
                height *= shrink
            }
            val bitmap = Bitmap.createBitmap(
                width.toInt().coerceAtLeast(1),
                height.toInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        }
    }

    private fun Bitmap.encodeTo(format: PdfImageFormat, output: OutputStream) {
        val ok = when (format) {
            PdfImageFormat.JPG -> compress(Bitmap.CompressFormat.JPEG, ImageExportJpegQuality, output)
            PdfImageFormat.PNG -> compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        require(ok) { "Failed to encode page image." }
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
                target.deleteOnFailure {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(target).use { output -> input.copyTo(output) }
                    } ?: error("Could not read selected PDF.")
                }
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
                            readerCopy.deleteOnFailure { exportedFile.copyTo(readerCopy, overwrite = true) }
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
                // Mark it in use: storage cleanup skips pdf-tools files touched in the last hour,
                // so a reader or sign session left open for a long time keeps its working copy.
                file.setLastModified(System.currentTimeMillis())
                ResolvedFile(file = file, deleteOnClose = false)
            }
        }
    }

    private fun openDocument(file: File, password: String?): PDDocument {
        return try {
            if (password.isNullOrBlank()) {
                PDDocument.load(file, scratchSettings())
            } else {
                PDDocument.load(file, password, scratchSettings())
            }
        } catch (error: InvalidPasswordException) {
            // Without a supplied password this just means the file is locked, not a wrong guess.
            val message = if (password.isNullOrBlank()) PasswordProtectedMessage else "Incorrect PDF password."
            throw IllegalArgumentException(message, error)
        } catch (error: Exception) {
            // Retry with empty password string for some encrypted files.
            if (!password.isNullOrBlank()) throw error
            try {
                PDDocument.load(file, "", scratchSettings())
            } catch (encrypted: Exception) {
                if (encrypted is InvalidPasswordException ||
                    encrypted.message?.contains("password", ignoreCase = true) == true
                ) {
                    throw IllegalArgumentException(PasswordProtectedMessage)
                }
                throw error
            }
        }
    }

    /**
     * PDFBox keeps decoded streams in a scratch buffer; the default is main memory, which runs
     * out on large PDFs. Past a small in-memory budget it spills to a temp file instead.
     */
    private fun scratchSettings(): MemoryUsageSetting = MemoryUsageSetting.setupMixed(ScratchMemoryBytes)

    /** Runs [block]; if it throws (cancellation included), deletes this partial file before rethrowing. */
    private inline fun <T> File.deleteOnFailure(block: () -> T): T =
        try {
            block()
        } catch (error: Throwable) {
            delete()
            throw error
        }

    private fun unlockForRendererIfNeeded(file: File, password: String?): File {
        if (!password.isNullOrBlank()) {
            ensurePdfBox()
            val unlocked = File(toolsDir(), "unlock_${UUID.randomUUID()}.pdf")
            unlocked.deleteOnFailure {
                openDocument(file, password).use { document ->
                    document.setAllSecurityToBeRemoved(true)
                    document.save(unlocked)
                }
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
            unlocked.deleteOnFailure {
                openDocument(file, null).use { document ->
                    if (document.isEncrypted) {
                        document.setAllSecurityToBeRemoved(true)
                    }
                    document.save(unlocked)
                }
            }
            unlocked
        } catch (error: Exception) {
            if (error.message?.contains("password", ignoreCase = true) == true ||
                error is InvalidPasswordException
            ) {
                throw IllegalArgumentException(PasswordProtectedMessage)
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
        if (staleSweepDone.compareAndSet(false, true)) {
            // Once per process: drop leftovers (outputs, working copies, decrypted temp copies)
            // from earlier runs, including runs that were killed before their cleanup ran.
            val cutoff = System.currentTimeMillis() - StaleToolFileMs
            dir.listFiles().orEmpty()
                .filter { it.lastModified() < cutoff }
                .forEach { it.deleteRecursively() }
        }
        return dir
    }

    private val staleSweepDone = java.util.concurrent.atomic.AtomicBoolean(false)

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
                // runCatching also catches OutOfMemoryError; its raw "Failed to allocate…" text is
                // not something to show.
                val message = if (it is OutOfMemoryError) TooLargeMessage else it.message ?: fallbackMessage
                ScanlyResult.Failure(ScanlyError(message = message, cause = it))
            },
        )

    private companion object {
        const val PdfMimeType = "application/pdf"
        const val PasswordProtectedMessage = "This PDF is password protected."
        const val TooLargeMessage = "This PDF is too large to process on this device."
        /** In-memory scratch budget for PDFBox before it spills to a temp file. */
        const val ScratchMemoryBytes = 8L * 1024 * 1024
        const val MaxImagePageEdgePx = 3000
        const val ImagePageLongEdgePt = 842f
        const val ImagePageJpegQuality = 92
        /** ~32 MB ARGB ceiling for a single page during compress. */
        const val MaxCompressPagePixels = 8_000_000L
        /** ~48 MB ARGB ceiling per page image; A4 at 2480 px wide is ~8.7 MP. */
        const val MaxImagePagePixels = 12_000_000.0
        const val ImageExportJpegQuality = 90
        /** Matches gallery imports, so PDF pages enter the processing pipeline at the same quality. */
        const val LibraryPageJpegQuality = 95
        /** Re-resolve the preview base after this long so edits to a library scan show up. */
        const val PreviewBaseTtlMs = 5 * 60 * 1000L
        /** Tool files untouched this long are leftovers from an earlier run. */
        const val StaleToolFileMs = 24 * 60 * 60 * 1000L
    }
}
