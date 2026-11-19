package `in`.c1ph3rj.scanly.feature.settings

import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.domain.usecase.FeedbackAttachmentsUseCase
import `in`.c1ph3rj.scanly.ui.theme.ScanlyTheme
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FeedbackEmailReturnUiTest {
    @get:Rule val compose = createComposeRule()
    private var clearInput: () -> Unit = {}
    private var closed = 0
    private fun show(model: FeedbackViewModel) {
        compose.setContent {
            val focus = LocalFocusManager.current
            val keyboard = LocalSoftwareKeyboardController.current
            SideEffect { clearInput = { focus.clearFocus(); keyboard?.hide() } }
            ScanlyTheme { SettingsFeedbackRoute({ closed++ }, model) }
        }
        compose.waitUntil { !model.uiState.value.isPreparing }
        compose.onNodeWithTag("feedback_subject").performTextInput("Keep my subject")
        compose.onNodeWithTag("feedback_message").performTextInput("Please keep this feedback until I confirm.")
        compose.runOnIdle { clearInput(); model.addImages(listOf("one")) }
        compose.waitUntil { !model.uiState.value.isPreparing && model.uiState.value.images.size == 1 }
    }
    private fun returned(model: FeedbackViewModel) = compose.runOnIdle {
        model.onEmailLaunched(); model.onHostStopped(); model.onHostResumed()
    }
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun notYetPreservesFormAndYesClearsAndCloses() {
        val model = FeedbackViewModel(FeedbackAttachmentsUseCase(ReturnTestRepository()), SavedStateHandle())
        try {
            show(model)
            returned(model)
            compose.onNodeWithText(text(R.string.feedback_sent_title)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.feedback_sent_not_yet)).performClick()
            compose.onNodeWithTag("feedback_subject").assertTextContains("Keep my subject")
            compose.onNodeWithTag("feedback_message").assertTextContains("Please keep this feedback until I confirm.")
            compose.runOnIdle { assertEquals(0, closed); assertEquals(1, model.uiState.value.images.size) }
            returned(model)
            compose.onNodeWithText(text(R.string.feedback_sent_yes)).performClick()
            compose.waitUntil { closed == 1 }
            assertEquals("", compose.onNodeWithTag("feedback_subject").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
            assertEquals("", compose.onNodeWithTag("feedback_message").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
            compose.onNodeWithTag("feedback_send").assertIsNotEnabled()
            compose.runOnIdle { assertTrue(model.uiState.value.images.isEmpty()) }
        } finally { model.viewModelScope.cancel() }
    }

    @Test fun dismissingQuestionKeepsDraftAndDoesNotClosePage() {
        val model = FeedbackViewModel(FeedbackAttachmentsUseCase(ReturnTestRepository()), SavedStateHandle())
        try {
            show(model)
            returned(model)
            compose.onNodeWithText(text(R.string.feedback_sent_title)).assertIsDisplayed()
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
            compose.waitUntil { !model.handoffState.value.showConfirmation }
            compose.onNodeWithTag("feedback_subject").assertTextContains("Keep my subject")
            compose.onNodeWithTag("feedback_message").assertTextContains("Please keep this feedback until I confirm.")
            compose.runOnIdle { assertEquals(0, closed); assertEquals(1, model.uiState.value.images.size) }
        } finally { model.viewModelScope.cancel() }
    }
}
