package `in`.c1ph3rj.scanly.feature.preview

import androidx.lifecycle.SavedStateHandle
import `in`.c1ph3rj.scanly.MainDispatcherRule
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.domain.model.NormalizedTextPoint
import `in`.c1ph3rj.scanly.domain.model.PageCaptureDraft
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import `in`.c1ph3rj.scanly.domain.model.PageProcessingState
import `in`.c1ph3rj.scanly.domain.model.RecognizedPageText
import `in`.c1ph3rj.scanly.domain.model.RecognizedTextToken
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import `in`.c1ph3rj.scanly.domain.repository.PageRepository
import `in`.c1ph3rj.scanly.domain.repository.PageTextRecognizer
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentPagesUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObservePageUseCase
import `in`.c1ph3rj.scanly.domain.usecase.RecognizePageTextUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PageImagePreviewViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun recognitionIsCachedUntilThePageRevisionChanges() = runTest {
        val pages = MutableStateFlow(listOf(page(updatedAtMillis = 1L)))
        val recognizer = FakePageTextRecognizer(ScanlyResult.Success(recognizedText()))
        val viewModel = viewModel(pages, recognizer)
        advanceUntilIdle()

        viewModel.toggleTextMode("page-1")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.textMode is PageTextModeState.Ready)
        assertEquals(1, recognizer.callCount)

        viewModel.selectTextRange(anchorIndex = 1, focusIndex = 0)
        assertEquals(
            0..1,
            (viewModel.uiState.value.textMode as PageTextModeState.Ready).selection,
        )

        viewModel.exitTextMode()
        viewModel.toggleTextMode("page-1")
        advanceUntilIdle()
        assertEquals(1, recognizer.callCount)

        pages.value = listOf(page(updatedAtMillis = 2L))
        advanceUntilIdle()
        assertEquals(PageTextModeState.Inactive, viewModel.uiState.value.textMode)

        viewModel.toggleTextMode("page-1")
        advanceUntilIdle()
        assertEquals(2, recognizer.callCount)
    }

    @Test
    fun emptyRecognitionCanBeRetriedWithoutUsingTheCachedEmptyResult() = runTest {
        val pages = MutableStateFlow(listOf(page()))
        val recognizer = FakePageTextRecognizer(ScanlyResult.Success(RecognizedPageText(emptyList())))
        val viewModel = viewModel(pages, recognizer)
        advanceUntilIdle()

        viewModel.toggleTextMode("page-1")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.textMode is PageTextModeState.Empty)

        recognizer.result = ScanlyResult.Success(recognizedText())
        viewModel.retryTextRecognition()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.textMode is PageTextModeState.Ready)
        assertEquals(2, recognizer.callCount)
    }

    @Test
    fun recognitionFailureProducesRetryableErrorState() = runTest {
        val pages = MutableStateFlow(listOf(page()))
        val recognizer = FakePageTextRecognizer(
            ScanlyResult.Failure(ScanlyError("Recognition unavailable")),
        )
        val viewModel = viewModel(pages, recognizer)
        advanceUntilIdle()

        viewModel.toggleTextMode("page-1")
        advanceUntilIdle()

        val error = viewModel.uiState.value.textMode as PageTextModeState.Error
        assertEquals("Recognition unavailable", error.message)
    }

    private fun viewModel(
        pages: MutableStateFlow<List<ScanPage>>,
        recognizer: PageTextRecognizer,
    ): PageImagePreviewViewModel {
        val repository = FakePageRepository(pages)
        return PageImagePreviewViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(PageImagePreviewDestination.pageIdArgument to "page-1"),
            ),
            observePageUseCase = ObservePageUseCase(repository),
            observeDocumentPagesUseCase = ObserveDocumentPagesUseCase(repository),
            recognizePageTextUseCase = RecognizePageTextUseCase(recognizer),
        )
    }

    private fun page(updatedAtMillis: Long = 1L) = ScanPage(
        id = "page-1",
        documentId = "document-1",
        pageIndex = 0,
        rawImagePath = null,
        processedImagePath = "processed.jpg",
        thumbnailPath = null,
        rotationDegrees = 0,
        cropQuad = null,
        filterPreset = PageFilterPreset.ORIGINAL,
        processingState = PageProcessingState.PROCESSED,
        createdAtMillis = 0L,
        updatedAtMillis = updatedAtMillis,
    )

    private fun recognizedText() = RecognizedPageText(
        tokens = listOf(
            token(index = 0, text = "Scanly"),
            token(index = 1, text = "OCR"),
        ),
    )

    private fun token(index: Int, text: String) = RecognizedTextToken(
        index = index,
        text = text,
        blockIndex = 0,
        lineIndex = 0,
        cornerPoints = listOf(
            NormalizedTextPoint(0f, 0f),
            NormalizedTextPoint(1f, 0f),
            NormalizedTextPoint(1f, 1f),
            NormalizedTextPoint(0f, 1f),
        ),
    )

    private class FakePageTextRecognizer(
        var result: ScanlyResult<RecognizedPageText>,
    ) : PageTextRecognizer {
        var callCount: Int = 0

        override suspend fun recognize(imagePath: String): ScanlyResult<RecognizedPageText> {
            callCount += 1
            return result
        }
    }

    private class FakePageRepository(
        private val pages: MutableStateFlow<List<ScanPage>>,
    ) : PageRepository {
        override fun observePages(documentId: String): Flow<List<ScanPage>> = pages

        override fun observePage(pageId: String): Flow<ScanPage?> =
            pages.map { pageList -> pageList.firstOrNull { it.id == pageId } }

        override suspend fun prepareCapture(documentId: String): ScanlyResult<PageCaptureDraft> =
            unexpectedCall()

        override suspend fun prepareReplacementCapture(pageId: String): ScanlyResult<PageCaptureDraft> =
            unexpectedCall()

        override suspend fun finalizeCapture(draft: PageCaptureDraft): ScanlyResult<String> =
            unexpectedCall()

        override suspend fun movePage(pageId: String, targetIndex: Int): ScanlyResult<Unit> =
            unexpectedCall()

        override suspend fun deletePage(pageId: String): ScanlyResult<Unit> = unexpectedCall()

        override suspend fun updatePageEdits(
            pageId: String,
            cropQuad: DocumentCornerQuad,
            rotationDegrees: Int,
            filterPreset: PageFilterPreset,
            applyFilterToAllPages: Boolean,
        ): ScanlyResult<Unit> = unexpectedCall()

        private fun <T> unexpectedCall(): T = error("Unexpected repository call")
    }
}
