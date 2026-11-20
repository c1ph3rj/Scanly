package `in`.c1ph3rj.scanly.feature.components

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.domain.model.ScanMode
import org.junit.Rule
import org.junit.Test

class ScanModeSelectorTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun label(mode: ScanMode) = context.getString(mode.labelRes)

    @Test
    fun pillStartsOnDocumentAndSelectsIdCard() {
        var selectedMode by mutableStateOf(ScanMode.DOCUMENT)
        composeRule.setContent {
            MaterialTheme {
                ScanModeSelector(selectedMode = selectedMode, onModeSelected = { selectedMode = it })
            }
        }

        composeRule.onNodeWithText(label(ScanMode.DOCUMENT)).assertIsSelected()
        composeRule.onNodeWithText(label(ScanMode.ID_CARD)).performClick().assertIsSelected()
        composeRule.onNodeWithText(label(ScanMode.DOCUMENT)).assertIsNotSelected()
    }

    @Test
    fun narrowVerticalRailSelectsBook() {
        var selectedMode by mutableStateOf(ScanMode.DOCUMENT)
        composeRule.setContent {
            MaterialTheme {
                ScanModeSelector(
                    selectedMode = selectedMode,
                    onModeSelected = { selectedMode = it },
                    layout = ScanModeSelectorLayout.VERTICAL,
                    modifier = Modifier.width(136.dp),
                )
            }
        }

        composeRule.onNodeWithText(label(ScanMode.ID_CARD)).assertExists()
        composeRule.onNodeWithText(label(ScanMode.BOOK)).performClick().assertIsSelected()
    }

    @Test
    fun lockedSelectorIgnoresTaps() {
        var selectedMode by mutableStateOf(ScanMode.DOCUMENT)
        composeRule.setContent {
            MaterialTheme {
                ScanModeSelector(selectedMode = selectedMode, onModeSelected = { selectedMode = it }, enabled = false)
            }
        }

        composeRule.onNodeWithText(label(ScanMode.BOOK)).performClick()
        composeRule.onNodeWithText(label(ScanMode.DOCUMENT)).assertIsSelected()
        check(context.getString(R.string.scan_mode_selector_description).isNotBlank())
    }
}
