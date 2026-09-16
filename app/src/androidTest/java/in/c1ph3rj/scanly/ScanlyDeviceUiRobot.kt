package `in`.c1ph3rj.scanly

import android.os.Build
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import `in`.c1ph3rj.scanly.core.ui.ScanlyTestTags

private const val DEFAULT_TIMEOUT_MS = 20_000L

internal fun dismissBlockingSystemDialogs() {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    device.waitForIdle(1_500)
    val labels = buildList {
        add("While using the app")
        add("Allow only while using the app")
        add("Allow")
        if (Build.VERSION.SDK_INT >= 33) {
            add("Allow notifications")
        }
        add("OK")
        add("Got it")
        add("Close")
    }
    labels.forEach { label ->
        val dialog = device.findObject(UiSelector().text(label))
        if (dialog.exists()) {
            dialog.click()
            device.waitForIdle(400)
        }
    }
}

internal fun SemanticsNodeInteractionsProvider.hasTag(tag: String): Boolean =
    onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() ||
        onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

@OptIn(ExperimentalTestApi::class)
internal fun ComposeTestRule.awaitTag(tag: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
    waitUntil(timeoutMs) { hasTag(tag) }
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeTestRule.awaitText(text: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
    waitUntil(timeoutMs) {
        onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
}

internal fun ComposeTestRule.completeOnboardingIfNeeded() {
    waitUntil(DEFAULT_TIMEOUT_MS) {
        hasTag(ScanlyTestTags.ONBOARDING_GET_STARTED) ||
            hasTag(ScanlyTestTags.HOME_SCREEN) ||
            hasTag(ScanlyTestTags.NAV_HOME)
    }
    if (hasTag(ScanlyTestTags.ONBOARDING_GET_STARTED)) {
        onNodeWithTag(ScanlyTestTags.ONBOARDING_GET_STARTED).performClick()
    }
    dismissBlockingSystemDialogs()
    waitUntil(DEFAULT_TIMEOUT_MS) {
        hasTag(ScanlyTestTags.HOME_SCREEN) || hasTag(ScanlyTestTags.NAV_HOME)
    }
}

internal fun ComposeTestRule.tapNav(navTag: String, screenTag: String) {
    waitUntil(DEFAULT_TIMEOUT_MS) { hasTag(navTag) }
    if (onAllNodesWithTag(navTag).fetchSemanticsNodes().isNotEmpty()) {
        onNodeWithTag(navTag).performClick()
    } else {
        onNodeWithTag(navTag, useUnmergedTree = true).performClick()
    }
    dismissBlockingSystemDialogs()
    awaitTag(screenTag)
    onNodeWithTag(screenTag).assertIsDisplayed()
}

internal fun ComposeTestRule.openDestination(navTag: String, screenTag: String) {
    completeOnboardingIfNeeded()
    tapNav(navTag, screenTag)
}

internal fun ComposeTestRule.generateQrFromTools(content: String) {
    openDestination(ScanlyTestTags.NAV_TOOLS, ScanlyTestTags.TOOLS_SCREEN)
    onNodeWithText("QR CODE").performClick()
    awaitText("Generate a code")
    onNodeWithText("Generate a code").performClick()
    awaitText("Create a QR code")
    awaitTag(ScanlyTestTags.QR_GENERATE_INPUT)
    onNodeWithTag(ScanlyTestTags.QR_GENERATE_INPUT).performTextReplacement(content)
    waitUntil(DEFAULT_TIMEOUT_MS) {
        onAllNodesWithContentDescription("QR code preview")
            .fetchSemanticsNodes()
            .isNotEmpty()
    }
    onNodeWithContentDescription("QR code preview").assertIsDisplayed()
}
