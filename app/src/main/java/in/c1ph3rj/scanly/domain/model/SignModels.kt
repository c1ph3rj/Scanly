package `in`.c1ph3rj.scanly.domain.model

/** Whether a saved mark is a full signature or a short set of initials. */
enum class SignatureKind(val storageValue: String, val label: String) {
    SIGNATURE("signature", "Signature"),
    INITIALS("initials", "Initials"),
    ;

    companion object {
        fun fromStorage(value: String?): SignatureKind =
            entries.firstOrNull { it.storageValue == value } ?: SIGNATURE
    }
}

/** How a saved signature was made. */
enum class SignatureSource(val storageValue: String) {
    DRAWN("drawn"),
    IMPORTED("imported"),
    ;

    companion object {
        fun fromStorage(value: String?): SignatureSource =
            entries.firstOrNull { it.storageValue == value } ?: DRAWN
    }
}

/**
 * A reusable signature or initials from "Your signatures".
 *
 * [imagePath] is a PNG ink mask: RGB black, alpha = ink coverage, cropped to the ink. Ink colour
 * is applied when it is placed, so one saved signature works in every [SignInk].
 */
data class SavedSignature(
    val id: String,
    val kind: SignatureKind,
    val source: SignatureSource,
    val imagePath: String,
    val widthPx: Int,
    val heightPx: Int,
    val createdAtMillis: Long,
) {
    /** Width / height of the ink image. */
    val aspectRatio: Float get() = widthPx.coerceAtLeast(1).toFloat() / heightPx.coerceAtLeast(1)
}

/**
 * A rectangle in normalised *visual* page space: fractions of the page as the reader sees it
 * (after any PDF /Rotate), origin top-left, y down.
 */
data class NormRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
    val centerX: Float get() = left + width / 2f
    val centerY: Float get() = top + height / 2f

    fun offsetBy(dx: Float, dy: Float): NormRect = copy(left = left + dx, top = top + dy)

    /** Keeps the whole rect on the page, shrinking it first if it is larger than the page. */
    fun clampedToPage(): NormRect {
        val w = width.coerceIn(MinSide, 1f)
        val h = height.coerceIn(MinSide, 1f)
        return NormRect(
            left = left.coerceIn(0f, 1f - w),
            top = top.coerceIn(0f, 1f - h),
            width = w,
            height = h,
        )
    }

    companion object {
        const val MinSide = 0.005f

        fun centeredAt(centerX: Float, centerY: Float, width: Float, height: Float): NormRect =
            NormRect(centerX - width / 2f, centerY - height / 2f, width, height)
    }
}

/** Ink colours offered for placed marks. Navy is the default, like a ballpoint pen. */
enum class SignInk(val storageValue: String, val label: String, val argb: Long) {
    NAVY("navy", "Navy", 0xFF14286B),
    BLACK("black", "Black", 0xFF111827),
    BLUE("blue", "Blue", 0xFF1D4ED8),
    RED("red", "Red", 0xFFB91C1C),
    ;

    val red: Float get() = ((argb shr 16) and 0xFF) / 255f
    val green: Float get() = ((argb shr 8) and 0xFF) / 255f
    val blue: Float get() = (argb and 0xFF) / 255f

    companion object {
        val Default: SignInk = NAVY

        fun fromStorage(value: String?): SignInk = entries.firstOrNull { it.storageValue == value } ?: Default
    }
}

/**
 * One item placed on a page while signing. Every renderer (editor overlay, PDF stamper, page
 * image compositor) draws a mark only from these fields, so the editor and the output agree.
 */
sealed interface SignMark {
    val id: String
    val pageIndex: Int
    val box: NormRect
    val ink: SignInk

    fun withBox(box: NormRect): SignMark
    fun withInk(ink: SignInk): SignMark
    fun withId(id: String): SignMark

    /** A signature or initials image (ink mask, see [SavedSignature.imagePath]). */
    data class Image(
        override val id: String,
        override val pageIndex: Int,
        override val box: NormRect,
        override val ink: SignInk,
        val imagePath: String,
        /** Width / height of the image; the box keeps this ratio on the page. */
        val aspectRatio: Float,
        val kind: SignatureKind,
    ) : SignMark {
        override fun withBox(box: NormRect) = copy(box = box)
        override fun withInk(ink: SignInk) = copy(ink = ink)
        override fun withId(id: String) = copy(id = id)
    }

    /** Free text on one line. */
    data class Text(
        override val id: String,
        override val pageIndex: Int,
        override val box: NormRect,
        override val ink: SignInk,
        val text: String,
    ) : SignMark {
        override fun withBox(box: NormRect) = copy(box = box)
        override fun withInk(ink: SignInk) = copy(ink = ink)
        override fun withId(id: String) = copy(id = id)
    }

    /** A date stamp; [text] is already formatted (e.g. "13 Oct 2026") and editable. */
    data class Date(
        override val id: String,
        override val pageIndex: Int,
        override val box: NormRect,
        override val ink: SignInk,
        val text: String,
    ) : SignMark {
        override fun withBox(box: NormRect) = copy(box = box)
        override fun withInk(ink: SignInk) = copy(ink = ink)
        override fun withId(id: String) = copy(id = id)
    }

    /** A square checkbox, ticked when [checked]. Square on the page. */
    data class Checkbox(
        override val id: String,
        override val pageIndex: Int,
        override val box: NormRect,
        override val ink: SignInk,
        val checked: Boolean = true,
    ) : SignMark {
        override fun withBox(box: NormRect) = copy(box = box)
        override fun withInk(ink: SignInk) = copy(ink = ink)
        override fun withId(id: String) = copy(id = id)
    }
}

/** Text carried by a [SignMark.Text] or [SignMark.Date], or null for other marks. */
val SignMark.textContent: String?
    get() = when (this) {
        is SignMark.Text -> text
        is SignMark.Date -> text
        else -> null
    }

/**
 * Shared drawing rules so the editor overlay, the PDF stamper and the page compositor render a
 * mark identically. Sizes are fractions of the mark's box.
 */
object SignMarkStyle {
    /** Font size as a fraction of the box height; renderers shrink further if the text is wider than the box. */
    const val TextHeightFraction = 0.74f

    /** Baseline position from the top of the box, as a fraction of the box height. */
    const val TextBaselineFraction = 0.78f

    /** Tick polyline inside the checkbox box, x/y fractions with y down. */
    val CheckPoints: List<Pair<Float, Float>> = listOf(0.24f to 0.53f, 0.43f to 0.72f, 0.78f to 0.3f)

    /** Tick stroke width as a fraction of the box side. */
    const val CheckStrokeFraction = 0.11f

    /** Checkbox border stroke width as a fraction of the box side; drawn inset by half its width. */
    const val CheckboxBorderFraction = 0.07f

    /** Checkbox corner radius as a fraction of the box side. */
    const val CheckboxCornerFraction = 0.12f

    /** Font size in output units for [text] in a box of [boxWidth] × [boxHeight] given its width at size 1. */
    fun fontSizeFor(boxWidth: Float, boxHeight: Float, widthAtSizeOne: Float): Float {
        val byHeight = boxHeight * TextHeightFraction
        if (widthAtSizeOne <= 0f) return byHeight
        return minOf(byHeight, boxWidth / widthAtSizeOne)
    }
}

/** Page markups saved with a Scanly page so a stamped page stays editable. */
data class PageMarkup(
    val pageId: String,
    val marks: List<SignMark>,
    /** The clean page image the marks are composited onto. */
    val baseImagePath: String,
    val updatedAtMillis: Long,
)

/** A PDF opened for signing: resolved once to a local file so page renders are cheap. */
data class PdfSignSession(
    val file: PdfToolSource.AppFile,
    /** Visual (as displayed) page sizes in PDF points. */
    val pageSizes: List<SignPageSize>,
)

data class SignPageSize(val width: Float, val height: Float) {
    val aspectRatio: Float get() = width.coerceAtLeast(1f) / height.coerceAtLeast(1f)
}
