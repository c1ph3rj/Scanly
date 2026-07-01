package `in`.c1ph3rj.scanly.feature.startup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import `in`.c1ph3rj.scanly.domain.model.LibraryAccessState
import `in`.c1ph3rj.scanly.domain.model.LibraryStartupStatus
import `in`.c1ph3rj.scanly.ui.theme.ScanlyTheme
import org.junit.Rule
import org.junit.Test

class LibraryStartupScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun reconnectStateDoesNotRenderAnEmptyLibrary() {
        composeRule.setContent {
            ScanlyTheme {
                LibraryStartupScreen(
                    state = LibraryAccessState(
                        status = LibraryStartupStatus.RECONNECT_REQUIRED,
                        message = "Select the existing folder.",
                    ),
                    onChooseFolder = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("Reconnect your Scanly library").assertIsDisplayed()
        composeRule.onNodeWithText("Select library folder").assertIsDisplayed()
    }
}

