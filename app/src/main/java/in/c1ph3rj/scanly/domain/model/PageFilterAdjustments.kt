package `in`.c1ph3rj.scanly.domain.model

/**
 * Continuous, user-facing filter controls applied on top of a [PageFilterPreset].
 *
 * Ranges are normalized for persistence and pipeline math:
 * - [intensity]: 0..1 (how strongly the filtered result replaces the source)
 * - [brightness]: -1..1
 * - [contrast]: -1..1
 * - [shadows]: 0..1 (shadow / uneven-light reduction amount)
 * - [details]: 0..1 (sharpen / micro-contrast amount)
 * - [threshold]: 0..1 (ink aggressiveness for binary-like presets; higher keeps more white)
 */
data class PageFilterAdjustments(
    val intensity: Float = DEFAULT_INTENSITY,
    val brightness: Float = DEFAULT_BRIGHTNESS,
    val contrast: Float = DEFAULT_CONTRAST,
    val shadows: Float = DEFAULT_SHADOWS,
    val details: Float = DEFAULT_DETAILS,
    val threshold: Float = DEFAULT_THRESHOLD,
) {
    val isDefault: Boolean
        get() = intensity == DEFAULT_INTENSITY &&
            brightness == DEFAULT_BRIGHTNESS &&
            contrast == DEFAULT_CONTRAST &&
            shadows == DEFAULT_SHADOWS &&
            details == DEFAULT_DETAILS &&
            threshold == DEFAULT_THRESHOLD

    fun sanitized(): PageFilterAdjustments = copy(
        intensity = intensity.coerceIn(INTENSITY_MIN, INTENSITY_MAX),
        brightness = brightness.coerceIn(BRIGHTNESS_MIN, BRIGHTNESS_MAX),
        contrast = contrast.coerceIn(CONTRAST_MIN, CONTRAST_MAX),
        shadows = shadows.coerceIn(SHADOWS_MIN, SHADOWS_MAX),
        details = details.coerceIn(DETAILS_MIN, DETAILS_MAX),
        threshold = threshold.coerceIn(THRESHOLD_MIN, THRESHOLD_MAX),
    )

    fun supportsThresholdControl(preset: PageFilterPreset): Boolean =
        when (preset) {
            PageFilterPreset.BLACK_AND_WHITE,
            PageFilterPreset.SOFT_BLACK_AND_WHITE,
            PageFilterPreset.RECEIPT,
            PageFilterPreset.HIGH_CONTRAST,
            -> true
            else -> false
        }

    companion object {
        const val DEFAULT_INTENSITY = 1.0f
        const val DEFAULT_BRIGHTNESS = 0.0f
        const val DEFAULT_CONTRAST = 0.0f
        const val DEFAULT_SHADOWS = 0.5f
        const val DEFAULT_DETAILS = 0.5f
        const val DEFAULT_THRESHOLD = 0.5f

        const val INTENSITY_MIN = 0.0f
        const val INTENSITY_MAX = 1.0f
        const val BRIGHTNESS_MIN = -1.0f
        const val BRIGHTNESS_MAX = 1.0f
        const val CONTRAST_MIN = -1.0f
        const val CONTRAST_MAX = 1.0f
        const val SHADOWS_MIN = 0.0f
        const val SHADOWS_MAX = 1.0f
        const val DETAILS_MIN = 0.0f
        const val DETAILS_MAX = 1.0f
        const val THRESHOLD_MIN = 0.0f
        const val THRESHOLD_MAX = 1.0f

        val Default = PageFilterAdjustments()

        fun of(
            intensity: Float = DEFAULT_INTENSITY,
            brightness: Float = DEFAULT_BRIGHTNESS,
            contrast: Float = DEFAULT_CONTRAST,
            shadows: Float = DEFAULT_SHADOWS,
            details: Float = DEFAULT_DETAILS,
            threshold: Float = DEFAULT_THRESHOLD,
        ): PageFilterAdjustments = PageFilterAdjustments(
            intensity = intensity,
            brightness = brightness,
            contrast = contrast,
            shadows = shadows,
            details = details,
            threshold = threshold,
        ).sanitized()
    }
}
