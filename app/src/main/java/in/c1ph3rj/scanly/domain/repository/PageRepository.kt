package `in`.c1ph3rj.scanly.domain.repository

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.domain.model.PageFilterAdjustments
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import `in`.c1ph3rj.scanly.domain.model.PageCaptureDraft
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import `in`.c1ph3rj.scanly.domain.model.ScanMode
import `in`.c1ph3rj.scanly.domain.model.IdCardSide
import kotlinx.coroutines.flow.Flow

interface PageRepository {
    fun observePages(documentId: String): Flow<List<ScanPage>>

    fun observePage(pageId: String): Flow<ScanPage?>

    suspend fun prepareCapture(
        documentId: String,
        scanMode: ScanMode = ScanMode.DOCUMENT,
        idCardPairId: String? = null,
        idCardSide: IdCardSide? = null,
    ): ScanlyResult<PageCaptureDraft>

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
        filterAdjustments: PageFilterAdjustments = PageFilterAdjustments.Default,
        applyFilterToAllPages: Boolean,
    ): ScanlyResult<Unit>
}
