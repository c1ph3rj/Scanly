package `in`.c1ph3rj.scanly.core.processing

import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset

internal object AdaptivePageFilterTuning {
    /**
     * Chooses a concrete filter for Auto. Every document preset already removes shading,
     * so routing is about content: keep real color (Magic Color), keep photos natural,
     * rescue faint ink (Faded Text), and give plain mono text a clean Grayscale page.
     * Photos of displays skip the paper model entirely (Screen).
     */
    internal fun automatic(profile: PageImageProfile?): PageFilterPreset {
        profile ?: return PageFilterPreset.MAGIC_COLOR

        val textLike = profile.textDensity >= 0.015 || profile.edgeDensity >= 0.04
        // Mostly picture: lots of chromatic area and little text — don't bleach it.
        val photoLike = profile.colorRatio >= 0.25 && profile.textDensity < 0.02
        val receiptLike = profile.aspectRatio >= 1.7 &&
            profile.textDensity >= 0.015 &&
            profile.colorRatio < 0.025 &&
            profile.saturation < 30.0
        // Darkest ink stays light even after shading is removed → pencil / thermal / faded print.
        val faintInk = textLike && profile.toneInkLevel >= FAINT_INK_LEVEL
        // Require real chromatic area so warm tungsten light doesn't look like "color".
        val carriesUsefulColor = profile.colorRatio >= 0.022 ||
            (profile.colorRatio >= 0.012 && profile.saturation >= 34.0)

        return when {
            profile.screenLikely -> PageFilterPreset.SCREEN
            photoLike -> PageFilterPreset.NATURAL_COLOR
            receiptLike || faintInk -> PageFilterPreset.FADED
            carriesUsefulColor -> PageFilterPreset.MAGIC_COLOR
            textLike -> PageFilterPreset.GRAYSCALE
            else -> PageFilterPreset.MAGIC_COLOR
        }
    }

    private const val FAINT_INK_LEVEL = 105.0
}
