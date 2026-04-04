package `in`.c1ph3rj.scanly.domain.repository

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import `in`.c1ph3rj.scanly.domain.model.PageCaptureDraft
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import kotlinx.coroutines.flow.Flow

interface PageRepository {
    fun observePages(documentId: String): Flow<List<ScanPage>>

    fun observePage(pageId: String): Flow<ScanPage?>

    suspend fun prepareCapture(documentId: String): ScanlyResult<PageCaptureDraft>

    suspend fun prepareReplacementCapture(pageId: String): ScanlyResult<PageCaptureDraft>

    suspend fun finalizeCapture(draft: PageCaptureDraft): ScanlyResult<String>

    suspend fun movePage(
        pageId: String,
        targetIndex: Int,
    ): ScanlyResult<Unit>

    suspend fun deletePage(pageId: String): ScanlyResult<Unit>

    suspend fun updatePageEdits(
        pageId: String,
        cropQuad: DocumentCornerQuad,
        rotationDegrees: Int,
        filterPreset: PageFilterPreset,
        applyFilterToAllPages: Boolean,
    ): ScanlyResult<Unit>
}
