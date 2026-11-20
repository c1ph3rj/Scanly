package `in`.c1ph3rj.scanly

import android.Manifest
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.isDisplayed
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import `in`.c1ph3rj.scanly.core.common.appString
import `in`.c1ph3rj.scanly.core.ui.ScanlyTestTags
import `in`.c1ph3rj.scanly.core.ui.ZoomableImageDialog
import `in`.c1ph3rj.scanly.domain.model.PdfToolSource
import `in`.c1ph3rj.scanly.feature.tools.pdf.PdfPeekDialog
import `in`.c1ph3rj.scanly.ui.theme.ScanlyTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScanlyDeviceUiTest {
    @get:Rule(order = 0)
    val grantPermissions: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun dismissSystemChrome() {
        dismissBlockingSystemDialogs()
    }

    @Test
    fun onboardingFirstScanCanReturnToHome() {
        composeRule.completeOnboardingIfNeeded()
        composeRule.onNodeWithTag(ScanlyTestTags.HOME_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithText("Import").assertIsDisplayed()
        composeRule.onNodeWithText("Folder").assertIsDisplayed()
        composeRule.onNodeWithTag(ScanlyTestTags.NAV_SCAN).assertIsDisplayed()
    }

    @Test
    fun libraryDestinationShowsIdentifyingUi() {
        composeRule.openDestination(ScanlyTestTags.NAV_LIBRARY, ScanlyTestTags.LIBRARY_SCREEN)
        // A granted first-run scan can leave an empty document after closing the camera.
        composeRule.onNodeWithTag(ScanlyTestTags.LIBRARY_SEARCH).assertIsDisplayed()
        composeRule.onNodeWithTag(ScanlyTestTags.LIBRARY_SEARCH).performClick()
        composeRule.awaitTag(ScanlyTestTags.SEARCH_SCREEN)
        composeRule.waitUntil(20_000) { composeRule.onNodeWithTag(ScanlyTestTags.SEARCH_INPUT).isDisplayed() }
        composeRule.onNodeWithTag(ScanlyTestTags.SEARCH_INPUT).assertIsDisplayed()
        composeRule.onNodeWithText("Documents").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.awaitTag(ScanlyTestTags.LIBRARY_SCREEN)
    }

    @Test
    fun toolsDestinationShowsIdentifyingUi() {
        composeRule.openDestination(ScanlyTestTags.NAV_TOOLS, ScanlyTestTags.TOOLS_SCREEN)
        composeRule.onNodeWithText("PDF").assertIsDisplayed()
        composeRule.onNodeWithText("Read PDF").assertIsDisplayed()
        composeRule.onNodeWithText("QR & barcode").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settingsDestinationShowsIdentifyingUi() {
        composeRule.openDestination(ScanlyTestTags.NAV_SETTINGS, ScanlyTestTags.SETTINGS_SCREEN)
        composeRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeRule.onNodeWithText("Storage").assertIsDisplayed()
    }

    @Test
    fun generatingAQrCodeShowsAPreview() {
        composeRule.generateQrFromTools("https://scanly.app/ui-test")
    }

    @Test
    fun fullScreenPreviewCloseButtonsRespectSystemInsets() {
        var showPdf by mutableStateOf(false)
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                ScanlyTheme {
                    if (showPdf) {
                        PdfPeekDialog(
                            source = PdfToolSource.AppFile("", "Preview"),
                            password = null,
                            onDismiss = {},
                        )
                    } else {
                        ZoomableImageDialog(imagePath = null, title = "Preview", onDismiss = { showPdf = true })
                    }
                }
            }
        }

        assertCloseButtonIsInSafeArea(appString(R.string.zoomable_image_close))
        composeRule.onNodeWithContentDescription(appString(R.string.zoomable_image_close)).performClick()
        assertCloseButtonIsInSafeArea(appString(R.string.pdf_peek_close))
    }

    private fun assertCloseButtonIsInSafeArea(label: String) {
        val decor = composeRule.activity.window.decorView
        val control = composeRule.onNodeWithContentDescription(label).assertIsDisplayed().fetchSemanticsNode()
        composeRule.runOnIdle {
            val insets = checkNotNull(ViewCompat.getRootWindowInsets(decor)).getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            val windowOrigin = IntArray(2).also(decor::getLocationOnScreen)
            val (x, y) = control.positionOnScreen
            assertTrue("$label overlaps a horizontal inset at $x", x >= windowOrigin[0] + insets.left &&
                x + control.size.width <= windowOrigin[0] + decor.width - insets.right)
            assertTrue("$label overlaps a vertical inset at $y", y >= windowOrigin[1] + insets.top &&
                y + control.size.height <= windowOrigin[1] + decor.height - insets.bottom)
        }
    }
}
