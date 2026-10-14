package `in`.c1ph3rj.scanly.feature.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import `in`.c1ph3rj.scanly.ui.theme.LocalPureBlack
import `in`.c1ph3rj.scanly.ui.theme.liftedForPureBlackSheet

/**
 * [ModalBottomSheet] that stays visibly raised in the pure-black theme, where the default sheet
 * colours are indistinguishable from the black screen behind (and a scrim can't dim black).
 *
 * In pure black the sheet *and its content* use a lifted colour scheme, so the colours given
 * here are resolved inside it — that's why [containerColor] is a lambda. Light and regular dark
 * themes are unchanged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanlyModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    sheetGesturesEnabled: Boolean = true,
    containerColor: @Composable () -> Color = { MaterialTheme.colorScheme.surfaceContainerLow },
    tonalElevation: Dp = 0.dp,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    content: @Composable ColumnScope.() -> Unit,
) {
    val pureBlack = LocalPureBlack.current
    val scheme = MaterialTheme.colorScheme.let { if (pureBlack) it.liftedForPureBlackSheet() else it }
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState,
            sheetGesturesEnabled = sheetGesturesEnabled,
            containerColor = containerColor(),
            tonalElevation = tonalElevation,
            // A darker scrim helps the raised sheet read against busy content behind it.
            scrimColor = if (pureBlack) Color.Black.copy(alpha = 0.6f) else BottomSheetDefaults.ScrimColor,
            dragHandle = dragHandle,
            content = content,
        )
    }
}
