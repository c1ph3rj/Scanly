package `in`.c1ph3rj.scanly.core.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class WindowWidthClass { Compact, Medium, Expanded }

/**
 * Adaptive layout information derived from the current window configuration.
 *
 * Breakpoints follow Material Design 3 guidance:
 *  - Compact  : < 600 dp  — phone portrait
 *  - Medium   : 600–839 dp — tablet portrait / large phone landscape
 *  - Expanded : ≥ 840 dp  — tablet landscape / foldable / desktop
 *
 * [isTablet] is based on smallest screen width so phones rotated into landscape
 * keep compact navigation and one-pane flows.
 */
data class WindowSizeInfo(
    val widthClass: WindowWidthClass,
    val isLandscape: Boolean,
    val isTablet: Boolean,
) {
    /** Number of columns for folder/group grids. */
    val groupColumns: Int
        get() = when (widthClass) {
            WindowWidthClass.Compact  -> 2
            WindowWidthClass.Medium   -> 3
            WindowWidthClass.Expanded -> 4
        }

    /** Number of columns for page thumbnail grids. */
    val pageColumns: Int
        get() = when (widthClass) {
            WindowWidthClass.Compact  -> 1
            WindowWidthClass.Medium   -> 2
            WindowWidthClass.Expanded -> 3
        }

    /** Columns for Tools hub action grids (merge / compress / etc.). */
    val toolGridColumns: Int
        get() = when (widthClass) {
            WindowWidthClass.Compact  -> 2
            WindowWidthClass.Medium   -> 3
            WindowWidthClass.Expanded -> 4
        }

    /** Columns for PDF library picker grids inside tool sheets. */
    val pdfLibraryGridColumns: Int
        get() = when (widthClass) {
            WindowWidthClass.Compact  -> 2
            WindowWidthClass.Medium   -> 3
            WindowWidthClass.Expanded -> 4
        }

    /**
     * Maximum width for centred content columns (Settings, Legal, Home).
     * Returns [Dp.Unspecified] on compact so no cap is applied.
     */
    val contentMaxWidth: Dp
        get() = when (widthClass) {
            WindowWidthClass.Compact  -> Dp.Unspecified
            WindowWidthClass.Medium   -> 720.dp
            WindowWidthClass.Expanded -> 900.dp
        }

    /**
     * Wider content column for Tools detail workspaces (forms + previews).
     * Returns [Dp.Unspecified] on compact so no cap is applied.
     */
    val toolContentMaxWidth: Dp
        get() = when (widthClass) {
            WindowWidthClass.Compact  -> Dp.Unspecified
            WindowWidthClass.Medium   -> 840.dp
            WindowWidthClass.Expanded -> 1000.dp
        }

    /**
     * Max width for primary bottom CTAs (Merge / Compress / etc.).
     * Prevents edge-to-edge “sausage” buttons on tablets.
     */
    val toolPrimaryActionMaxWidth: Dp
        get() = when (widthClass) {
            WindowWidthClass.Compact  -> Dp.Unspecified
            WindowWidthClass.Medium   -> 420.dp
            WindowWidthClass.Expanded -> 400.dp
        }

    /** Comfortable max width for single-column lists (merge file list, forms). */
    val toolFormMaxWidth: Dp
        get() = when (widthClass) {
            WindowWidthClass.Compact  -> Dp.Unspecified
            WindowWidthClass.Medium   -> 560.dp
            WindowWidthClass.Expanded -> 520.dp
        }

    /** Height for PDF first-page previews inside tool Ready states. */
    val toolPreviewHeight: Dp
        get() = when {
            // Landscape: keep preview compact so controls fit above the bottom CTA.
            isLandscape && widthClass != WindowWidthClass.Compact -> 220.dp
            widthClass == WindowWidthClass.Compact -> 240.dp
            widthClass == WindowWidthClass.Medium -> 280.dp
            else -> 320.dp
        }

    /**
     * Side-by-side tool layouts (preview | controls, camera | result).
     * Used on tablets and on medium/expanded widths with enough horizontal room.
     */
    val useToolTwoPaneLayout: Boolean
        get() = widthClass != WindowWidthClass.Compact &&
            (isTablet || widthClass == WindowWidthClass.Expanded)

    /** Maximum width for modal dialogs on large screens. */
    val dialogMaxWidth: Dp
        get() = when (widthClass) {
            WindowWidthClass.Compact  -> 560.dp
            WindowWidthClass.Medium   -> 520.dp
            WindowWidthClass.Expanded -> 480.dp
        }

    /** Maximum width for bottom-sheet content on large screens. */
    val sheetMaxWidth: Dp
        get() = when (widthClass) {
            WindowWidthClass.Compact  -> Dp.Unspecified
            WindowWidthClass.Medium   -> 640.dp
            WindowWidthClass.Expanded -> 560.dp
        }

    /** Horizontal screen-edge padding for scrollable lists. */
    val horizontalPadding: Dp
        get() = when (widthClass) {
            WindowWidthClass.Compact  -> 20.dp
            WindowWidthClass.Medium   -> 32.dp
            WindowWidthClass.Expanded -> 48.dp
        }

    /**
     * True when the device is a tablet in landscape orientation — triggers
     * side-by-side / two-pane layouts in the editor and document detail.
     */
    val useTabletLandscapeLayout: Boolean
        get() = isTablet && isLandscape

    /** True for phones in landscape: wide, but still height-constrained. */
    val useCompactLandscapeLayout: Boolean
        get() = !isTablet && isLandscape
}

/** Reads current window metrics from [LocalConfiguration] without any extra dependency. */
@Composable
fun rememberWindowSizeInfo(): WindowSizeInfo {
    val configuration = LocalConfiguration.current
    return remember(configuration) {
        resolveWindowSizeInfo(
            screenWidthDp = configuration.screenWidthDp,
            smallestScreenWidthDp = configuration.smallestScreenWidthDp,
            isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
        )
    }
}

internal fun resolveWindowSizeInfo(
    screenWidthDp: Int,
    smallestScreenWidthDp: Int,
    isLandscape: Boolean,
): WindowSizeInfo {
    val widthClass = when {
        screenWidthDp < 600 -> WindowWidthClass.Compact
        screenWidthDp < 840 -> WindowWidthClass.Medium
        else -> WindowWidthClass.Expanded
    }
    return WindowSizeInfo(
        widthClass = widthClass,
        isLandscape = isLandscape,
        isTablet = smallestScreenWidthDp >= 600,
    )
}
