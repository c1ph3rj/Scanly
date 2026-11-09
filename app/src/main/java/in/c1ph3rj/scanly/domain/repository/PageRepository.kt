package `in`.c1ph3rj.scanly.domain.repository

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.domain.model.PageFilterAdjustments
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import `in`.c1ph3rj.scanly.domain.model.PageCaptureDraft
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import `in`.c1ph3rj.scanly.domain.model.TrashedPage
import kotlinx.coroutines.flow.Flow

interface PageRepository {
    fun observePages(documentId: String): Flow<List<ScanPage>>

    fun observePage(pageId: String): Flow<ScanPage?>

    suspend fun prepareCapture(documentId: String): ScanlyResult<PageCaptureDraft>

    suspend fun prepareReplacementCapture(pageId: String): ScanlyResult<PageCaptureDraft>

    /**
     * Saves a captured (or imported) raw image as a page. With [flatPage] set, the image is used
     * as-is (no edge detection or crop) and gets that filter; otherwise the camera pipeline runs.
     */
    suspend fun finalizeCapture(
        draft: PageCaptureDraft,
        flatPage: FlatPageSetup? = null,
    ): ScanlyResult<String>

    suspend fun movePage(
        pageId: String,
        targetIndex: Int,
    ): ScanlyResult<Unit>

    /** Permanently deletes a live page and its files. */
    suspend fun deletePage(pageId: String): ScanlyResult<Unit>

    /** Moves a page to Recently deleted; the rest of the document closes the gap. */
    suspend fun movePageToTrash(pageId: String): ScanlyResult<Unit>

    /** Puts a trashed page back at the position it was deleted from (or the end, if shorter now). */
    suspend fun restorePage(pageId: String): ScanlyResult<Unit>

    fun observeTrashedPages(): Flow<List<TrashedPage>>

    /** Permanently deletes a trashed page and its files. */
    suspend fun deletePageForever(pageId: String): ScanlyResult<Unit>

    /** Permanently deletes every trashed page. @return number of pages removed */
    suspend fun emptyTrashedPages(): ScanlyResult<Int>

    /** Permanently deletes pages trashed at or before [cutoffMillis]. @return number removed */
    suspend fun purgeTrashedPagesBefore(cutoffMillis: Long): ScanlyResult<Int>

    suspend fun updatePageEdits(
        pageId: String,
        cropQuad: DocumentCornerQuad,
        rotationDegrees: Int,
        filterPreset: PageFilterPreset,
        filterAdjustments: PageFilterAdjustments = PageFilterAdjustments.Default,
        applyFilterToAllPages: Boolean,
    ): ScanlyResult<Unit>
}

/** A page that needs no straightening, such as one rendered from a PDF. */
data class FlatPageSetup(
    val filterPreset: PageFilterPreset,
)
