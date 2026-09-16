package `in`.c1ph3rj.scanly.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ChromeIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val resolvedContainerColor = if (enabled) {
        containerColor
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val resolvedContentColor = if (enabled) {
        contentColor
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
    }
    Surface(
        modifier = modifier.size(44.dp),
        color = resolvedContainerColor,
        shape = MaterialTheme.shapes.large,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = resolvedContentColor,
            )
        }
    }
}

/**
 * Compact pill used for metadata (folder, date, counts, mode).
 *
 * Sizes to content by default. When the parent bounds the chip (e.g.
 * [Modifier.weight] with `fill = false`), long labels stay on one line and
 * ellipsize — they never stretch the pill to fill leftover row space, and
 * hyphens in dates won't soft-break ("23-Jul" / "-2026").
 */
@Composable
fun MetricChip(
    label: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    border: BorderStroke? = null,
    onClick: (() -> Unit)? = null,
) {
    val shape = MaterialTheme.shapes.large
    // Non-breaking hyphens stop Android from soft-breaking "23-Jul-2026".
    val singleLineLabel = label.replace('-', '\u2011')
    val content: @Composable () -> Unit = {
        Row(
            // wrapContentWidth: chip hugs its label. Parent weight(fill=false)
            // still caps max width so long labels ellipsize instead of wrapping.
            modifier = Modifier
                .wrapContentWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = contentColor,
                )
            }
            Text(
                text = singleLineLabel,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    // Surface onClick clips the ripple to the pill; avoid Modifier.clickable bleed.
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.wrapContentWidth(),
            color = containerColor,
            contentColor = contentColor,
            shape = shape,
            border = border,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
        ) {
            content()
        }
    } else {
        Surface(
            modifier = modifier.wrapContentWidth(),
            color = containerColor,
            contentColor = contentColor,
            shape = shape,
            border = border,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
        ) {
            content()
        }
    }
}
