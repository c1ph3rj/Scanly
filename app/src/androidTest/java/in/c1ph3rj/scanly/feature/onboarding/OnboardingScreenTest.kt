package `in`.c1ph3rj.scanly.feature.onboarding

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import `in`.c1ph3rj.scanly.ui.theme.ScanlyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Isolated Compose fixtures. Run on a large emulator; no library/preferences are reset. */
class OnboardingScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private fun show(
        width: Int = 390,
        height: Int = 844,
        fontScale: Float = 1f,
        dark: Boolean = false,
        pureBlack: Boolean = false,
        completing: Boolean = false,
        onComplete: () -> Unit = {},
    ) {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                ScanlyTheme(darkTheme = dark, pureBlack = pureBlack) {
                    // These are content-only viewports, centered inside the large host window.
                    // Its physical cutout does not intersect them; don't apply its insets again.
                    Box(Modifier.requiredSize(width.dp, height.dp).consumeWindowInsets(WindowInsets.safeDrawing)) {
                        OnboardingScreen(
                            OnboardingUiState(status = OnboardingStatus.REQUIRED, isCompleting = completing),
                            onComplete, {},
                        )
                    }
                }
            }
        }
    }

    private fun capture(name: String, tag: String = "onboarding_screen") {
        composeRule.waitForIdle()
        val bitmap = composeRule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.getExternalFilesDir(null), "test-artifacts/onboarding/$name.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun skip() {
        composeRule.onNodeWithTag("onboarding_skip").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("onboarding_get_started").assertIsDisplayed()
    }

    @Test fun threePages_continueBackAndComplete() {
        var completions = 0
        show(onComplete = { completions++ })
        composeRule.onNodeWithText("Paper to polished.").assertIsDisplayed()
        capture("phone-capture")
        composeRule.onNodeWithTag("onboarding_continue").performClick()
        composeRule.onNodeWithText("Find more than files.").assertIsDisplayed()
        capture("phone-find")
        composeRule.onNodeWithTag("onboarding_back").performClick()
        composeRule.onNodeWithText("Paper to polished.").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_dot_2").performClick()
        composeRule.onNodeWithText("Ready when you are.").assertIsDisplayed()
        composeRule.onNodeWithText("No account. No cloud upload.").assertIsDisplayed()
        capture("phone-share")
        assertEquals(0, completions)
        composeRule.onNodeWithTag("onboarding_get_started").performClick()
        composeRule.runOnIdle { assertEquals(1, completions) }
    }

    @Test fun swipe_changesPageAndSkipDoesNotComplete() {
        var completions = 0
        show(onComplete = { completions++ })
        composeRule.onNodeWithTag("onboarding_pager").performTouchInput { swipeLeft() }
        composeRule.onNodeWithText("Find more than files.").assertIsDisplayed()
        skip()
        composeRule.runOnIdle { assertEquals(0, completions) }
    }

    @Test fun pageSurvivesSavedStateRestoration() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            ScanlyTheme {
                OnboardingScreen(OnboardingUiState(status = OnboardingStatus.REQUIRED), {}, {})
            }
        }
        skip()
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithTag("onboarding_get_started").assertIsDisplayed()
        composeRule.onNodeWithText("Ready when you are.").assertIsDisplayed()
    }

    @Test fun savingDisablesNavigationAndCompletion() {
        show(completing = true)
        composeRule.onNodeWithTag("onboarding_continue").assertIsNotEnabled()
        composeRule.onNodeWithTag("onboarding_skip").assertIsNotEnabled()
        composeRule.onNodeWithTag("onboarding_dot_2").assertIsNotEnabled()
    }

    @Test fun smallPhone_actionsRemainVisibleAndCopyScrolls() {
        show(width = 320, height = 480)
        composeRule.onNodeWithTag("onboarding_layout_compact").assertExists()
        composeRule.onNodeWithTag("onboarding_continue").assertIsDisplayed().assertIsEnabled()
        capture("small-phone-capture")
        skip()
        composeRule.onNodeWithText("No account. No cloud upload.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_get_started").assertIsDisplayed()
        capture("small-phone-share-scrolled")
    }

    @Test fun tabletPortrait_centersTheStory() {
        show(width = 800, height = 1280)
        composeRule.onNodeWithTag("onboarding_layout_medium").assertExists()
        composeRule.onNodeWithTag("onboarding_continue").assertIsDisplayed()
        capture("tablet-portrait")
    }

    @Test fun tabletLandscape_usesTwoPanes() {
        show(width = 1280, height = 800)
        composeRule.onNodeWithTag("onboarding_layout_wide").assertExists()
        composeRule.onNodeWithText("Paper to polished.").assertIsDisplayed()
        capture("tablet-landscape")
    }

    @Test fun phoneLandscape_keepsActionsAndCopyReachable() {
        show(width = 640, height = 360)
        composeRule.onNodeWithTag("onboarding_layout_wide").assertExists()
        composeRule.onNodeWithTag("onboarding_continue").assertIsDisplayed()
        composeRule.onNodeWithText("Paper to polished.").performScrollTo().assertIsDisplayed()
        capture("phone-landscape")
        skip()
        composeRule.onNodeWithText("No account. No cloud upload.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_get_started").assertIsDisplayed()
    }

    @Test fun narrowSplitWindowAndLargeText_canFinish() {
        show(width = 320, height = 568, fontScale = 2f)
        composeRule.onNodeWithTag("onboarding_continue").assertIsDisplayed()
        composeRule.onNodeWithText("Paper to polished.").performScrollTo().assertIsDisplayed()
        capture("large-text-capture")
        skip()
        composeRule.onNodeWithText("No account. No cloud upload.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_get_started").assertIsDisplayed().assertIsEnabled()
        capture("large-text-share-scrolled")
    }

    @Test fun darkTheme_usesReadableBrandColors() {
        show(dark = true)
        skip()
        capture("dark-share")
    }

    @Test fun pureBlackTheme_keepsPaperIllustration() {
        show(dark = true, pureBlack = true)
        capture("amoled-capture")
    }

    @Test fun largeTextLandscape_keepsPrimaryActionVisible() {
        show(width = 740, height = 360, fontScale = 2f)
        skip()
        composeRule.onNodeWithText("No account. No cloud upload.").performScrollTo()
        capture("large-text-landscape-scrolled")
        composeRule.onNodeWithText("No account. No cloud upload.").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_get_started").assertIsDisplayed()
    }

    @Test fun resizingWindow_preservesPageAndChangesLayout() {
        val bounds = mutableStateOf(390 to 844)
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                ScanlyTheme {
                    Box(
                        Modifier.requiredSize(bounds.value.first.dp, bounds.value.second.dp)
                            .consumeWindowInsets(WindowInsets.safeDrawing),
                    ) {
                        OnboardingScreen(OnboardingUiState(status = OnboardingStatus.REQUIRED), {}, {})
                    }
                }
            }
        }
        skip()
        composeRule.runOnIdle { bounds.value = 900 to 420 }
        composeRule.onNodeWithTag("onboarding_layout_wide").assertExists()
        composeRule.onNodeWithTag("onboarding_get_started").assertIsDisplayed()
        composeRule.runOnIdle { bounds.value = 800 to 1280 }
        composeRule.onNodeWithTag("onboarding_layout_medium").assertExists()
        composeRule.onNodeWithText("Ready when you are.").assertIsDisplayed()
        capture("resized-tablet-share")
    }

    @Test fun completionFailure_keepsLastPageAndAllowsRetry() {
        val state = mutableStateOf(OnboardingUiState(status = OnboardingStatus.REQUIRED))
        composeRule.setContent {
            ScanlyTheme {
                OnboardingScreen(state.value, { state.value = state.value.copy(errorMessage = "Try again") }, {})
            }
        }
        skip()
        composeRule.onNodeWithTag("onboarding_get_started").performClick()
        composeRule.onNodeWithText("Try again").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_get_started").assertIsEnabled()
    }

    @Test fun permissionSheet_notNowAndEnableAreSeparateActions() {
        var allowed = 0
        var skipped = 0
        composeRule.setContent {
            ScanlyTheme(darkTheme = false) {
                OnboardingPermissionSheet(false, true, {}, { skipped++ }, { allowed++ })
            }
        }
        composeRule.onNodeWithTag("onboarding_allow_camera").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, allowed); assertEquals(0, skipped) }
        composeRule.onNodeWithTag("onboarding_camera_later").performClick()
        composeRule.runOnIdle { assertEquals(1, skipped) }
        capture("permission-sheet", "onboarding_permission_sheet")
    }

    @Test fun permissionSheet_blockedExplainsSettingsAndKeepsSkip() {
        composeRule.setContent {
            ScanlyTheme {
                OnboardingPermissionSheet(true, true, {}, {}, {})
            }
        }
        composeRule.onNodeWithText("Open Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Camera access is turned off").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_camera_later").assertIsDisplayed().assertIsEnabled()
    }

    @Test fun flow_notNowCompletesWithoutRequestingCamera() {
        var completions = 0
        composeRule.setContent {
            ScanlyTheme {
                OnboardingFlow(OnboardingUiState(status = OnboardingStatus.REQUIRED), { completions++ }, {})
            }
        }
        skip()
        composeRule.onNodeWithTag("onboarding_get_started").performClick()
        composeRule.onNodeWithTag("onboarding_permission_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_camera_later").performClick()
        composeRule.runOnIdle { assertEquals(1, completions) }
        composeRule.onNodeWithTag("onboarding_permission_sheet").assertDoesNotExist()
    }

    @Test fun flow_dismissingPermissionReturnsToShareWithoutCompleting() {
        var completions = 0
        composeRule.setContent {
            ScanlyTheme {
                OnboardingFlow(OnboardingUiState(status = OnboardingStatus.REQUIRED), { completions++ }, {})
            }
        }
        skip()
        composeRule.onNodeWithTag("onboarding_get_started").performClick()
        composeRule.onNodeWithTag("onboarding_permission_sheet").assertIsDisplayed()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("onboarding_permission_sheet").assertDoesNotExist()
        composeRule.onNodeWithTag("onboarding_get_started").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, completions) }
    }
}
