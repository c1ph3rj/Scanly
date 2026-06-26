package `in`.c1ph3rj.scanly.data.storage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedMediaDocumentStorageManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val coverArtFactory: DocumentCoverArtFactory,
    private val dispatchers: ScanlyDispatchers,
) : DocumentStorageManager {

    private val mediaCollection: Uri
        get() = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    override suspend fun createDocumentScaffold(
        documentId: String,
        title: String,
    ): DocumentFileLayout = withContext(dispatchers.io) {
        val coverPath = createOrReplaceImagePath(
            documentId = documentId,
            directoryName = PersistentDocumentLayout.THUMBNAILS_DIRECTORY,
            displayName = PersistentDocumentLayout.COVER_FILE_NAME,
        )
        writeCoverFile(coverPath, title)

        DocumentFileLayout(
            rootDirectoryPath = documentRootPathFrom(coverPath, documentId),
            coverThumbnailPath = coverPath,
        )
    }

    override suspend fun refreshDocumentCover(
        documentId: String,
        title: String,
    ): String = withContext(dispatchers.io) {
        val coverPath = createOrReplaceImagePath(
            documentId = documentId,
            directoryName = PersistentDocumentLayout.THUMBNAILS_DIRECTORY,
            displayName = PersistentDocumentLayout.COVER_FILE_NAME,
        )
        writeCoverFile(coverPath, title)
        coverPath
    }

    override suspend fun createPageCaptureDraft(
        documentId: String,
        pageIndex: Int,
    ): PageCaptureStorageDraft = withContext(dispatchers.io) {
        val fileName = PersistentDocumentLayout.pageFileName(pageIndex)
        PageCaptureStorageDraft(
            rawImagePath = createOrReplaceImagePath(
                documentId = documentId,
                directoryName = PersistentDocumentLayout.RAW_DIRECTORY,
                displayName = fileName,
            ),
            processedImagePath = createOrReplaceImagePath(
                documentId = documentId,
                directoryName = PersistentDocumentLayout.PROCESSED_DIRECTORY,
                displayName = fileName,
            ),
            thumbnailPath = createOrReplaceImagePath(
                documentId = documentId,
                directoryName = PersistentDocumentLayout.THUMBNAILS_DIRECTORY,
                displayName = fileName,
            ),
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
        outputFile.parentFile?.mkdirs()

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
            deleteMediaByRelativePathPrefix("${PersistentDocumentLayout.PUBLIC_ROOT_DIRECTORY}/$documentId/")
            deleteLegacyPrivateDocumentRoot(documentId)
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
                .distinct()
                .forEach(::deleteMediaByPath)
        }
    }

    override suspend fun clearAllDocumentStorage() {
        withContext(dispatchers.io) {
            deleteMediaByRelativePathPrefix("${PersistentDocumentLayout.PUBLIC_ROOT_DIRECTORY}/")
            legacyPrivateDocumentsRoot().deleteRecursively()
        }
    }

    override suspend fun documentStorageUsageBytes(): Long = withContext(dispatchers.io) {
        mediaSizeBytes() + directorySize(legacyPrivateDocumentsRoot())
    }

    override suspend fun discoverStoredDocuments(): List<StoredDocumentSnapshot> =
        withContext(dispatchers.io) {
            val documents = linkedMapOf<String, MutableStoredDocument>()
            queryScanlyMediaRows().forEach { row ->
                val asset = PersistentDocumentLayout.parseAsset(
                    relativePath = row.relativePath,
                    displayName = row.displayName,
                ) ?: return@forEach

                val document = documents.getOrPut(asset.documentId) {
                    MutableStoredDocument(asset.documentId)
                }
                document.updatedAtMillis = maxOf(document.updatedAtMillis, row.updatedAtMillis)
                document.createdAtMillis = minOf(document.createdAtMillis, row.updatedAtMillis)
                document.rootDirectoryPath = document.rootDirectoryPath
                    ?: row.filePath?.let { path -> documentRootPathFrom(path, asset.documentId) }

                if (asset.isCover) {
                    document.coverThumbnailPath = row.filePath
                    return@forEach
                }

                val pageIndex = asset.pageIndex ?: return@forEach
                val page = document.pages.getOrPut(pageIndex) {
                    MutableStoredPage(pageIndex)
                }
                page.updatedAtMillis = maxOf(page.updatedAtMillis, row.updatedAtMillis)
                when (asset.directoryName) {
                    PersistentDocumentLayout.RAW_DIRECTORY -> page.rawImagePath = row.filePath
                    PersistentDocumentLayout.PROCESSED_DIRECTORY -> page.processedImagePath = row.filePath
                    PersistentDocumentLayout.THUMBNAILS_DIRECTORY -> page.thumbnailPath = row.filePath
                }
            }

            documents.values.mapNotNull { document ->
                val pages = document.pages.values
                    .sortedBy { page -> page.pageIndex }
                    .map { page ->
                        StoredPageSnapshot(
                            pageIndex = page.pageIndex,
                            rawImagePath = page.rawImagePath,
                            processedImagePath = page.processedImagePath,
                            thumbnailPath = page.thumbnailPath,
                            updatedAtMillis = page.updatedAtMillis,
                        )
                    }
                if (pages.isEmpty() && document.coverThumbnailPath == null) {
                    return@mapNotNull null
                }
                StoredDocumentSnapshot(
                    id = document.id,
                    rootDirectoryPath = document.rootDirectoryPath
                        ?: "${PersistentDocumentLayout.PUBLIC_ROOT_DIRECTORY}/${document.id}",
                    coverThumbnailPath = document.coverThumbnailPath ?: pages.firstOrNull()?.thumbnailPath,
                    pages = pages,
                    createdAtMillis = document.createdAtMillis.takeUnless { it == Long.MAX_VALUE }
                        ?: document.updatedAtMillis,
                    updatedAtMillis = document.updatedAtMillis,
                )
            }
        }

    override fun isLegacyPrivateDocumentPath(path: String?): Boolean {
        if (path.isNullOrBlank()) {
            return false
        }
        val legacyRoot = legacyPrivateDocumentsRoot().absoluteFile
        return runCatching {
            File(path).absoluteFile.toPath().startsWith(legacyRoot.toPath())
        }.getOrDefault(false)
    }

    override suspend fun deleteLegacyPrivateDocumentStorage(documentId: String) {
        withContext(dispatchers.io) {
            deleteLegacyPrivateDocumentRoot(documentId)
        }
    }

    @Suppress("DEPRECATION")
    private fun createOrReplaceImagePath(
        documentId: String,
        directoryName: String,
        displayName: String,
    ): String {
        val relativePath = PersistentDocumentLayout.documentRelativePath(documentId, directoryName)
        deleteMediaByRelativePathAndName(relativePath, displayName)

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, JPEG_MIME_TYPE)
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
        }
        val uri = context.contentResolver.insert(mediaCollection, values)
            ?: error("Could not create persistent media row for $displayName.")
        val path = queryDataPath(uri)
            ?: run {
                context.contentResolver.delete(uri, null, null)
                error("Could not resolve filesystem path for $displayName.")
            }
        File(path).parentFile?.mkdirs()
        return path
    }

    private fun writeCoverFile(
        outputPath: String,
        title: String,
    ) {
        val outputFile = File(outputPath)
        outputFile.parentFile?.mkdirs()
        val coverBitmap = coverArtFactory.create(title)
        FileOutputStream(outputFile).use { outputStream ->
            coverBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
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

    @Suppress("DEPRECATION")
    private fun queryDataPath(uri: Uri): String? {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) {
                return@use null
            }
            cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
        }
    }

    private fun deleteMediaByRelativePathAndName(
        relativePath: String,
        displayName: String,
    ) {
        context.contentResolver.delete(
            mediaCollection,
            "${MediaStore.Images.Media.RELATIVE_PATH}=? AND ${MediaStore.Images.Media.DISPLAY_NAME}=?",
            arrayOf(relativePath, displayName),
        )
    }

    private fun deleteMediaByRelativePathPrefix(relativePathPrefix: String) {
        context.contentResolver.delete(
            mediaCollection,
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
            arrayOf("$relativePathPrefix%"),
        )
    }

    @Suppress("DEPRECATION")
    private fun deleteMediaByPath(path: String) {
        context.contentResolver.delete(
            mediaCollection,
            "${MediaStore.Images.Media.DATA}=?",
            arrayOf(path),
        )
        File(path).delete()
    }

    @Suppress("DEPRECATION")
    private fun queryScanlyMediaRows(): List<MediaRow> {
        val projection = arrayOf(
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_MODIFIED,
        )
        val rows = mutableListOf<MediaRow>()
        context.contentResolver.query(
            mediaCollection,
            projection,
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
            arrayOf("${PersistentDocumentLayout.PUBLIC_ROOT_DIRECTORY}/%"),
            "${MediaStore.Images.Media.RELATIVE_PATH} ASC, ${MediaStore.Images.Media.DISPLAY_NAME} ASC",
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val relativePathIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            while (cursor.moveToNext()) {
                rows += MediaRow(
                    displayName = cursor.getString(nameIndex).orEmpty(),
                    relativePath = cursor.getString(relativePathIndex).orEmpty(),
                    filePath = cursor.getString(dataIndex),
                    updatedAtMillis = cursor.getLong(modifiedIndex).takeIf { it > 0L }
                        ?.times(1_000L)
                        ?: System.currentTimeMillis(),
                )
            }
        }
        return rows
    }

    private fun mediaSizeBytes(): Long {
        val projection = arrayOf(MediaStore.Images.Media.SIZE)
        var totalBytes = 0L
        context.contentResolver.query(
            mediaCollection,
            projection,
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
            arrayOf("${PersistentDocumentLayout.PUBLIC_ROOT_DIRECTORY}/%"),
            null,
        )?.use { cursor ->
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            while (cursor.moveToNext()) {
                totalBytes += cursor.getLong(sizeIndex).coerceAtLeast(0L)
            }
        }
        return totalBytes
    }

    private fun documentRootPathFrom(
        assetPath: String,
        documentId: String,
    ): String {
        return File(assetPath).parentFile?.parentFile?.absolutePath
            ?: "${PersistentDocumentLayout.PUBLIC_ROOT_DIRECTORY}/$documentId"
    }

    private fun legacyPrivateDocumentsRoot(): File =
        File(context.filesDir, LEGACY_DOCUMENTS_DIRECTORY)

    private fun deleteLegacyPrivateDocumentRoot(documentId: String) {
        File(legacyPrivateDocumentsRoot(), documentId).deleteRecursively()
    }

    private fun directorySize(directory: File): Long {
        if (!directory.exists()) {
            return 0L
        }
        return directory.walkTopDown()
            .filter { file -> file.isFile }
            .sumOf { file -> file.length() }
    }

    private data class MediaRow(
        val displayName: String,
        val relativePath: String,
        val filePath: String?,
        val updatedAtMillis: Long,
    )

    private data class MutableStoredDocument(
        val id: String,
        var rootDirectoryPath: String? = null,
        var coverThumbnailPath: String? = null,
        var createdAtMillis: Long = Long.MAX_VALUE,
        var updatedAtMillis: Long = 0L,
        val pages: MutableMap<Int, MutableStoredPage> = mutableMapOf(),
    )

    private data class MutableStoredPage(
        val pageIndex: Int,
        var rawImagePath: String? = null,
        var processedImagePath: String? = null,
        var thumbnailPath: String? = null,
        var updatedAtMillis: Long = 0L,
    )

    private companion object {
        const val LEGACY_DOCUMENTS_DIRECTORY = "documents"
        const val JPEG_MIME_TYPE = "image/jpeg"
        const val JPEG_QUALITY = 92
        const val THUMBNAIL_JPEG_QUALITY = 90
        const val THUMBNAIL_MAX_DIMENSION = 1_024
    }
}
