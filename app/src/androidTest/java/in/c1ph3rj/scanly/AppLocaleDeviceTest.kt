package `in`.c1ph3rj.scanly

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.activity.compose.setContent
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import `in`.c1ph3rj.scanly.core.common.AppLanguage
import `in`.c1ph3rj.scanly.core.common.AppLocales
import `in`.c1ph3rj.scanly.core.common.AppLocaleProvider
import `in`.c1ph3rj.scanly.core.common.DefaultScanlyDispatchers
import `in`.c1ph3rj.scanly.core.common.appString
import `in`.c1ph3rj.scanly.core.ui.ScanlyTestTags
import `in`.c1ph3rj.scanly.data.settings.DefaultSettingsRepository
import `in`.c1ph3rj.scanly.feature.onboarding.OnboardingScreen
import `in`.c1ph3rj.scanly.feature.onboarding.OnboardingStatus
import `in`.c1ph3rj.scanly.feature.onboarding.OnboardingUiState
import `in`.c1ph3rj.scanly.ui.theme.ScanlyTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertSame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/** Run on API 29–32 and API 33+ to exercise both locale implementations. */
@RunWith(AndroidJUnit4::class)
class AppLocaleDeviceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun shippedLocalesResolveAndFormatRealAndroidResources() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val englishContext = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(Locale.ENGLISH))
        })
        val english = englishContext.getString(R.string.settings_language)
        AppLanguage.entries.forEach { language ->
            val localized = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                setLocales(LocaleList(language.locale))
            })
            if (language != AppLanguage.ENGLISH) {
                assertNotEquals(language.tag, english, localized.getString(R.string.settings_language))
            }
            // Includes 0/1/2/5/21 to cover one/few/many and zero-as-one languages.
            listOf(0, 1, 2, 5, 21).forEach { count ->
                val pages = localized.resources.getQuantityString(R.plurals.common_pages, count, count)
                assertNotEquals(language.tag, "", pages)
                localized.resources.getQuantityString(
                    R.plurals.recently_deleted_restored_some_items, count, "unused", count, count + 1, "reason",
                )
                localized.resources.getQuantityString(
                    R.plurals.folder_delete_what_should_happen, count, "Test", count,
                )
            }
            localized.getString(R.string.library_section_month_year, "Month", 2026)
            localized.getString(R.string.pdf_watermark_opacity_percent, 50)
            // Format every translated string with the source argument types, on Android itself.
            R.string::class.java.fields.forEach { field ->
                val id = field.getInt(null)
                val arguments = formatArguments(englishContext.getString(id))
                if (arguments.isNotEmpty()) localized.getString(id, *arguments)
            }
        }
        listOf("id-ID", "pt-PT", "zh-Hans-SG").forEach { tag ->
            val localized = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                setLocales(LocaleList(Locale.forLanguageTag(tag)))
            })
            assertNotEquals(tag, english, localized.getString(R.string.settings_language))
        }
        listOf("zh-Hant", "zh-TW", "zh-HK", "sw").forEach { tag ->
            val localized = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                setLocales(LocaleList(Locale.forLanguageTag(tag)))
            })
            assertEquals(tag, english, localized.getString(R.string.settings_language))
        }
    }

    @Test
    fun languagePickerUpdatesScreensAndSurvivesActivityRecreation() {
        dismissBlockingSystemDialogs()
        val settings = DefaultSettingsRepository(
            composeRule.activity.applicationContext, DefaultScanlyDispatchers(),
        )
        // ARM-only LiteRT calibration cannot run reliably under x86 ARM translation.
        // Keep this test focused on locales; restore the original setting after closing the UI.
        val automaticModels = runBlocking { settings.getAutomaticModelSelection() }
        runBlocking { settings.setAutomaticModelSelection(false) }
        val original = AppLocales.selectedLanguage(composeRule.activity)
        try {
            composeRule.activityRule.scenario.onActivity { AppLocales.setLanguage(it, AppLanguage.ENGLISH) }
            composeRule.waitForIdle()
            composeRule.completeOnboardingIfNeeded()
            val languages = listOf(
                AppLanguage.SPANISH, AppLanguage.GERMAN, AppLanguage.HINDI, AppLanguage.TAMIL,
                AppLanguage.MALAYALAM, AppLanguage.JAPANESE, AppLanguage.RUSSIAN,
            )
            languages.forEach { language ->
                composeRule.openDestination(ScanlyTestTags.NAV_SETTINGS, ScanlyTestTags.SETTINGS_SCREEN)
                composeRule.onNodeWithText(appString(R.string.settings_language))
                    .performScrollTo().performClick()
                composeRule.onNodeWithText(language.nativeName).performScrollTo()
                val activity = composeRule.activity
                val window = activity.window
                val scrollBefore = languageScrollPosition()
                composeRule.onNodeWithText(language.nativeName).performClick()
                composeRule.waitUntil(15_000) {
                    AppLocales.selectedLanguage(composeRule.activity) == language &&
                        appString(R.string.settings_language) == localizedTitle(language.locale)
                }
                composeRule.waitForIdle()
                assertSame("Language selection replaced the activity", activity, composeRule.activity)
                assertSame("Language selection replaced the window", window, composeRule.activity.window)
                assertEquals("Language selection reset the list", scrollBefore, languageScrollPosition(), 0.001f)
                val title = localizedTitle(language.locale)
                composeRule.onNodeWithText(title).assertIsDisplayed()
                assertEquals(language.tag, title, appString(R.string.settings_language))
                screenshot("${language.tag}-language")
                composeRule.activityRule.scenario.recreate()
                composeRule.waitForIdle()
                assertEquals(language, AppLocales.selectedLanguage(composeRule.activity))
                assertEquals(title, appString(R.string.settings_language))
                // Resource lookups are immediate; the restored NavHost must also finish loading
                // before Back is sent, otherwise it can finish the activity during startup.
                composeRule.waitUntil(20_000) {
                    composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
                }
                composeRule.onNodeWithText(title).assertIsDisplayed()
                // Back from the picker, then exercise the stable routes in the chosen language.
                androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
                listOf(
                    ScanlyTestTags.NAV_HOME to ScanlyTestTags.HOME_SCREEN,
                    ScanlyTestTags.NAV_LIBRARY to ScanlyTestTags.LIBRARY_SCREEN,
                    ScanlyTestTags.NAV_TOOLS to ScanlyTestTags.TOOLS_SCREEN,
                ).forEach { (nav, screen) ->
                    composeRule.openDestination(nav, screen)
                    composeRule.onNodeWithTag(screen).assertIsDisplayed()
                    if (screen == ScanlyTestTags.HOME_SCREEN) {
                        listOf(R.string.home_scan, R.string.home_import, R.string.home_folder).forEach {
                            assertTextFitsVertically(appString(it))
                        }
                    }
                    if (screen == ScanlyTestTags.LIBRARY_SCREEN) {
                        assertTextFitsVertically(appString(R.string.group_detail_new_document))
                    }
                    screenshot("${language.tag}-$screen")
                }
                composeRule.onNodeWithText(appString(R.string.tools_catalog_read_pdf))
                    .performScrollTo().performClick()
                composeRule.waitForIdle()
                screenshot("${language.tag}-pdf-reader")
                androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
            }
        } finally {
            composeRule.activityRule.scenario.onActivity { AppLocales.setLanguage(it, original) }
            composeRule.waitForIdle()
            composeRule.activityRule.scenario.close()
            runBlocking { settings.setAutomaticModelSelection(automaticModels) }
        }
    }

    private fun localizedTitle(locale: Locale): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(locale))
        }).getString(R.string.settings_language)
    }

    private fun languageScrollPosition(): Float = composeRule.onNodeWithTag("settings_language_list")
        .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    @Test
    fun systemDefaultAndPlatformSelectionUpdateTheExistingComposition() {
        val activity = composeRule.activity
        val original = AppLocales.selectedLanguage(activity)
        try {
            composeRule.completeOnboardingIfNeeded()
            composeRule.activityRule.scenario.onActivity { AppLocales.setLanguage(it, AppLanguage.TAMIL) }
            composeRule.waitForIdle()
            assertEquals(localizedTitle(AppLanguage.TAMIL.locale), appString(R.string.settings_language))
            composeRule.onNodeWithText(appString(R.string.home_scan)).assertIsDisplayed()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                // Simulates changing the per-app language in Android Settings rather than the picker.
                composeRule.activityRule.scenario.onActivity {
                    it.getSystemService(android.app.LocaleManager::class.java).applicationLocales =
                        LocaleList.forLanguageTags(AppLanguage.JAPANESE.tag)
                }
                composeRule.waitUntil(15_000) {
                    appString(R.string.settings_language) == localizedTitle(AppLanguage.JAPANESE.locale)
                }
                composeRule.waitForIdle()
                assertSame(activity, composeRule.activity)
                composeRule.onNodeWithText(appString(R.string.home_scan)).assertIsDisplayed()
            }
            composeRule.activityRule.scenario.onActivity { AppLocales.setLanguage(it, null) }
            composeRule.waitForIdle()
            assertSame(activity, composeRule.activity)
            assertEquals(null, AppLocales.selectedLanguage(activity))
            assertEquals(localizedTitle(AppLocales.deviceLocale(activity)), appString(R.string.settings_language))
            // The actual root composition (onboarding or Home) must read the new resources too.
            composeRule.completeOnboardingIfNeeded()
            composeRule.openDestination(ScanlyTestTags.NAV_HOME, ScanlyTestTags.HOME_SCREEN)
            composeRule.onNodeWithText(appString(R.string.home_scan)).assertIsDisplayed()
        } finally {
            composeRule.activityRule.scenario.onActivity { AppLocales.setLanguage(it, original) }
            composeRule.waitForIdle()
        }
    }

    private fun screenshot(name: String, tag: String? = null) {
        composeRule.waitForIdle()
        val bitmap = if (tag == null) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        } else {
            composeRule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        }
        val directory = File(composeRule.activity.cacheDir, "i18n-qa").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun formatArguments(source: String): Array<Any> {
        val arguments = mutableMapOf<Int, Any>()
        var sequential = 0
        Regex("%(?:(\\d+)\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?([sdf%])").findAll(source).forEach { match ->
            val conversion = match.groupValues[2]
            if (conversion == "%") return@forEach
            val index = match.groupValues[1].toIntOrNull()?.minus(1) ?: sequential++
            arguments[index] = when (conversion) {
                "d" -> 2
                "f" -> 2.5
                else -> "test"
            }
        }
        return Array((arguments.keys.maxOrNull() ?: -1) + 1) { arguments[it] ?: "unused" }
    }

    private fun assertTextFitsVertically(text: String) {
        val nodes = composeRule.onAllNodesWithText(text, useUnmergedTree = true)
        val results = mutableListOf<TextLayoutResult>()
        repeat(nodes.fetchSemanticsNodes().size) { index ->
            nodes[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        }
        assertTrue("Missing text layout: $text", results.isNotEmpty())
        assertFalse("Clipped translated control: $text", results.any { it.didOverflowHeight })
    }

    @Test
    fun onboardingShowsAllThreePagesInRepresentativeLanguages() {
        val original = AppLocales.selectedLanguage(composeRule.activity)
        try {
            listOf(
                AppLanguage.SPANISH, AppLanguage.GERMAN, AppLanguage.HINDI, AppLanguage.TAMIL,
                AppLanguage.MALAYALAM, AppLanguage.JAPANESE, AppLanguage.RUSSIAN,
            ).forEach { language ->
                composeRule.activityRule.scenario.onActivity { AppLocales.setLanguage(it, language) }
                composeRule.waitUntil(15_000) {
                    AppLocales.selectedLanguage(composeRule.activity) == language
                }
                // Replace content only after the in-place locale update has settled.
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                composeRule.activityRule.scenario.recreate()
                composeRule.waitForIdle()
                composeRule.activityRule.scenario.onActivity {
                    it.setContent {
                        key(language.tag) {
                            AppLocaleProvider {
                                ScanlyTheme {
                                    OnboardingScreen(
                                        OnboardingUiState(status = OnboardingStatus.REQUIRED), {}, {},
                                    )
                                }
                            }
                        }
                    }
                }
                composeRule.waitForIdle()
                listOf(
                    R.string.onboarding_page_paper_to_polished,
                    R.string.onboarding_page_find_more_than_files,
                    R.string.onboarding_page_ready_when_you_are,
                ).forEachIndexed { index, title ->
                    composeRule.onNodeWithText(appString(title)).assertIsDisplayed()
                    screenshot("${language.tag}-onboarding-$index", "onboarding_screen")
                    if (index < 2) composeRule.onNodeWithTag("onboarding_continue").performClick()
                    composeRule.waitForIdle()
                }
                composeRule.onNodeWithTag("onboarding_get_started").assertIsDisplayed()
            }
        } finally {
            composeRule.activityRule.scenario.onActivity { AppLocales.setLanguage(it, original) }
            composeRule.waitForIdle()
        }
    }

}
