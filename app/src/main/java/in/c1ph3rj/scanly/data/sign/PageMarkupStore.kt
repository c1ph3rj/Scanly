package `in`.c1ph3rj.scanly.data.sign

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import `in`.c1ph3rj.scanly.core.ui.ThumbnailCache
import `in`.c1ph3rj.scanly.data.local.db.dao.PageMarkupDao
import `in`.c1ph3rj.scanly.data.local.db.entity.PageMarkupEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.ScanPageEntity
import `in`.c1ph3rj.scanly.data.storage.DocumentStorageManager
import `in`.c1ph3rj.scanly.domain.model.SignMark
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Burns sign marks into a page's processed image while keeping it editable.
 *
 * Layout inside `documents/{id}/`:
 * - `processed/page_NNN.jpg` — what every screen and export reads; the composite once signed.
 * - `processed/page_NNN.base.jpg` — the clean image the marks are drawn onto.
 * - `markups/{markId}.png` — private copies of placed signature images, so deleting a saved
 *   signature never changes a signed page.
 *
 * Every image write goes to a sibling `.tmp` file first and is swapped in afterwards, and the
 * markup row is saved before the composite replaces the page image, so a page that carries marks
 * always has a row (and base) to edit them from.
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
     * stamped onto it again, so signatures survive page edits. Boxes are re-fitted when the page
     * aspect changed (a rotate swaps width and height), so marks keep their proportions.
     */
    suspend fun reapplyAfterReprocess(page: ScanPageEntity) {
        val markup = markupDao.getMarkup(page.id) ?: return
        val processedPath = page.processedImagePath ?: return
        val basePath = basePathFor(processedPath)
        // A row that cannot be read is left alone: the fresh image stays unsigned, nothing is lost.
        // The base still tracks the new clean image so a later sign never stamps onto a stale one.
        val decoded = SignMarkCodec.decodeOrNull(markup.marksJson) ?: run {
            copyReplacing(File(processedPath), File(basePath))
            if (markup.baseImagePath != basePath) {
                markupDao.upsertMarkup(markup.copy(baseImagePath = basePath, updatedAtMillis = System.currentTimeMillis()))
            }
            return
        }
        if (decoded.isEmpty()) {
            markupDao.deleteMarkup(page.id)
            File(basePath).delete()
            return
        }
        // Read before the old base is overwritten below.
        val oldAspect = listOf(basePath, markup.baseImagePath).firstNotNullOfOrNull { imageAspect(it) }
        val newAspect = imageAspect(processedPath)
        val aspectChanged = oldAspect != null && newAspect != null &&
            abs(newAspect / oldAspect - 1f) > AspectTolerance
        val marks = if (aspectChanged) {
            decoded.map { it.withBox(it.box.reshapedForPageAspect(oldAspect!!, newAspect!!)) }
        } else {
            decoded
        }
        copyReplacing(File(processedPath), File(basePath))
        val composite = renderToTemp(basePath, processedPath, marks)
        try {
            markupDao.upsertMarkup(
                markup.copy(
                    marksJson = if (aspectChanged) SignMarkCodec.encode(marks) else markup.marksJson,
                    baseImagePath = basePath,
                    updatedAtMillis = System.currentTimeMillis(),
                ),
            )
            replaceFile(composite, File(processedPath))
        } finally {
            composite.delete()
        }
        refreshThumbnail(processedPath, page.thumbnailPath)
    }

    /**
     * Sets [marks] on [page] and re-stamps its image. Returns true when the page image changed.
     * An empty list restores the clean image and forgets the markup.
     */
    suspend fun apply(page: ScanPageEntity, marks: List<SignMark>): Boolean {
        val processedPath = page.processedImagePath ?: error("This page is still processing. Try again in a moment.")
        val existing = markupDao.getMarkup(page.id)
        // Null when there is no row or it cannot be read; an unreadable row is never treated as empty.
        val existingMarks = existing?.let { SignMarkCodec.decodeOrNull(it.marksJson) }
        val markupsDir = File(documentRootOf(processedPath), MARKUPS_DIRECTORY)

        if (marks.isEmpty()) {
            if (existing == null) return false
            val base = File(existing.baseImagePath)
            if (base.isFile) {
                copyReplacing(base, File(processedPath))
            }
            markupDao.deleteMarkup(page.id)
            base.delete()
            deleteUnusedImages(existingMarks.orEmpty(), emptyList(), markupsDir)
            refreshThumbnail(processedPath, page.thumbnailPath)
            return true
        }

        val ownedMarks = marks.map { mark -> mark.ownedBy(markupsDir) }
        // pageIndex is re-keyed to the page's position by the caller, so a reorder is not a change.
        if (existingMarks != null && ownedMarks.withoutPageIndex() == existingMarks.withoutPageIndex()) return false

        // An unreadable row still owns its base: reuse it rather than rebasing onto a signed image.
        val existingBase = existing?.baseImagePath?.takeIf { File(it).isFile }
        val basePath = existingBase ?: basePathFor(processedPath)
        var rowSaved = false
        try {
            if (existingBase == null) copyReplacing(File(processedPath), File(basePath))
            val composite = renderToTemp(basePath, processedPath, ownedMarks)
            try {
                // The row goes in first: once marks are burnt into processed/, a row always exists.
                markupDao.upsertMarkup(
                    PageMarkupEntity(
                        pageId = page.id,
                        marksJson = SignMarkCodec.encode(ownedMarks),
                        baseImagePath = basePath,
                        updatedAtMillis = System.currentTimeMillis(),
                    ),
                )
                rowSaved = true
                replaceFile(composite, File(processedPath))
            } finally {
                composite.delete()
            }
        } catch (error: Throwable) {
            runCatching {
                withContext(NonCancellable) {
                    if (existing == null) {
                        // The page had no markup: forget the row and the base made for this attempt.
                        markupDao.deleteMarkup(page.id)
                        File(basePath).delete()
                    } else if (rowSaved) {
                        // The image still shows the previous marks, so the previous row stays true.
                        markupDao.upsertMarkup(existing)
                    }
                }
            }
            throw error
        }
        refreshThumbnail(processedPath, page.thumbnailPath)
        deleteUnusedImages(existingMarks.orEmpty(), ownedMarks, markupsDir)
        return true
    }

    /** Removes a deleted page's base image and private signature copies. */
    fun deleteAssets(markup: PageMarkupEntity?, processedPath: String?) {
        markup ?: return
        File(markup.baseImagePath).delete()
        val markupsDir = processedPath?.let { File(documentRootOf(it), MARKUPS_DIRECTORY) } ?: return
        deleteUnusedImages(SignMarkCodec.decode(markup.marksJson), emptyList(), markupsDir)
    }

    /**
     * Draws [marks] onto the base image and writes the JPEG next to [processedPath] as a `.tmp`
     * file. The caller swaps it in with [replaceFile] and deletes the temp file afterwards.
     */
    private fun renderToTemp(basePath: String, processedPath: String, marks: List<SignMark>): File {
        val temp = File("$processedPath$TEMP_SUFFIX")
        try {
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
                FileOutputStream(temp).use { output ->
                    check(composite.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                        "Could not save the signed page."
                    }
                    output.fd.sync()
                }
            } finally {
                composite.recycle()
            }
        } catch (error: Throwable) {
            temp.delete()
            throw error
        }
        return temp
    }

    /** Moves [temp] over [target]; falls back to a copy when the rename is refused. */
    private fun replaceFile(temp: File, target: File) {
        if (!temp.renameTo(target)) temp.copyTo(target, overwrite = true)
    }

    /** Copies [source] over [target] through a temp file, so [target] is never left half-written. */
    private fun copyReplacing(source: File, target: File) {
        val temp = File("${target.path}$TEMP_SUFFIX")
        try {
            source.copyTo(temp, overwrite = true)
            replaceFile(temp, target)
        } finally {
            temp.delete()
        }
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

        /** True when [imagePath] is a private copy a document already owns (see [ownedBy]). */
        fun isOwnedMarkupImage(imagePath: String): Boolean =
            File(imagePath).parentFile?.name == MARKUPS_DIRECTORY

        private const val JPEG_QUALITY = 94
        private const val TEMP_SUFFIX = ".tmp"

        /** Relative page-aspect change below which marks keep their boxes as they are. */
        private const val AspectTolerance = 0.01f

        /** `processed/page_001.jpg` → `processed/page_001.base.jpg`. */
        fun basePathFor(processedPath: String): String {
            val file = File(processedPath)
            return File(file.parentFile, "${file.nameWithoutExtension}.base.jpg").absolutePath
        }

        private fun documentRootOf(processedPath: String): File =
            File(processedPath).parentFile?.parentFile ?: error("Invalid page image path.")

        /** Width / height of the image at [path] without decoding it, or null if it cannot be read. */
        private fun imageAspect(path: String): Float? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            return bounds.outWidth.toFloat() / bounds.outHeight
        }

        /** Marks with [SignMark.pageIndex] zeroed, to compare content regardless of page position. */
        private fun List<SignMark>.withoutPageIndex(): List<SignMark> = map { mark ->
            when (mark) {
                is SignMark.Image -> mark.copy(pageIndex = 0)
                is SignMark.Text -> mark.copy(pageIndex = 0)
                is SignMark.Date -> mark.copy(pageIndex = 0)
                is SignMark.Checkbox -> mark.copy(pageIndex = 0)
            }
        }
    }
}
