package `in`.c1ph3rj.scanly.core.processing

import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset

internal object AdaptivePageFilterTuning {
    /**
     * Chooses a concrete filter for Auto. Prefer readable scan-like output:
     * white paper (Clean), clear text (Soft B&W), real color (Enhanced), and only
     * fall back to flat Grayscale when nothing stronger is justified.
     */
    internal fun automatic(profile: PageImageProfile?): PageFilterPreset {
        profile ?: return PageFilterPreset.CLEAN

        val lowContent = profile.textDensity < 0.008 &&
            profile.edgeDensity < 0.02 &&
            profile.colorRatio < 0.01
        val receiptLike = profile.aspectRatio >= 1.7 &&
            profile.textDensity >= 0.015 &&
            profile.colorRatio < 0.025 &&
            profile.saturation < 30.0
        // Require real chromatic area so warm tungsten light doesn't look like "color".
        val carriesUsefulColor = profile.colorRatio >= 0.022 ||
            (profile.colorRatio >= 0.012 && profile.saturation >= 34.0)
        val strongShadows = profile.shadowRatio >= 0.15 || profile.backgroundUnevenness >= 16.0
        val unevenBackground = profile.backgroundUnevenness >= 12.0 || profile.shadowRatio >= 0.12
        val textHeavy = profile.textDensity >= 0.025 || profile.edgeDensity >= 0.055
        val veryTextHeavy = profile.textDensity >= 0.055 || profile.edgeDensity >= 0.09
        val difficultLighting = profile.brightness < 125.0 || profile.contrast < 22.0
        val wellLitPaper = profile.brightness >= 150.0 &&
            profile.contrast >= 26.0 &&
            !strongShadows

        return when {
            lowContent && !unevenBackground -> PageFilterPreset.ORIGINAL
            lowContent -> PageFilterPreset.SHADOW_REDUCTION
            receiptLike -> PageFilterPreset.RECEIPT
            // Strong cast shadows on color pages need dedicated shadow lift.
            carriesUsefulColor && strongShadows -> PageFilterPreset.SHADOW_REDUCTION
            // Mild unevenness: keep color rather than desaturating via shadow reduction.
            carriesUsefulColor && unevenBackground -> PageFilterPreset.ENHANCED_COLOR
            carriesUsefulColor -> PageFilterPreset.ENHANCED_COLOR
            // Dense mono text on clean lighting → soft B&W for crisp glyphs.
            veryTextHeavy && wellLitPaper -> PageFilterPreset.SOFT_BLACK_AND_WHITE
            // Text pages with shadows / dim light → clean paper (white background).
            textHeavy && (unevenBackground || difficultLighting) -> PageFilterPreset.CLEAN
            // Ordinary text documents: clean white paper reads better than flat grayscale.
            textHeavy && wellLitPaper -> PageFilterPreset.CLEAN
            textHeavy -> PageFilterPreset.GRAYSCALE
            // Mild content without strong text metrics: gentle clean-up.
            unevenBackground -> PageFilterPreset.CLEAN
            else -> PageFilterPreset.CLEAN
        }
    }
}
