package `in`.c1ph3rj.scanly.feature.tools

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import `in`.c1ph3rj.scanly.navigation.ToolsPdfMergeDestination
import `in`.c1ph3rj.scanly.navigation.ToolsPdfSplitDestination
import `in`.c1ph3rj.scanly.ui.theme.ScanlyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real Compose screen captures without reading or modifying any persisted user documents. */
@RunWith(AndroidJUnit4::class)
class ToolsArtworkTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tactileToolsRenderInAllThemesAndDocumentActionsKeepTheirRoutes() {
        val appearance = mutableIntStateOf(0)
        var openedRoute: String? = null
        composeRule.setContent {
            key(appearance.intValue) {
                ScanlyTheme(darkTheme = appearance.intValue > 0, pureBlack = appearance.intValue == 2) {
                    ToolsScreen(
                        uiState = ToolsUiState(),
                        snackbarHostState = SnackbarHostState(),
                        onScan = {},
                        onImport = {},
                        onOpenTool = { openedRoute = it },
                        onSuggestTitle = { "New scan" },
                    )
                }
            }
        }
        listOf("light", "dark", "amoled").forEachIndexed { index, theme ->
            composeRule.runOnIdle { appearance.intValue = index }
            composeRule.onNodeWithText("Scan a document").assertIsDisplayed()
            composeRule.onNodeWithText("Add to library").assertIsDisplayed()
            capture("$theme-top")
            composeRule.onNodeWithText("Merge").performScrollTo().performClick()
            composeRule.runOnIdle { assertEquals(ToolsPdfMergeDestination.route, openedRoute) }
            composeRule.onNodeWithText("Split").performScrollTo().performClick()
            composeRule.runOnIdle { assertEquals(ToolsPdfSplitDestination.route, openedRoute) }
            capture("$theme-documents")
            composeRule.onNodeWithText("My signatures").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("QR & barcode").assertIsDisplayed()
            composeRule.onNodeWithText("Image to text").assertIsDisplayed()
            capture("$theme-more")
        }
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "tools-tactile")
            .apply { mkdirs() }
        assertTrue(UiDevice.getInstance(instrumentation).takeScreenshot(File(directory, "$name.png")))
    }
}
