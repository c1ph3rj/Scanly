package `in`.c1ph3rj.scanly.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPrivateDocumentStorageManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val coverArtFactory: DocumentCoverArtFactory,
    private val dispatchers: ScanlyDispatchers,
) : DocumentStorageManager {

    override suspend fun createDocumentScaffold(
        documentId: String,
        title: String,
    ): DocumentFileLayout = withContext(dispatchers.io) {
        val rootDirectory = ensureDirectory(documentRoot(documentId))
        ensureDirectory(File(rootDirectory, RAW_DIRECTORY))
        ensureDirectory(File(rootDirectory, PROCESSED_DIRECTORY))
        val thumbnailDirectory = ensureDirectory(File(rootDirectory, THUMBNAILS_DIRECTORY))
        val coverFile = File(thumbnailDirectory, COVER_FILE_NAME)
        writeCoverFile(coverFile, title)

        DocumentFileLayout(
            rootDirectoryPath = rootDirectory.absolutePath,
            coverThumbnailPath = coverFile.absolutePath,
        )
    }

    override suspend fun refreshDocumentCover(
        documentId: String,
        title: String,
    ): String = withContext(dispatchers.io) {
        val rootDirectory = ensureDirectory(documentRoot(documentId))
        val thumbnailDirectory = ensureDirectory(File(rootDirectory, THUMBNAILS_DIRECTORY))
        val coverFile = File(thumbnailDirectory, COVER_FILE_NAME)
        writeCoverFile(coverFile, title)
        coverFile.absolutePath
    }

    override suspend fun createPageCaptureDraft(
        documentId: String,
        pageIndex: Int,
    ): PageCaptureStorageDraft = withContext(dispatchers.io) {
        val rootDirectory = ensureDirectory(documentRoot(documentId))
        val rawDirectory = ensureDirectory(File(rootDirectory, RAW_DIRECTORY))
        val processedDirectory = ensureDirectory(File(rootDirectory, PROCESSED_DIRECTORY))
        val thumbnailDirectory = ensureDirectory(File(rootDirectory, THUMBNAILS_DIRECTORY))
        val fileName = pageFileName(pageIndex)

        PageCaptureStorageDraft(
            rawImagePath = File(rawDirectory, fileName).absolutePath,
            processedImagePath = File(processedDirectory, fileName).absolutePath,
            thumbnailPath = File(thumbnailDirectory, fileName).absolutePath,
        )
    }

    override suspend fun generatePageThumbnail(
        rawImagePath: String,
        thumbnailPath: String,
    ): PageThumbnailResult = withContext(dispatchers.io) {
        val rawFile = File(rawImagePath)
        if (!rawFile.exists() || rawFile.length() <= 0L) {
            error("Captured image missing at $rawImagePath.")
        }

        val rotationDegrees = ExifInterface(rawImagePath).rotationDegrees
        val bitmap = decodeSampledBitmap(rawImagePath, THUMBNAIL_MAX_DIMENSION, THUMBNAIL_MAX_DIMENSION)
            ?: error("Could not decode captured image at $rawImagePath.")
        val rotatedBitmap = rotateBitmapIfNeeded(bitmap, rotationDegrees)
        val outputFile = File(thumbnailPath)
        outputFile.parentFile?.let(::ensureDirectory)

        FileOutputStream(outputFile).use { outputStream ->
            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_JPEG_QUALITY, outputStream)
            outputStream.flush()
        }

        if (rotatedBitmap !== bitmap) {
            rotatedBitmap.recycle()
        }
        bitmap.recycle()

        PageThumbnailResult(
            thumbnailPath = outputFile.absolutePath,
            rotationDegrees = rotationDegrees,
        )
    }

    override suspend fun deleteDocumentStorage(documentId: String) {
        withContext(dispatchers.io) {
            documentRoot(documentId).deleteRecursively()
        }
    }

    override suspend fun deletePageAssets(
        rawImagePath: String?,
        processedImagePath: String?,
        thumbnailPath: String?,
    ) {
        withContext(dispatchers.io) {
            listOf(rawImagePath, processedImagePath, thumbnailPath)
                .filterNotNull()
                .map(::File)
                .distinctBy { file -> file.absolutePath }
                .forEach { file ->
                    if (file.exists()) {
                        file.delete()
                    }
                }
        }
    }

    override suspend fun clearAllDocumentStorage() {
        withContext(dispatchers.io) {
            File(context.filesDir, DOCUMENTS_DIRECTORY).deleteRecursively()
        }
    }

    override suspend fun documentStorageUsageBytes(): Long = withContext(dispatchers.io) {
        directorySize(File(context.filesDir, DOCUMENTS_DIRECTORY))
    }

    override suspend fun discoverStoredDocuments(): List<StoredDocumentSnapshot> =
        withContext(dispatchers.io) {
            emptyList()
        }

    override fun isLegacyPrivateDocumentPath(path: String?): Boolean {
        if (path.isNullOrBlank()) {
            return false
        }
        val legacyRoot = File(context.filesDir, DOCUMENTS_DIRECTORY).absoluteFile
        return runCatching {
            File(path).absoluteFile.toPath().startsWith(legacyRoot.toPath())
        }.getOrDefault(false)
    }

    override suspend fun deleteLegacyPrivateDocumentStorage(documentId: String) {
        withContext(dispatchers.io) {
            documentRoot(documentId).deleteRecursively()
        }
    }

    private fun documentRoot(documentId: String): File =
        File(context.filesDir, "$DOCUMENTS_DIRECTORY/$documentId")

    private fun directorySize(directory: File): Long {
        if (!directory.exists()) {
            return 0L
        }
        return directory.walkTopDown()
            .filter { file -> file.isFile }
            .sumOf { file -> file.length() }
    }

    private fun ensureDirectory(directory: File): File {
        if (!directory.exists() && !directory.mkdirs()) {
            error("Could not create ${directory.absolutePath}.")
        }
        return directory
    }

    private fun writeCoverFile(
        outputFile: File,
        title: String,
    ) {
        outputFile.parentFile?.let(::ensureDirectory)
        val coverBitmap = coverArtFactory.create(title)
        FileOutputStream(outputFile).use { outputStream ->
            coverBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            outputStream.flush()
        }
        coverBitmap.recycle()
    }

    private fun decodeSampledBitmap(
        path: String,
        targetWidth: Int,
        targetHeight: Int,
    ): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, boundsOptions)

        val sampleOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(
                sourceWidth = boundsOptions.outWidth,
                sourceHeight = boundsOptions.outHeight,
                targetWidth = targetWidth,
                targetHeight = targetHeight,
            )
        }
        return BitmapFactory.decodeFile(path, sampleOptions)
    }

    private fun calculateInSampleSize(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): Int {
        var inSampleSize = 1
        if (sourceHeight > targetHeight || sourceWidth > targetWidth) {
            var halfHeight = sourceHeight / 2
            var halfWidth = sourceWidth / 2

            while (halfHeight / inSampleSize >= targetHeight && halfWidth / inSampleSize >= targetWidth) {
                inSampleSize *= 2
                halfHeight = sourceHeight / 2
                halfWidth = sourceWidth / 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun rotateBitmapIfNeeded(
        bitmap: Bitmap,
        rotationDegrees: Int,
    ): Bitmap {
        if (rotationDegrees == 0) {
            return bitmap
        }

        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun pageFileName(pageIndex: Int): String = "page_${(pageIndex + 1).toString().padStart(3, '0')}.jpg"

    private companion object {
        const val DOCUMENTS_DIRECTORY = "documents"
        const val RAW_DIRECTORY = "raw"
        const val PROCESSED_DIRECTORY = "processed"
        const val THUMBNAILS_DIRECTORY = "thumbs"
        const val COVER_FILE_NAME = "cover.jpg"
        const val JPEG_QUALITY = 92
        const val THUMBNAIL_JPEG_QUALITY = 90
        const val THUMBNAIL_MAX_DIMENSION = 1_024
    }
}
