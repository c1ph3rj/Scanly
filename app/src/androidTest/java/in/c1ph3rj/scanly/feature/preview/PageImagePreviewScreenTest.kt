package `in`.c1ph3rj.scanly.feature.preview

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import `in`.c1ph3rj.scanly.domain.model.NormalizedTextPoint
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import `in`.c1ph3rj.scanly.domain.model.PageProcessingState
import `in`.c1ph3rj.scanly.domain.model.RecognizedPageText
import `in`.c1ph3rj.scanly.domain.model.RecognizedTextToken
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import `in`.c1ph3rj.scanly.ui.theme.ScanlyTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PageImagePreviewScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readyTextModeExposesSelectionAndCopyActions() {
        var copied = false
        composeRule.setContent {
            var uiState by remember { mutableStateOf(readyState()) }
            PageImagePreviewScreen(
                uiState = uiState,
                onNavigateUp = {},
                onEditPage = {},
                onSharePage = {},
                onSelectPage = {},
                onToggleTextMode = {},
                onExitTextMode = {},
                onRetryTextRecognition = {},
                onSelectTextToken = {},
                onSelectTextRange = { _, _ -> },
                onClearTextSelection = {},
                onSelectAllText = {
                    val ready = uiState.textMode as PageTextModeState.Ready
                    uiState = uiState.copy(textMode = ready.copy(selection = 0..1))
                },
                onCopySelectedText = { copied = true },
            )
        }

        composeRule.onNodeWithContentDescription("Close text selection").assertExists()
        composeRule.onNodeWithText("2 words found").assertExists()
        composeRule.onNodeWithText("Copy").assertIsNotEnabled()
        composeRule.onNodeWithText("Select all").performClick()
        composeRule.onNodeWithText("2 selected").assertTextContains("2 selected")
        composeRule.onNodeWithText("Copy").assertIsEnabled().performClick()
        composeRule.runOnIdle { assertTrue(copied) }
    }

    private fun readyState(): PageImagePreviewUiState {
        val page = ScanPage(
            id = "page-1",
            documentId = "document-1",
            pageIndex = 0,
            rawImagePath = null,
            processedImagePath = null,
            thumbnailPath = null,
            rotationDegrees = 0,
            cropQuad = null,
            filterPreset = PageFilterPreset.ORIGINAL,
            processingState = PageProcessingState.PROCESSED,
            createdAtMillis = 0L,
            updatedAtMillis = 1L,
        )
        val cacheKey = PageOcrCacheKey("page-1", "processed.jpg", 1L)
        return PageImagePreviewUiState(
            pages = listOf(page),
            selectedPageId = page.id,
            isLoading = false,
            textMode = PageTextModeState.Ready(
                cacheKey = cacheKey,
                recognizedText = RecognizedPageText(
                    listOf(token(0, "Scanly"), token(1, "OCR")),
                ),
            ),
        )
    }

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
}
