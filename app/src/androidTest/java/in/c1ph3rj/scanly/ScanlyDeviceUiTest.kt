package `in`.c1ph3rj.scanly

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.isDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import `in`.c1ph3rj.scanly.core.ui.ScanlyTestTags
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
    fun onboardingCompletesThenHomeIdentifyingUiIsVisible() {
        composeRule.completeOnboardingIfNeeded()
        composeRule.onNodeWithTag(ScanlyTestTags.HOME_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithText("Import").assertIsDisplayed()
        composeRule.onNodeWithText("Folder").assertIsDisplayed()
        composeRule.onNodeWithTag(ScanlyTestTags.NAV_SCAN).assertIsDisplayed()
    }

    @Test
    fun libraryDestinationShowsIdentifyingUi() {
        composeRule.openDestination(ScanlyTestTags.NAV_LIBRARY, ScanlyTestTags.LIBRARY_SCREEN)
        // The suite clears app data, so the library opens on its empty state.
        composeRule.onNodeWithText("Your library is ready").assertIsDisplayed()
        composeRule.onNodeWithText("New folder").assertIsDisplayed()
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
}
