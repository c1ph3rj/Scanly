package `in`.c1ph3rj.scanly.feature.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import `in`.c1ph3rj.scanly.domain.model.ScanMode
import org.junit.Rule
import org.junit.Test

class ScanModeSelectorTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun modePillStartsOnDocumentAndSelectsId() {
        var selectedMode by mutableStateOf(ScanMode.DOCUMENT)
        composeRule.setContent {
            MaterialTheme {
                ScanModeSelector(
                    selectedMode = selectedMode,
                    onModeSelected = { selectedMode = it },
                )
            }
        }

        composeRule.onNodeWithText("Document").assertIsSelected()
        composeRule.onNodeWithText("ID").performClick().assertIsSelected()
    }

    @Test
    fun verticalModeRailKeepsLabelsOnOneLineAndSelectsBook() {
        var selectedMode by mutableStateOf(ScanMode.DOCUMENT)
        composeRule.setContent {
            MaterialTheme {
                ScanModeSelector(
                    selectedMode = selectedMode,
                    onModeSelected = { selectedMode = it },
                    layout = ScanModeSelectorLayout.VERTICAL,
                    modifier = Modifier.width(168.dp),
                )
            }
        }

        composeRule.onNodeWithText("ID card").assertExists()
        composeRule.onNodeWithText("Book").performClick().assertIsSelected()
    }
}
