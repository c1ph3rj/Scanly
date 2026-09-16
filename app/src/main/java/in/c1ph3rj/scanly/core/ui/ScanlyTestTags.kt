package `in`.c1ph3rj.scanly.core.ui

/** Semantics test tags used by connected-device UI tests. */
object ScanlyTestTags {
    const val HOME_SCREEN = "home_screen"
    const val LIBRARY_SCREEN = "library_screen"
    const val TOOLS_SCREEN = "tools_screen"
    const val SETTINGS_SCREEN = "settings_screen"

    const val NAV_HOME = "scanly_nav_home"
    const val NAV_LIBRARY = "scanly_nav_library"
    const val NAV_TOOLS = "scanly_nav_tools"
    const val NAV_SETTINGS = "scanly_nav_settings"

    const val QR_GENERATE_INPUT = "qr_generate_input"

    const val ONBOARDING_GET_STARTED = "onboarding_get_started"

    fun navTag(route: String): String = "scanly_nav_$route"
}
