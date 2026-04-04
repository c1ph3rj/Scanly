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
    private val scanPageDao: ScanPageDao,
    private val dispatchers: ScanlyDispatchers,
) : DocumentExportRepository {

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
            }.fold(
                onSuccess = { artifact -> ScanlyResult.Success(artifact) },
                onFailure = { throwable ->
                    ScanlyResult.Failure(
                        ScanlyError(
                            message = throwable.message ?: "Could not export PDF.",
                            cause = throwable,
                        ),
                    )
                },
            )
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
        }.fold(
            onSuccess = { artifact -> ScanlyResult.Success(artifact) },
            onFailure = { throwable ->
                ScanlyResult.Failure(
                    ScanlyError(
                        message = throwable.message ?: "Could not prepare PDF share.",
                        cause = throwable,
                    ),
                )
            },
        )
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
            }.fold(
                onSuccess = { artifact -> ScanlyResult.Success(artifact) },
                onFailure = { throwable ->
                    ScanlyResult.Failure(
                        ScanlyError(
                            message = throwable.message ?: "Could not export images.",
                            cause = throwable,
                        ),
                    )
                },
            )
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
            }.fold(
                onSuccess = { artifact -> ScanlyResult.Success(artifact) },
                onFailure = { throwable ->
                    ScanlyResult.Failure(
                        ScanlyError(
                            message = throwable.message ?: "Could not prepare images for sharing.",
                            cause = throwable,
                        ),
                    )
                },
            )
        }

    private suspend fun loadExportInput(documentId: String): ExportInput {
        val document = documentDao.getDocument(documentId)
            ?: error("Document not found.")
        val pages = scanPageDao.getPages(documentId)
        val pageImagePaths = pages.map { page ->
            page.processedImagePath ?: page.rawImagePath ?: error("Missing image for page ${page.pageIndex + 1}.")
        }
        if (pageImagePaths.isEmpty()) {
            error("No pages available to export.")
        }
        return ExportInput(
            documentTitle = document.title,
            fileStem = DocumentPresentationFormatter.safeFileStem(document.title),
            pageImagePaths = pageImagePaths,
        )
    }

    private fun ensureFreshExportDirectory(documentId: String): File {
        val directory = File(context.cacheDir, "exports/$documentId")
        if (directory.exists()) {
            directory.deleteRecursively()
        }
        if (!directory.exists() && !directory.mkdirs()) {
            error("Could not create export directory.")
        }
        return directory
    }

    private fun writePdf(
        outputFile: File,
        pageImagePaths: List<String>,
        options: PdfExportOptions,
    ) {
        val pdfDocument = PdfDocument()
        val backgroundPaint = Paint().apply { color = Color.WHITE }

        pageImagePaths.forEachIndexed { index, imagePath ->
            val bitmap = decodeBitmapForExport(imagePath)
                ?: error("Could not decode $imagePath for export.")
            val pageLayout = resolvePageLayout(
                imageWidth = bitmap.width,
                imageHeight = bitmap.height,
                options = options,
            )
            val pageInfo = PdfDocument.PageInfo.Builder(
                pageLayout.pageWidthPx,
                pageLayout.pageHeightPx,
                index + 1,
            ).create()
            val page = pdfDocument.startPage(pageInfo)
            try {
                val canvas = page.canvas
                canvas.drawRect(
                    0f,
                    0f,
                    pageLayout.pageWidthPx.toFloat(),
                    pageLayout.pageHeightPx.toFloat(),
                    backgroundPaint,
                )
                val destinationRect = fitRect(
                    sourceWidth = bitmap.width.toFloat(),
                    sourceHeight = bitmap.height.toFloat(),
                    targetWidth = pageLayout.contentWidthPx.toFloat(),
                    targetHeight = pageLayout.contentHeightPx.toFloat(),
                ).apply {
                    offset(pageLayout.marginPx.toFloat(), pageLayout.marginPx.toFloat())
                }
                canvas.drawBitmap(bitmap, null, destinationRect, null)
            } finally {
                pdfDocument.finishPage(page)
                bitmap.recycle()
            }
        }

        outputFile.outputStream().use { stream ->
            pdfDocument.writeTo(stream)
        }
        pdfDocument.close()
    }

    private fun writeImageArchive(
        outputFile: File,
        fileStem: String,
        pageImagePaths: List<String>,
    ) {
        ZipOutputStream(FileOutputStream(outputFile)).use { zipOutputStream ->
            pageImagePaths.forEachIndexed { index, imagePath ->
                val sourceFile = File(imagePath)
                if (!sourceFile.exists()) return@forEachIndexed
                val zipEntry = ZipEntry("${fileStem}_p${index + 1}.jpg")
                zipOutputStream.putNextEntry(zipEntry)
                sourceFile.inputStream().use { input ->
                    input.copyTo(zipOutputStream)
                }
                zipOutputStream.closeEntry()
            }
        }
    }

    private fun decodeBitmapForExport(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(
                width = bounds.outWidth,
                height = bounds.outHeight,
                maxDimension = maxExportBitmapDimension,
            )
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(path, options)
    }

    private fun calculateSampleSize(
        width: Int,
        height: Int,
        maxDimension: Int,
    ): Int {
        var sampleSize = 1
        var currentWidth = width
        var currentHeight = height
        while (currentWidth > maxDimension || currentHeight > maxDimension) {
            currentWidth /= 2
            currentHeight /= 2
            sampleSize *= 2
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
        return RectF(
            left,
            top,
            left + scaledWidth,
            top + scaledHeight,
        )
    }

    private fun resolvePageLayout(
        imageWidth: Int,
        imageHeight: Int,
        options: PdfExportOptions,
    ): PageLayout {
        val (pageWidthPx, pageHeightPx) = when (options.pageSize) {
            PdfPageSize.FIT -> orientedFitDimensions(
                width = imageWidth,
                height = imageHeight,
                orientation = options.orientation,
            )

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

        return PageLayout(
            pageWidthPx = pageWidthPx,
            pageHeightPx = pageHeightPx,
            marginPx = marginPx,
        )
    }

    private fun orientedFitDimensions(
        width: Int,
        height: Int,
        orientation: PdfPageOrientation,
    ): Pair<Int, Int> {
        val shortEdge = min(width, height)
        val longEdge = maxOf(width, height)
        return when (orientation) {
            PdfPageOrientation.PORTRAIT -> shortEdge to longEdge
            PdfPageOrientation.LANDSCAPE -> longEdge to shortEdge
        }
    }

    private data class ExportInput(
        val documentTitle: String,
        val fileStem: String,
        val pageImagePaths: List<String>,
    )

    private data class PageLayout(
        val pageWidthPx: Int,
        val pageHeightPx: Int,
        val marginPx: Int,
    ) {
        val contentWidthPx: Int
            get() = (pageWidthPx - (marginPx * 2)).coerceAtLeast(1)

        val contentHeightPx: Int
            get() = (pageHeightPx - (marginPx * 2)).coerceAtLeast(1)
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
