package `in`.c1ph3rj.scanly.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

internal const val TARGET_PACKAGE = "in.c1ph3rj.scanly"

// Compose test tags, exposed as resource ids by MainActivity (testTagsAsResourceId).
private const val HOME_SCREEN = "home_screen"
private const val LIBRARY_SCREEN = "library_screen"
private const val TOOLS_SCREEN = "tools_screen"
private const val SETTINGS_SCREEN = "settings_screen"
private const val LIBRARY_SEARCH_BUTTON = "library_search"
private const val SEARCH_SCREEN = "search_screen"

private const val WAIT_MILLIS = 10_000L

/** Starts the app and leaves it on Home, stepping through first-run onboarding if it shows. */
internal fun MacrobenchmarkScope.startOnHome() {
    pressHome()
    startActivityAndWait()
    // Onboarding: Skip jumps to the last page, its button opens the camera sheet, "later" on
    // that sheet ends on Home. Each step is only taken if its control is on screen.
    val onboardingSteps = listOf("onboarding_camera_later", "onboarding_get_started", "onboarding_skip")
    repeat(8) {
        if (device.hasObject(By.res(HOME_SCREEN))) return
        val control = onboardingSteps.firstNotNullOfOrNull { device.findObject(By.res(it)) }
        if (control != null) {
            control.click()
            device.waitForIdle()
        } else {
            device.wait(Until.hasObject(By.res(HOME_SCREEN)), 1_500L)
        }
    }
    check(device.wait(Until.hasObject(By.res(HOME_SCREEN)), WAIT_MILLIS)) { "Home did not appear." }
}

/**
 * The navigation the profile and the frame benchmarks cover: every tab, one pushed screen and
 * Back. It runs on an empty library, so it needs no test data. Extend it with a document flow
 * (open, page preview, editor) once the tests can seed a document.
 */
internal fun MacrobenchmarkScope.navigateTabs() {
    openTab("library", LIBRARY_SCREEN)
    device.findObject(By.res(LIBRARY_SEARCH_BUTTON))?.let { search ->
        search.click()
        if (device.wait(Until.hasObject(By.res(SEARCH_SCREEN)), WAIT_MILLIS)) {
            device.pressBack()
            // The search field takes focus: the first Back may only close the keyboard.
            if (!device.wait(Until.hasObject(By.res(LIBRARY_SCREEN)), 2_000L)) device.pressBack()
            device.wait(Until.hasObject(By.res(LIBRARY_SCREEN)), WAIT_MILLIS)
        }
    }
    openTab("tools", TOOLS_SCREEN)
    openTab("settings", SETTINGS_SCREEN)
    openTab("home", HOME_SCREEN)
}

private fun MacrobenchmarkScope.openTab(route: String, screenTag: String) {
    val tab = checkNotNull(device.wait(Until.findObject(By.res("scanly_nav_$route")), WAIT_MILLIS)) {
        "Tab '$route' not found."
    }
    tab.click()
    check(device.wait(Until.hasObject(By.res(screenTag)), WAIT_MILLIS)) { "Screen '$screenTag' did not appear." }
    device.waitForIdle()
}
