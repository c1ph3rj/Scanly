package `in`.c1ph3rj.scanly.domain.model

/**
 * User-facing post-filter tweaks applied after a [PageFilterPreset] render.
 * Values are normalized roughly in `[-1, 1]` (sharpness in `[0, 1]`).
 */
data class PageFilterAdjustments(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val sharpness: Float = 0f,
) {
    val isDefault: Boolean
        get() = brightness == 0f &&
            contrast == 0f &&
            saturation == 0f &&
            sharpness == 0f

    fun sanitized(): PageFilterAdjustments = copy(
        brightness = brightness.coerceIn(MIN_DELTA, MAX_DELTA),
        contrast = contrast.coerceIn(MIN_DELTA, MAX_DELTA),
        saturation = saturation.coerceIn(MIN_DELTA, MAX_DELTA),
        sharpness = sharpness.coerceIn(0f, MAX_DELTA),
    )

    companion object {
        val Default = PageFilterAdjustments()

        const val MIN_DELTA = -1f
        const val MAX_DELTA = 1f

        /** UI sliders use -100..100 (sharpness 0..100). */
        fun fromUiPercents(
            brightness: Int,
            contrast: Int,
            saturation: Int,
            sharpness: Int,
        ): PageFilterAdjustments = PageFilterAdjustments(
            brightness = brightness.coerceIn(-100, 100) / 100f,
            contrast = contrast.coerceIn(-100, 100) / 100f,
            saturation = saturation.coerceIn(-100, 100) / 100f,
            sharpness = sharpness.coerceIn(0, 100) / 100f,
        ).sanitized()
    }

    fun brightnessPercent(): Int = (brightness * 100f).toInt().coerceIn(-100, 100)
    fun contrastPercent(): Int = (contrast * 100f).toInt().coerceIn(-100, 100)
    fun saturationPercent(): Int = (saturation * 100f).toInt().coerceIn(-100, 100)
    fun sharpnessPercent(): Int = (sharpness * 100f).toInt().coerceIn(0, 100)
}
