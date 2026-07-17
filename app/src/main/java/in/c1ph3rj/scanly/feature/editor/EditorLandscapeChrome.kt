package `in`.c1ph3rj.scanly.feature.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import `in`.c1ph3rj.scanly.core.ui.WindowSizeInfo
import `in`.c1ph3rj.scanly.core.ui.WindowWidthClass

/**
 * Shared two-pane metrics for editor, crop, filters, and adjust on wide landscape.
 *
 * Layout: large preview (left) + fixed-feel tool panel (right). Preview keeps most
 * of the width so documents stay readable on tablet landscape.
 */
data class EditorTwoPaneSpec(
    val horizontalPadding: Dp,
    val contentMaxWidth: Dp,
    val chromeMaxWidth: Dp,
    val controlsMaxWidth: Dp,
    val previewWeight: Float,
    val controlsWeight: Float,
    val paneSpacing: Dp,
)

fun WindowSizeInfo.usesEditorTwoPane(): Boolean =
    useTabletLandscapeLayout || (useToolTwoPaneLayout && isLandscape)

@Composable
fun rememberEditorTwoPaneSpec(windowSizeInfo: WindowSizeInfo): EditorTwoPaneSpec? {
    if (!windowSizeInfo.usesEditorTwoPane()) return null
    return remember(windowSizeInfo) {
        val expanded = windowSizeInfo.widthClass == WindowWidthClass.Expanded
        EditorTwoPaneSpec(
            horizontalPadding = when {
                expanded -> 28.dp
                else -> 20.dp
            },
            contentMaxWidth = if (expanded) 1280.dp else 1100.dp,
            chromeMaxWidth = if (expanded) 1280.dp else 1100.dp,
            controlsMaxWidth = if (expanded) 380.dp else 340.dp,
            previewWeight = if (expanded) 0.70f else 0.66f,
            controlsWeight = if (expanded) 0.30f else 0.34f,
            paneSpacing = if (expanded) 20.dp else 16.dp,
        )
    }
}

/** Rounded tool panel used on the right side of editor landscape layouts. */
@Composable
fun EditorToolPanel(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val scroll = rememberScrollState()
    Surface(
        modifier = modifier,
        color = colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(top = 18.dp, bottom = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true)
                    .then(
                        if (scrollable) {
                            Modifier.verticalScroll(scroll)
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

/**
 * Full-width landscape tool action — replaces cramped horizontal LazyRow chips
 * that waste space and look sparse on tablet side panels.
 */
@Composable
fun EditorRailAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    destructive: Boolean = false,
    leadingContent: (@Composable () -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val contentColor = when {
        !enabled -> colorScheme.onSurface.copy(alpha = 0.38f)
        destructive -> colorScheme.error
        emphasized -> colorScheme.primary
        else -> colorScheme.onSurface
    }
    val iconTint = when {
        !enabled -> colorScheme.onSurface.copy(alpha = 0.28f)
        destructive -> colorScheme.error
        else -> colorScheme.primary
    }
    val container = when {
        emphasized -> colorScheme.primaryContainer.copy(alpha = 0.55f)
        else -> colorScheme.surfaceContainerHigh
    }
    val border = when {
        emphasized -> colorScheme.primary.copy(alpha = 0.45f)
        destructive -> colorScheme.error.copy(alpha = 0.35f)
        else -> colorScheme.outlineVariant
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        color = container,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (leadingContent != null) {
                leadingContent()
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = label,
                    color = contentColor,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
fun EditorSideBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.widthIn(min = 88.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}
