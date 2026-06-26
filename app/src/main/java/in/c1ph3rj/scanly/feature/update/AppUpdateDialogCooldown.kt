package `in`.c1ph3rj.scanly.feature.update

internal object AppUpdateDialogCooldown {
    private const val SIX_HOURS_MILLIS = 6L * 60L * 60L * 1000L

    fun canShowAgain(lastShownAtMillis: Long?, nowMillis: Long): Boolean {
        if (lastShownAtMillis == null) return true
        return nowMillis - lastShownAtMillis >= SIX_HOURS_MILLIS
    }
}
