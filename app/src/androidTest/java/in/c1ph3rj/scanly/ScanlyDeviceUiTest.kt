package `in`.c1ph3rj.scanly

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
    val grantPermissions: GrantPermissionRule = GrantPermissionRule.grant(
        *buildList {
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray(),
    )

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
        composeRule.onNodeWithText("Scan").assertIsDisplayed()
        composeRule.onNodeWithText("Folder").assertIsDisplayed()
    }

    @Test
    fun libraryDestinationShowsIdentifyingUi() {
        composeRule.openDestination(ScanlyTestTags.NAV_LIBRARY, ScanlyTestTags.LIBRARY_SCREEN)
        composeRule.onNodeWithText("Folders").assertIsDisplayed()
        composeRule.onNodeWithText("Documents").assertIsDisplayed()
    }

    @Test
    fun toolsDestinationShowsIdentifyingUi() {
        composeRule.openDestination(ScanlyTestTags.NAV_TOOLS, ScanlyTestTags.TOOLS_SCREEN)
        composeRule.onNodeWithText("PDF workspace").assertIsDisplayed()
        composeRule.onNodeWithText("QR CODE").assertIsDisplayed()
    }

    @Test
    fun settingsDestinationShowsIdentifyingUi() {
        composeRule.openDestination(ScanlyTestTags.NAV_SETTINGS, ScanlyTestTags.SETTINGS_SCREEN)
        composeRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeRule.onNodeWithText("Storage & backup").assertIsDisplayed()
    }

    @Test
    fun generatingAQrCodeShowsAPreview() {
        composeRule.generateQrFromTools("https://scanly.app/ui-test")
    }
}
