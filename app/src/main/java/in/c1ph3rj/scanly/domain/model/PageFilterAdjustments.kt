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
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val warmth: Float = 0f,
    val vignette: Float = 0f,
) {
    val isDefault: Boolean
        get() = brightness == 0f &&
            contrast == 0f &&
            saturation == 0f &&
            sharpness == 0f &&
            highlights == 0f &&
            shadows == 0f &&
            warmth == 0f &&
            vignette == 0f

    fun sanitized(): PageFilterAdjustments = copy(
        brightness = brightness.coerceIn(MIN_DELTA, MAX_DELTA),
        contrast = contrast.coerceIn(MIN_DELTA, MAX_DELTA),
        saturation = saturation.coerceIn(MIN_DELTA, MAX_DELTA),
        sharpness = sharpness.coerceIn(0f, MAX_DELTA),
        highlights = highlights.coerceIn(MIN_DELTA, MAX_DELTA),
        shadows = shadows.coerceIn(MIN_DELTA, MAX_DELTA),
        warmth = warmth.coerceIn(MIN_DELTA, MAX_DELTA),
        vignette = vignette.coerceIn(0f, MAX_DELTA),
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
            highlights: Int = 0,
            shadows: Int = 0,
            warmth: Int = 0,
            vignette: Int = 0,
        ): PageFilterAdjustments = PageFilterAdjustments(
            brightness = brightness.coerceIn(-100, 100) / 100f,
            contrast = contrast.coerceIn(-100, 100) / 100f,
            saturation = saturation.coerceIn(-100, 100) / 100f,
            sharpness = sharpness.coerceIn(0, 100) / 100f,
            highlights = highlights.coerceIn(-100, 100) / 100f,
            shadows = shadows.coerceIn(-100, 100) / 100f,
            warmth = warmth.coerceIn(-100, 100) / 100f,
            vignette = vignette.coerceIn(0, 100) / 100f,
        ).sanitized()
    }

    fun brightnessPercent(): Int = (brightness * 100f).toInt().coerceIn(-100, 100)
    fun contrastPercent(): Int = (contrast * 100f).toInt().coerceIn(-100, 100)
    fun saturationPercent(): Int = (saturation * 100f).toInt().coerceIn(-100, 100)
    fun sharpnessPercent(): Int = (sharpness * 100f).toInt().coerceIn(0, 100)
    fun highlightsPercent(): Int = (highlights * 100f).toInt().coerceIn(-100, 100)
    fun shadowsPercent(): Int = (shadows * 100f).toInt().coerceIn(-100, 100)
    fun warmthPercent(): Int = (warmth * 100f).toInt().coerceIn(-100, 100)
    fun vignettePercent(): Int = (vignette * 100f).toInt().coerceIn(0, 100)
}
