package `in`.c1ph3rj.scanly.data.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.core.common.DocumentPresentationFormatter
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.data.local.db.dao.DocumentDao
import `in`.c1ph3rj.scanly.data.local.db.dao.DocumentGroupDao
import `in`.c1ph3rj.scanly.data.local.db.dao.ScanPageDao
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.model.PdfExportOptions
import `in`.c1ph3rj.scanly.domain.model.PdfPageMargin
import `in`.c1ph3rj.scanly.domain.model.PdfPageOrientation
import `in`.c1ph3rj.scanly.domain.model.PdfPageSize
import `in`.c1ph3rj.scanly.domain.model.ShareArtifact
import `in`.c1ph3rj.scanly.domain.repository.DocumentExportRepository
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.roundToInt

@Singleton
class DefaultDocumentExportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentDao: DocumentDao,
    private val documentGroupDao: DocumentGroupDao,
    private val scanPageDao: ScanPageDao,
    private val dispatchers: ScanlyDispatchers,
) : DocumentExportRepository {

    // ─── Single-document exports ────────────────────────────────────────────────

    override suspend fun exportPdf(
        documentId: String,
        options: PdfExportOptions,
    ): ScanlyResult<ExportArtifact> =
        withContext(dispatchers.io) {
            runCatching {
                val exportInput = loadExportInput(documentId)
                val exportDirectory = ensureFreshExportDirectory(documentId)
                val fileName = "${exportInput.fileStem}.pdf"
                val outputFile = File(exportDirectory, fileName)
                writePdf(outputFile, exportInput.pageImagePaths, options)
                ExportArtifact(
                    filePath = outputFile.absolutePath,
                    fileName = fileName,
                    mimeType = pdfMimeType,
                )
            }.toScanlyResult("Could not export PDF.")
        }

    override suspend fun preparePdfShare(
        documentId: String,
        options: PdfExportOptions,
    ): ScanlyResult<ShareArtifact> = withContext(dispatchers.io) {
        runCatching {
            val exportInput = loadExportInput(documentId)
            val exportDirectory = ensureFreshExportDirectory(documentId)
            val outputFile = File(exportDirectory, "${exportInput.fileStem}.pdf")
            writePdf(outputFile, exportInput.pageImagePaths, options)
            ShareArtifact(
                mimeType = pdfMimeType,
                title = exportInput.documentTitle,
                filePaths = listOf(outputFile.absolutePath),
            )
        }.toScanlyResult("Could not prepare PDF share.")
    }

    override suspend fun exportImageArchive(documentId: String): ScanlyResult<ExportArtifact> =
        withContext(dispatchers.io) {
            runCatching {
                val exportInput = loadExportInput(documentId)
                val exportDirectory = ensureFreshExportDirectory(documentId)
                val fileName = "${exportInput.fileStem}_images.zip"
                val outputFile = File(exportDirectory, fileName)
                writeImageArchive(
                    outputFile = outputFile,
                    fileStem = exportInput.fileStem,
                    pageImagePaths = exportInput.pageImagePaths,
                )
                ExportArtifact(
                    filePath = outputFile.absolutePath,
                    fileName = fileName,
                    mimeType = zipMimeType,
                )
            }.toScanlyResult("Could not export images.")
        }

    override suspend fun prepareImageShare(documentId: String): ScanlyResult<ShareArtifact> =
        withContext(dispatchers.io) {
            runCatching {
                val exportInput = loadExportInput(documentId)
                ShareArtifact(
                    mimeType = imageMimeType,
                    title = exportInput.documentTitle,
                    filePaths = exportInput.pageImagePaths,
                )
            }.toScanlyResult("Could not prepare images for sharing.")
        }

    // ─── Group exports ──────────────────────────────────────────────────────────

    override suspend fun exportGroupAsSinglePdf(
        groupId: String,
        options: PdfExportOptions,
        onProgress: (current: Int, total: Int) -> Unit,
    ): ScanlyResult<ExportArtifact> = withContext(dispatchers.io) {
        runCatching {
            val output = buildGroupSinglePdf(groupId, options, onProgress)
            ExportArtifact(
                filePath = output.file.absolutePath,
                fileName = output.file.name,
                mimeType = pdfMimeType,
            )
        }.toScanlyResult("Could not export group as PDF.")
    }

    override suspend fun exportGroupAsZippedPdfs(
        groupId: String,
        options: PdfExportOptions,
        onProgress: (currentDoc: Int, totalDocs: Int) -> Unit,
    ): ScanlyResult<ExportArtifact> = withContext(dispatchers.io) {
        runCatching {
            val output = buildGroupZippedPdfs(groupId, options, onProgress)
            ExportArtifact(
                filePath = output.file.absolutePath,
                fileName = output.file.name,
                mimeType = zipMimeType,
            )
        }.toScanlyResult("Could not export group as ZIP.")
    }

    override suspend fun prepareGroupSinglePdfShare(
        groupId: String,
        options: PdfExportOptions,
        onProgress: (current: Int, total: Int) -> Unit,
    ): ScanlyResult<ShareArtifact> = withContext(dispatchers.io) {
        runCatching {
            val output = buildGroupSinglePdf(groupId, options, onProgress)
            ShareArtifact(
                mimeType = pdfMimeType,
                title = output.title,
                filePaths = listOf(output.file.absolutePath),
            )
        }.toScanlyResult("Could not prepare group PDF share.")
    }

    override suspend fun prepareGroupZippedPdfsShare(
        groupId: String,
        options: PdfExportOptions,
        onProgress: (currentDoc: Int, totalDocs: Int) -> Unit,
    ): ScanlyResult<ShareArtifact> = withContext(dispatchers.io) {
        runCatching {
            val output = buildGroupZippedPdfs(groupId, options, onProgress)
            ShareArtifact(
                mimeType = zipMimeType,
                title = output.title,
                filePaths = listOf(output.file.absolutePath),
            )
        }.toScanlyResult("Could not prepare group ZIP share.")
    }

    private suspend fun buildGroupSinglePdf(
        groupId: String,
        options: PdfExportOptions,
        onProgress: (current: Int, total: Int) -> Unit,
    ): GroupExportOutput {
        val group = documentGroupDao.getGroup(groupId) ?: error("Group not found.")
        val documents = documentDao.getDocumentsByGroup(groupId)
        if (documents.isEmpty()) error("No documents in group.")

        // Collect all page image paths across all documents in title order
        val allPagePaths = documents.flatMap { doc ->
            scanPageDao.getPages(doc.id).map { page ->
                page.processedImagePath ?: page.rawImagePath
                    ?: error("Missing image for a page in \"${doc.title}\".")
            }
        }
        if (allPagePaths.isEmpty()) error("No pages available to export.")

        val exportDir = ensureFreshGroupExportDirectory(groupId)
        val fileStem = DocumentPresentationFormatter.safeFileStem(group.title)
        val outputFile = File(exportDir, "$fileStem.pdf")

        writePdfWithProgress(outputFile, allPagePaths, options, onProgress)

        return GroupExportOutput(file = outputFile, title = group.title)
    }

    private suspend fun buildGroupZippedPdfs(
        groupId: String,
        options: PdfExportOptions,
        onProgress: (currentDoc: Int, totalDocs: Int) -> Unit,
    ): GroupExportOutput {
        val group = documentGroupDao.getGroup(groupId) ?: error("Group not found.")
        val documents = documentDao.getDocumentsByGroup(groupId)
        if (documents.isEmpty()) error("No documents in group.")

        val exportDir = ensureFreshGroupExportDirectory(groupId)
        val groupFileStem = DocumentPresentationFormatter.safeFileStem(group.title)
        val zipFile = File(exportDir, "${groupFileStem}_documents.zip")
        val tempPdfFiles = mutableListOf<File>()
        var bundledDocuments = 0

        try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
                documents.forEachIndexed { index, doc ->
                    onProgress(index + 1, documents.size)
                    val pages = scanPageDao.getPages(doc.id)
                    val pageImagePaths = pages.mapNotNull { page ->
                        page.processedImagePath ?: page.rawImagePath
                    }
                    if (pageImagePaths.isEmpty()) return@forEachIndexed

                    val docFileStem = DocumentPresentationFormatter.safeFileStem(doc.title)
                    val tempPdf = File(exportDir, "$docFileStem.pdf")
                    tempPdfFiles.add(tempPdf)
                    writePdf(tempPdf, pageImagePaths, options)

                    zipOut.putNextEntry(ZipEntry("$docFileStem.pdf"))
                    tempPdf.inputStream().use { it.copyTo(zipOut) }
                    zipOut.closeEntry()
                    bundledDocuments++
                }
            }
        } finally {
            tempPdfFiles.forEach { it.delete() }
        }

        if (bundledDocuments == 0) error("No pages available to export.")

        return GroupExportOutput(file = zipFile, title = group.title)
    }

    // ─── Private helpers ────────────────────────────────────────────────────────

    private suspend fun loadExportInput(documentId: String): ExportInput {
        val document = documentDao.getDocument(documentId) ?: error("Document not found.")
        val pages = scanPageDao.getPages(documentId)
        val pageImagePaths = pages.map { page ->
            page.processedImagePath ?: page.rawImagePath
                ?: error("Missing image for page ${page.pageIndex + 1}.")
        }
        if (pageImagePaths.isEmpty()) error("No pages available to export.")
        return ExportInput(
            documentTitle = document.title,
            fileStem = DocumentPresentationFormatter.safeFileStem(document.title),
            pageImagePaths = pageImagePaths,
        )
    }

    private fun ensureFreshExportDirectory(documentId: String): File =
        ensureDirectory("exports/$documentId")

    private fun ensureFreshGroupExportDirectory(groupId: String): File =
        ensureDirectory("exports/group_$groupId")

    private fun ensureDirectory(relativePath: String): File {
        val directory = File(context.cacheDir, relativePath)
        if (directory.exists()) directory.deleteRecursively()
        if (!directory.mkdirs()) error("Could not create export directory.")
        return directory
    }

    private fun writePdf(
        outputFile: File,
        pageImagePaths: List<String>,
        options: PdfExportOptions,
    ) = writePdfWithProgress(outputFile, pageImagePaths, options, onProgress = null)

    private fun writePdfWithProgress(
        outputFile: File,
        pageImagePaths: List<String>,
        options: PdfExportOptions,
        onProgress: ((current: Int, total: Int) -> Unit)?,
    ) {
        val pdfDocument = PdfDocument()
        val backgroundPaint = Paint().apply { color = Color.WHITE }

        pageImagePaths.forEachIndexed { index, imagePath ->
            val bitmap = decodeBitmapForExport(imagePath)
                ?: error("Could not decode $imagePath for export.")
            val pageLayout = resolvePageLayout(bitmap.width, bitmap.height, options)
            val pageInfo = PdfDocument.PageInfo.Builder(
                pageLayout.pageWidthPx,
                pageLayout.pageHeightPx,
                index + 1,
            ).create()
            val page = pdfDocument.startPage(pageInfo)
            try {
                val canvas = page.canvas
                canvas.drawRect(
                    0f, 0f,
                    pageLayout.pageWidthPx.toFloat(),
                    pageLayout.pageHeightPx.toFloat(),
                    backgroundPaint,
                )
                val destRect = fitRect(
                    sourceWidth = bitmap.width.toFloat(),
                    sourceHeight = bitmap.height.toFloat(),
                    targetWidth = pageLayout.contentWidthPx.toFloat(),
                    targetHeight = pageLayout.contentHeightPx.toFloat(),
                ).apply {
                    offset(pageLayout.marginPx.toFloat(), pageLayout.marginPx.toFloat())
                }
                canvas.drawBitmap(bitmap, null, destRect, null)
            } finally {
                pdfDocument.finishPage(page)
                bitmap.recycle()
            }
            onProgress?.invoke(index + 1, pageImagePaths.size)
        }

        outputFile.outputStream().use { pdfDocument.writeTo(it) }
        pdfDocument.close()
    }

    private fun writeImageArchive(
        outputFile: File,
        fileStem: String,
        pageImagePaths: List<String>,
    ) {
        ZipOutputStream(FileOutputStream(outputFile)).use { zipOut ->
            pageImagePaths.forEachIndexed { index, imagePath ->
                val sourceFile = File(imagePath)
                if (!sourceFile.exists()) return@forEachIndexed
                zipOut.putNextEntry(ZipEntry("${fileStem}_p${index + 1}.jpg"))
                sourceFile.inputStream().use { it.copyTo(zipOut) }
                zipOut.closeEntry()
            }
        }
    }

    private fun decodeBitmapForExport(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        return BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply {
                inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, maxExportBitmapDimension)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        )
    }

    private fun calculateSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        var w = width
        var h = height
        while (w > maxDimension || h > maxDimension) {
            w /= 2; h /= 2; sampleSize *= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    private fun fitRect(
        sourceWidth: Float,
        sourceHeight: Float,
        targetWidth: Float,
        targetHeight: Float,
    ): RectF {
        val scale = min(targetWidth / sourceWidth, targetHeight / sourceHeight)
        val scaledWidth = sourceWidth * scale
        val scaledHeight = sourceHeight * scale
        val left = (targetWidth - scaledWidth) / 2f
        val top = (targetHeight - scaledHeight) / 2f
        return RectF(left, top, left + scaledWidth, top + scaledHeight)
    }

    private fun resolvePageLayout(
        imageWidth: Int,
        imageHeight: Int,
        options: PdfExportOptions,
    ): PageLayout {
        val (pageWidthPx, pageHeightPx) = when (options.pageSize) {
            PdfPageSize.FIT -> orientedFitDimensions(imageWidth, imageHeight, options.orientation)
            PdfPageSize.A4 -> when (options.orientation) {
                PdfPageOrientation.PORTRAIT -> a4PortraitWidthPx to a4PortraitHeightPx
                PdfPageOrientation.LANDSCAPE -> a4PortraitHeightPx to a4PortraitWidthPx
            }
            PdfPageSize.US_LETTER -> when (options.orientation) {
                PdfPageOrientation.PORTRAIT -> letterPortraitWidthPx to letterPortraitHeightPx
                PdfPageOrientation.LANDSCAPE -> letterPortraitHeightPx to letterPortraitWidthPx
            }
        }
        val marginPx = when (options.margin) {
            PdfPageMargin.NONE -> 0
            PdfPageMargin.SMALL -> (min(pageWidthPx, pageHeightPx) * SmallMarginFraction).roundToInt().coerceAtLeast(20)
            PdfPageMargin.LARGE -> (min(pageWidthPx, pageHeightPx) * LargeMarginFraction).roundToInt().coerceAtLeast(40)
        }
        return PageLayout(pageWidthPx, pageHeightPx, marginPx)
    }

    private fun orientedFitDimensions(width: Int, height: Int, orientation: PdfPageOrientation): Pair<Int, Int> {
        val short = min(width, height)
        val long = maxOf(width, height)
        return when (orientation) {
            PdfPageOrientation.PORTRAIT -> short to long
            PdfPageOrientation.LANDSCAPE -> long to short
        }
    }

    private fun <T> Result<T>.toScanlyResult(fallbackMessage: String): ScanlyResult<T> =
        fold(
            onSuccess = { ScanlyResult.Success(it) },
            onFailure = {
                ScanlyResult.Failure(ScanlyError(message = it.message ?: fallbackMessage, cause = it))
            },
        )

    private data class ExportInput(
        val documentTitle: String,
        val fileStem: String,
        val pageImagePaths: List<String>,
    )

    private data class GroupExportOutput(
        val file: File,
        val title: String,
    )

    private data class PageLayout(
        val pageWidthPx: Int,
        val pageHeightPx: Int,
        val marginPx: Int,
    ) {
        val contentWidthPx: Int get() = (pageWidthPx - marginPx * 2).coerceAtLeast(1)
        val contentHeightPx: Int get() = (pageHeightPx - marginPx * 2).coerceAtLeast(1)
    }

    private companion object {
        const val maxExportBitmapDimension = 2400
        const val pdfMimeType = "application/pdf"
        const val zipMimeType = "application/zip"
        const val imageMimeType = "image/jpeg"
        const val a4PortraitWidthPx = 1240
        const val a4PortraitHeightPx = 1754
        const val letterPortraitWidthPx = 1275
        const val letterPortraitHeightPx = 1650
        const val SmallMarginFraction = 0.04f
        const val LargeMarginFraction = 0.08f
    }
}
