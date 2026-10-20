package `in`.c1ph3rj.scanly.domain.model

/**
 * How much Pip, Scanly's mascot, shows up. [enabled] is the master switch; the others only
 * matter while it is on. [tipDismissedEpochDay] remembers the day a Home tip was closed so the
 * next tip waits until tomorrow.
 */
data class PipPreferences(
    val enabled: Boolean = true,
    val homeTips: Boolean = true,
    val cameraCoaching: Boolean = true,
    val celebrations: Boolean = true,
    val motion: Boolean = true,
    val tipDismissedEpochDay: Long = NO_DAY,
) {
    val showHomeTips: Boolean get() = enabled && homeTips
    val showCameraCoaching: Boolean get() = enabled && cameraCoaching
    val showCelebrations: Boolean get() = enabled && celebrations

    companion object {
        const val NO_DAY: Long = -1L
    }
}

enum class PipPreference {
    ENABLED,
    HOME_TIPS,
    CAMERA_COACHING,
    CELEBRATIONS,
    MOTION,
}
