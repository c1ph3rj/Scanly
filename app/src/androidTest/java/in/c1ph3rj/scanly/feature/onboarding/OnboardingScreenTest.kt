package `in`.c1ph3rj.scanly.feature.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import `in`.c1ph3rj.scanly.ui.theme.ScanlyTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun onboarding_showsAllCoreFeaturesInOneScreen() {
        composeRule.setContent {
            ScanlyTheme {
                OnboardingScreen(
                    uiState = OnboardingUiState(status = OnboardingStatus.REQUIRED),
                    onComplete = {},
                    onDismissError = {},
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 2_000) {
            composeRule.onAllNodesWithText("Export your way")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeRule.onNodeWithTag("onboarding_screen").assertExists()
        composeRule.onNodeWithTag("onboarding_layout_compact").assertExists()
        composeRule.onNodeWithText("Scanly.").assertExists()
        composeRule.onNodeWithText("OPEN SOURCE").assertExists()
        composeRule.onNodeWithText("Capture clean pages").assertExists()
        composeRule.onNodeWithText("Keep documents organized").assertExists()
        composeRule.onNodeWithText("Export your way").assertExists()
        composeRule.onNodeWithTag("onboarding_get_started").assertIsEnabled()
    }

    @Test
    fun getStarted_completesOnboarding() {
        var completionRequested = false
        composeRule.setContent {
            ScanlyTheme {
                OnboardingScreen(
                    uiState = OnboardingUiState(status = OnboardingStatus.REQUIRED),
                    onComplete = { completionRequested = true },
                    onDismissError = {},
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 2_000) {
            composeRule.onAllNodesWithText("Get started")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag("onboarding_get_started").performClick()

        composeRule.runOnIdle {
            assertTrue(completionRequested)
        }
    }

    @Test
    fun tabletPortrait_usesCenteredMediumLayout() {
        composeRule.setContent {
            ScanlyTheme {
                Box(modifier = androidx.compose.ui.Modifier.requiredSize(800.dp, 1_280.dp)) {
                    OnboardingLayout(
                        layoutMode = OnboardingLayoutMode.MEDIUM,
                        animationStage = 5,
                        isCompleting = false,
                        onComplete = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("onboarding_layout_medium").assertExists()
        composeRule.onNodeWithText("Export your way").assertExists()
        composeRule.onNodeWithTag("onboarding_get_started").assertIsEnabled()
    }

    @Test
    fun expandedScreen_usesWideLayout() {
        composeRule.setContent {
            ScanlyTheme {
                Box(modifier = androidx.compose.ui.Modifier.requiredSize(1_280.dp, 800.dp)) {
                    OnboardingLayout(
                        layoutMode = OnboardingLayoutMode.WIDE,
                        animationStage = 5,
                        isCompleting = false,
                        onComplete = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("onboarding_layout_wide").assertExists()
        composeRule.onNodeWithText("Export your way").assertExists()
        composeRule.onNodeWithTag("onboarding_get_started").assertIsEnabled()
    }
}
