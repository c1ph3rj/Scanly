package `in`.c1ph3rj.scanly.feature.onboarding

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import `in`.c1ph3rj.scanly.core.common.AppLanguage
import `in`.c1ph3rj.scanly.ui.theme.ScanlyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class WelcomeScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private fun show(
        width: Dp = 390.dp,
        height: Dp = 844.dp,
        selected: AppLanguage? = null,
        onSelect: (AppLanguage?) -> Unit = {},
        onContinue: () -> Unit = {},
    ) {
        composeRule.setContent {
            ScanlyTheme {
                Box(Modifier.requiredSize(width, height)) {
                    WelcomeScreen(
                        selected = selected,
                        deviceLocale = Locale.US,
                        onLanguageSelected = onSelect,
                        onContinue = onContinue,
                    )
                }
            }
        }
    }

    @Test fun showsLogoSummaryAndPhoneLanguageByDefault() {
        show()
        composeRule.onNodeWithTag("welcome_logo").assertIsDisplayed()
        composeRule.onNodeWithTag("welcome_greeting").assertIsDisplayed()
        composeRule.onNodeWithTag("welcome_phone_language").assertIsSelected()
        composeRule.onNodeWithTag("welcome_language_en").assertIsNotSelected()
        composeRule.onNodeWithTag("welcome_continue").assertIsDisplayed()
    }

    @Test fun pickingALanguageReportsIt() {
        var picked: AppLanguage? = null
        show(onSelect = { picked = it })
        composeRule.onNodeWithTag("welcome_language_list")
            .performScrollToNode(hasTestTag("welcome_language_ta"))
        composeRule.onNodeWithTag("welcome_language_ta").performClick()
        composeRule.runOnIdle { assertEquals(AppLanguage.TAMIL, picked) }
    }

    @Test fun searchFiltersByEnglishOrNativeName() {
        show(selected = AppLanguage.GERMAN)
        composeRule.onNodeWithTag("welcome_search").performTextInput("tamil")
        Espresso.closeSoftKeyboard()
        composeRule.onNodeWithTag("welcome_language_ta").assertIsDisplayed()
        composeRule.onNodeWithTag("welcome_language_en").assertDoesNotExist()
        composeRule.onNodeWithTag("welcome_phone_language").assertDoesNotExist()
        composeRule.onNodeWithTag("welcome_search_clear").performClick()
        composeRule.onNodeWithTag("welcome_phone_language").assertIsDisplayed()
        composeRule.onNodeWithTag("welcome_search").performTextInput("zzz")
        Espresso.closeSoftKeyboard()
        composeRule.onNodeWithTag("welcome_no_match").assertIsDisplayed()
    }

    @Test fun continueIsReachableOnASmallPhone() = assertContinueReachable(320.dp, 568.dp, "single_pane")

    @Test fun phoneLandscapeUsesTwoPanes() = assertContinueReachable(780.dp, 360.dp, "two_pane")

    @Test fun tabletUsesTwoPanes() = assertContinueReachable(1280.dp, 800.dp, "two_pane")

    private fun assertContinueReachable(width: Dp, height: Dp, layout: String) {
        var continued = 0
        show(width = width, height = height, onContinue = { continued++ })
        composeRule.onNodeWithTag("welcome_layout_$layout").assertExists()
        composeRule.onNodeWithTag("welcome_continue").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, continued) }
    }

    /** Visual QA: writes PNGs to the app's external files dir under test-artifacts/welcome. */
    @Test fun capturesLayouts() {
        val sizes = listOf(
            Triple("phone", 390.dp, 844.dp),
            Triple("small-phone", 320.dp, 568.dp),
            Triple("landscape", 800.dp, 360.dp),
            Triple("tablet", 1280.dp, 800.dp),
        )
        var current by mutableStateOf(sizes.first())
        var dark by mutableStateOf(false)
        var selected by mutableStateOf<AppLanguage?>(null)
        composeRule.setContent {
            ScanlyTheme(darkTheme = dark) {
                Box(Modifier.requiredSize(current.second, current.third)) {
                    WelcomeScreen(selected, Locale.US, { selected = it }, {})
                }
            }
        }
        sizes.forEach { size ->
            current = size
            save(size.first)
        }
        current = sizes.first()
        dark = true
        selected = AppLanguage.TAMIL
        save("phone-dark-tamil-selected")
    }

    private fun save(name: String) {
        composeRule.waitForIdle()
        val bitmap = composeRule.onNodeWithTag("welcome_screen").captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.getExternalFilesDir(null), "test-artifacts/welcome/$name.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun flowShowsWelcomeFirstAndBackReturnsToIt() {
        composeRule.setContent {
            ScanlyTheme {
                OnboardingFlow(OnboardingUiState(status = OnboardingStatus.REQUIRED), {}, {})
            }
        }
        composeRule.onNodeWithTag("welcome_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("welcome_continue").performClick()
        composeRule.onNodeWithTag("onboarding_screen").assertIsDisplayed()
        Espresso.pressBack()
        composeRule.onNodeWithTag("welcome_screen").assertIsDisplayed()
    }
}
