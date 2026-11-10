package `in`.c1ph3rj.scanly.feature.onboarding

import android.Manifest
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.rule.GrantPermissionRule
import `in`.c1ph3rj.scanly.ui.theme.ScanlyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OnboardingGrantedCameraTest {
    @get:Rule(order = 0)
    val permission = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    @Test
    fun firstScanUsesScanCallbackWhenCameraIsGranted() {
        var starts = 0
        var homeCompletions = 0
        composeRule.setContent {
            ScanlyTheme {
                OnboardingFlow(
                    uiState = OnboardingUiState(status = OnboardingStatus.REQUIRED),
                    onComplete = { homeCompletions++ },
                    onDismissError = {},
                    onStartScan = { starts++ },
                )
            }
        }
        composeRule.onNodeWithTag("onboarding_skip").performClick()
        composeRule.onNodeWithText("Scan my first page").performClick()
        composeRule.runOnIdle {
            assertEquals(1, starts)
            assertEquals(0, homeCompletions)
        }
        composeRule.onNodeWithTag("onboarding_permission_sheet").assertDoesNotExist()
        composeRule.onNodeWithText("Import a photo").assertDoesNotExist()
    }
}
