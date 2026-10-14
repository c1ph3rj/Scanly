package `in`.c1ph3rj.scanly.domain.model

import kotlin.math.roundToInt

/**
 * User-facing post-filter tweaks applied after a [PageFilterPreset] render.
 * Values are normalized roughly in `[-1, 1]` (sharpness in `[0, 1]`).
 * [intensity] scales how strongly the preset is applied, in `[0, 1.5]` (default `1`).
 */
data class PageFilterAdjustments(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val sharpness: Float = 0f,
    val intensity: Float = 1f,
) {
    val isDefault: Boolean
        get() = brightness == 0f &&
            contrast == 0f &&
            saturation == 0f &&
            sharpness == 0f &&
            intensity == 1f

    fun sanitized(): PageFilterAdjustments = copy(
        brightness = brightness.coerceIn(MIN_DELTA, MAX_DELTA),
        contrast = contrast.coerceIn(MIN_DELTA, MAX_DELTA),
        saturation = saturation.coerceIn(MIN_DELTA, MAX_DELTA),
        sharpness = sharpness.coerceIn(0f, MAX_DELTA),
        intensity = intensity.coerceIn(MIN_INTENSITY, MAX_INTENSITY),
    )

    companion object {
        val Default = PageFilterAdjustments()

        const val MIN_DELTA = -1f
        const val MAX_DELTA = 1f

        /** Filter strength range; `1f` is the preset as designed. */
        const val MIN_INTENSITY = 0f
        const val MAX_INTENSITY = 1.5f

        /** UI sliders use -100..100 (sharpness 0..100, intensity 0..150). */
        fun fromUiPercents(
            brightness: Int,
            contrast: Int,
            saturation: Int,
            sharpness: Int,
            intensity: Int = 100,
        ): PageFilterAdjustments = PageFilterAdjustments(
            brightness = brightness.coerceIn(-100, 100) / 100f,
            contrast = contrast.coerceIn(-100, 100) / 100f,
            saturation = saturation.coerceIn(-100, 100) / 100f,
            sharpness = sharpness.coerceIn(0, 100) / 100f,
            intensity = intensity.coerceIn(0, 150) / 100f,
        ).sanitized()
    }

    fun brightnessPercent(): Int = (brightness * 100f).toInt().coerceIn(-100, 100)
    fun contrastPercent(): Int = (contrast * 100f).toInt().coerceIn(-100, 100)
    fun saturationPercent(): Int = (saturation * 100f).toInt().coerceIn(-100, 100)
    fun sharpnessPercent(): Int = (sharpness * 100f).toInt().coerceIn(0, 100)
    fun intensityPercent(): Int = (intensity * 100f).roundToInt().coerceIn(0, 150)
}
