package `in`.c1ph3rj.scanly.data.pdftools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix as PdfMatrix
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.model.PdfCompressQuality
import `in`.c1ph3rj.scanly.domain.model.PdfDocumentInfo
import `in`.c1ph3rj.scanly.domain.model.PdfExportOptions
import `in`.c1ph3rj.scanly.domain.model.PdfToolSource
import `in`.c1ph3rj.scanly.domain.model.WatermarkLayout
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

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
                    val policy = StandardProtectionPolicy(
                        UUID.randomUUID().toString(),
                        newPassword,
                        AccessPermission(),
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
                    if (document.isEncrypted) {
                        document.setAllSecurityToBeRemoved(true)
                    }
                    val text = options.text.trim()
                    document.pages.forEachIndexed { pageIndex, page ->
                        if (options.pageRange == WatermarkPageRange.FIRST_PAGE && pageIndex != 0) {
                            return@forEachIndexed
                        }
                        stampWatermarkPage(document, page, options, text)
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
            resolveLocalFile(source).useFile { local ->
                val previewFile = File(toolsDir(), "watermark_preview_${UUID.randomUUID()}.pdf")
                try {
                    openDocument(local, password).use { document ->
                        require(document.numberOfPages > 0) { "This PDF has no pages." }
                        if (document.isEncrypted) {
                            document.setAllSecurityToBeRemoved(true)
                        }
                        while (document.numberOfPages > 1) {
                            document.removePage(document.numberOfPages - 1)
                        }
                        stampWatermarkPage(
                            document = document,
                            page = document.getPage(0),
                            options = options,
                            text = options.text.trim(),
                        )
                        document.save(previewFile)
                    }
                    renderWithPdfRenderer(previewFile, pageIndex = 0, maxWidth = maxWidth)
                } finally {
                    previewFile.delete()
                }
            }
        }.toScanlyResult("Could not update watermark preview.")
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

    private fun stampWatermarkPage(
        document: PDDocument,
        page: PDPage,
        options: WatermarkOptions,
        text: String,
    ) {
        // Use the visible crop box rather than the media box so centred and repeated marks
        // follow exactly the page area rendered by Android's PdfRenderer.
        val pageBox = page.cropBox ?: page.mediaBox
        val font = PDType1Font.HELVETICA_BOLD
        val fontSize = resolveWatermarkFontSize(
            pageWidth = pageBox.width,
            pageHeight = pageBox.height,
            text = text,
            font = font,
            options = options,
        )
        val unitWidth = font.getStringWidth(text) / 1000f
        // Slight tracking stretches letters so stamps read as watermarks, not body text.
        val characterSpacing = when (options.layout) {
            WatermarkLayout.CENTERED -> (fontSize * 0.045f).coerceIn(1.2f, 6f)
            WatermarkLayout.REPEATED -> (fontSize * 0.03f).coerceIn(0.6f, 3.5f)
        }
        val trackingExtra = if (text.length > 1) (text.length - 1) * characterSpacing else 0f
        val textWidth = unitWidth * fontSize + trackingExtra
        val baselineOffset = fontSize * 0.32f
        val radians = Math.toRadians(options.angleDegrees.toDouble())
        val cos = cos(radians).toFloat()
        val sin = sin(radians).toFloat()
        val positions = when (options.layout) {
            WatermarkLayout.CENTERED -> listOf(
                pageBox.lowerLeftX + pageBox.width / 2f to
                    pageBox.lowerLeftY + pageBox.height / 2f,
            )
            WatermarkLayout.REPEATED -> watermarkTilePositions(
                lowerX = pageBox.lowerLeftX,
                lowerY = pageBox.lowerLeftY,
                width = pageBox.width,
                height = pageBox.height,
                textWidth = textWidth,
                fontSize = fontSize,
                angleDegrees = options.angleDegrees,
            )
        }

        PDPageContentStream(
            document,
            page,
            PDPageContentStream.AppendMode.APPEND,
            true,
            true,
        ).use { stream ->
            val graphicsState = PDExtendedGraphicsState().apply {
                nonStrokingAlphaConstant = options.opacity.coerceIn(0.05f, 0.8f)
            }
            stream.setGraphicsStateParameters(graphicsState)
            stream.beginText()
            stream.setFont(font, fontSize)
            stream.setCharacterSpacing(characterSpacing)
            // Cool mid-gray reads as a security mark without drowning body text.
            stream.setNonStrokingColor(0.42f, 0.42f, 0.46f)
            positions.forEach { (centerX, centerY) ->
                stream.setTextMatrix(PdfMatrix(cos, sin, -sin, cos, centerX, centerY))
                stream.newLineAtOffset(-textWidth / 2f, -baselineOffset)
                stream.showText(text)
            }
            stream.endText()
        }
    }

    /**
     * Page-relative point size so a Medium stamp looks intentional on both phone
     * captures and full A4 pages instead of a fixed 42pt that is either tiny or sparse.
     */
    private fun resolveWatermarkFontSize(
        pageWidth: Float,
        pageHeight: Float,
        text: String,
        font: PDType1Font,
        options: WatermarkOptions,
    ): Float {
        val scale = options.size.scale
        val unitWidth = (font.getStringWidth(text) / 1000f).coerceAtLeast(0.01f)
        val shortestEdge = minOf(pageWidth, pageHeight)
        return when (options.layout) {
            WatermarkLayout.CENTERED -> {
                // Dominant single stamp: ~80–95% of the longer page edge so portrait
                // pages still get a bold diagonal mark.
                val referenceEdge = maxOf(pageWidth, pageHeight)
                val widthFraction = (0.72f * scale).coerceIn(0.48f, 0.9f)
                val targetWidth = referenceEdge * widthFraction
                // Prefer spanning most of the page width on wide/short pages.
                val widthTarget = pageWidth * (0.88f * scale).coerceIn(0.55f, 0.96f)
                maxOf(targetWidth / unitWidth, widthTarget / unitWidth).coerceIn(48f, 180f)
            }
            WatermarkLayout.REPEATED -> {
                // Tile size scales with paper so the field stays dense on any page size.
                (shortestEdge * 0.064f * scale).coerceIn(16f, 52f)
            }
        }
    }

    private fun watermarkTilePositions(
        lowerX: Float,
        lowerY: Float,
        width: Float,
        height: Float,
        textWidth: Float,
        fontSize: Float,
        angleDegrees: Float,
    ): List<Pair<Float, Float>> {
        val radians = Math.toRadians(angleDegrees.toDouble())
        val absoluteCos = abs(cos(radians)).toFloat()
        val absoluteSin = abs(sin(radians)).toFloat()
        // Axis-aligned bounds of the rotated glyph box (width × body height).
        val textHeight = fontSize
        val rotatedWidth = textWidth * absoluteCos + textHeight * absoluteSin
        val rotatedHeight = textWidth * absoluteSin + textHeight * absoluteCos
        // 0 at horizontal/vertical, 1 near 45°. Diagonal stamps may pack tighter than
        // their AABB; horizontal stamps must keep full width/height or they collide.
        val diagonalFactor = abs(sin(2.0 * radians)).toFloat().coerceIn(0f, 1f)
        val horizontalPack = 1f - 0.28f * diagonalFactor
        val verticalPack = 1f - 0.40f * diagonalFactor
        val horizontalGap = fontSize * (1.15f - 0.45f * diagonalFactor)
        val verticalGap = fontSize * (1.55f - 0.55f * diagonalFactor)
        val horizontalStep = maxOf(
            rotatedWidth * horizontalPack + horizontalGap,
            textWidth * (1f - 0.2f * diagonalFactor) + horizontalGap,
            fontSize * 3.2f,
        )
        val verticalStep = maxOf(
            rotatedHeight * verticalPack + verticalGap,
            textHeight * (1.8f - 0.4f * diagonalFactor) + verticalGap,
            fontSize * 2.6f,
        )
        // Bleed past the crop so rotated glyphs still cover corners and edges.
        // Horizontal needs less bleed; diagonal needs more.
        val padX = rotatedWidth * (0.35f + 0.25f * diagonalFactor)
        val padY = rotatedHeight * (0.35f + 0.25f * diagonalFactor)
        // Brick stagger only when diagonal — on horizontal it shifts rows into collisions.
        val rowStagger = if (diagonalFactor < 0.12f) 0f else horizontalStep * 0.5f
        return buildList {
            var row = 0
            var y = lowerY - padY
            val maximumY = lowerY + height + padY
            while (y <= maximumY + 0.5f) {
                val rowOffset = if (row % 2 == 0) 0f else rowStagger
                var x = lowerX - padX + rowOffset
                val maximumX = lowerX + width + padX
                while (x <= maximumX + 0.5f) {
                    add(x to y)
                    x += horizontalStep
                }
                y += verticalStep
                row += 1
                // Hard cap guards pathological short text / tiny pages from huge streams.
                if (size >= MAX_WATERMARK_TILES) break
            }
        }.take(MAX_WATERMARK_TILES)
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
        /** Safety cap for pathological short labels / tiny pages. */
        const val MAX_WATERMARK_TILES = 96
    }
}
