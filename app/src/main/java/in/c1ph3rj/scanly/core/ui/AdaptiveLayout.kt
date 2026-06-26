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
 */
data class WindowSizeInfo(
    val widthClass: WindowWidthClass,
    val isLandscape: Boolean,
) {
    val isTablet: Boolean
        get() = widthClass != WindowWidthClass.Compact

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
}

/** Reads current window metrics from [LocalConfiguration] without any extra dependency. */
@Composable
fun rememberWindowSizeInfo(): WindowSizeInfo {
    val configuration = LocalConfiguration.current
    return remember(configuration) {
        val widthDp = configuration.screenWidthDp
        val widthClass = when {
            widthDp < 600  -> WindowWidthClass.Compact
            widthDp < 840  -> WindowWidthClass.Medium
            else           -> WindowWidthClass.Expanded
        }
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        WindowSizeInfo(widthClass = widthClass, isLandscape = isLandscape)
    }
}
