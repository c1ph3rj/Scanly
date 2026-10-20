package `in`.c1ph3rj.scanly.data.sign

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import `in`.c1ph3rj.scanly.core.ui.ThumbnailCache
import `in`.c1ph3rj.scanly.data.local.db.dao.PageMarkupDao
import `in`.c1ph3rj.scanly.data.local.db.entity.PageMarkupEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.ScanPageEntity
import `in`.c1ph3rj.scanly.data.storage.DocumentStorageManager
import `in`.c1ph3rj.scanly.domain.model.SignMark
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Burns sign marks into a page's processed image while keeping it editable.
 *
 * Layout inside `documents/{id}/`:
 * - `processed/page_NNN.jpg` — what every screen and export reads; the composite once signed.
 * - `processed/page_NNN.base.jpg` — the clean image the marks are drawn onto.
 * - `markups/{markId}.png` — private copies of placed signature images, so deleting a saved
 *   signature never changes a signed page.
 *
 * Callers run on an IO dispatcher inside the library mutation lock.
 */
@Singleton
class PageMarkupStore @Inject constructor(
    private val markupDao: PageMarkupDao,
    private val compositor: PageMarkupCompositor,
    private val storageManager: DocumentStorageManager,
    private val thumbnailCache: ThumbnailCache,
) {

    suspend fun markupFor(pageId: String): PageMarkupEntity? = markupDao.getMarkup(pageId)

    /**
     * Called right after the page pipeline rewrote [page]'s processed image from raw (crop,
     * filter, rotate or retake): that fresh output becomes the new clean base and the marks are
     * stamped onto it again, so signatures survive page edits.
     */
    suspend fun reapplyAfterReprocess(page: ScanPageEntity) {
        val markup = markupDao.getMarkup(page.id) ?: return
        val processedPath = page.processedImagePath ?: return
        val marks = SignMarkCodec.decode(markup.marksJson)
        val basePath = basePathFor(processedPath)
        File(processedPath).copyTo(File(basePath), overwrite = true)
        if (marks.isEmpty()) {
            markupDao.deleteMarkup(page.id)
            File(basePath).delete()
            return
        }
        renderComposite(basePath, processedPath, page.thumbnailPath, marks)
        markupDao.upsertMarkup(markup.copy(baseImagePath = basePath, updatedAtMillis = System.currentTimeMillis()))
    }

    /**
     * Sets [marks] on [page] and re-stamps its image. Returns true when the page image changed.
     * An empty list restores the clean image and forgets the markup.
     */
    suspend fun apply(page: ScanPageEntity, marks: List<SignMark>): Boolean {
        val processedPath = page.processedImagePath ?: error("This page is still processing. Try again in a moment.")
        val existing = markupDao.getMarkup(page.id)
        val existingMarks = existing?.let { SignMarkCodec.decode(it.marksJson) }.orEmpty()
        val markupsDir = File(documentRootOf(processedPath), MARKUPS_DIRECTORY)

        if (marks.isEmpty()) {
            if (existing == null) return false
            val base = File(existing.baseImagePath)
            if (base.isFile) {
                base.copyTo(File(processedPath), overwrite = true)
                base.delete()
            }
            markupDao.deleteMarkup(page.id)
            deleteUnusedImages(existingMarks, emptyList(), markupsDir)
            refreshThumbnail(processedPath, page.thumbnailPath)
            return true
        }

        val ownedMarks = marks.map { mark -> mark.ownedBy(markupsDir) }
        if (existing != null && ownedMarks == existingMarks) return false

        val basePath = existing?.baseImagePath?.takeIf { File(it).isFile } ?: basePathFor(processedPath).also {
            File(processedPath).copyTo(File(it), overwrite = true)
        }
        renderComposite(basePath, processedPath, page.thumbnailPath, ownedMarks)
        markupDao.upsertMarkup(
            PageMarkupEntity(
                pageId = page.id,
                marksJson = SignMarkCodec.encode(ownedMarks),
                baseImagePath = basePath,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
        deleteUnusedImages(existingMarks, ownedMarks, markupsDir)
        return true
    }

    /** Removes a deleted page's base image and private signature copies. */
    fun deleteAssets(markup: PageMarkupEntity?, processedPath: String?) {
        markup ?: return
        File(markup.baseImagePath).delete()
        val markupsDir = processedPath?.let { File(documentRootOf(it), MARKUPS_DIRECTORY) } ?: return
        deleteUnusedImages(SignMarkCodec.decode(markup.marksJson), emptyList(), markupsDir)
    }

    private suspend fun renderComposite(
        basePath: String,
        processedPath: String,
        thumbnailPath: String?,
        marks: List<SignMark>,
    ) {
        val base = BitmapFactory.decodeFile(
            basePath,
            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 },
        ) ?: error("Could not read the page image.")
        val composite = try {
            compositor.composite(base, marks)
        } finally {
            base.recycle()
        }
        try {
            FileOutputStream(processedPath).use { output ->
                check(composite.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                    "Could not save the signed page."
                }
            }
        } finally {
            composite.recycle()
        }
        refreshThumbnail(processedPath, thumbnailPath)
    }

    private suspend fun refreshThumbnail(processedPath: String, thumbnailPath: String?) {
        thumbnailCache.invalidate(processedPath)
        thumbnailPath ?: return
        storageManager.generatePageThumbnail(rawImagePath = processedPath, thumbnailPath = thumbnailPath)
        thumbnailCache.invalidate(thumbnailPath)
    }

    /** Copies an image mark's PNG into the document so the page owns it. */
    private fun SignMark.ownedBy(markupsDir: File): SignMark {
        if (this !is SignMark.Image) return this
        val source = File(imagePath)
        if (source.parentFile?.canonicalPath == markupsDir.canonicalPath) return this
        if (!markupsDir.exists() && !markupsDir.mkdirs()) error("Could not create signature storage.")
        require(source.isFile) { "A signature image is missing. Place it again." }
        val target = File(markupsDir, "$id.png")
        source.copyTo(target, overwrite = true)
        return copy(imagePath = target.absolutePath)
    }

    private fun deleteUnusedImages(old: List<SignMark>, current: List<SignMark>, markupsDir: File) {
        val keep = current.filterIsInstance<SignMark.Image>().map { File(it.imagePath).canonicalPath }.toSet()
        old.filterIsInstance<SignMark.Image>()
            .map { File(it.imagePath) }
            .filter { it.parentFile?.canonicalPath == markupsDir.canonicalPath && it.canonicalPath !in keep }
            .forEach { it.delete() }
    }

    companion object {
        private const val MARKUPS_DIRECTORY = "markups"
        private const val JPEG_QUALITY = 94

        /** `processed/page_001.jpg` → `processed/page_001.base.jpg`. */
        fun basePathFor(processedPath: String): String {
            val file = File(processedPath)
            return File(file.parentFile, "${file.nameWithoutExtension}.base.jpg").absolutePath
        }

        private fun documentRootOf(processedPath: String): File =
            File(processedPath).parentFile?.parentFile ?: error("Invalid page image path.")
    }
}
