package `in`.c1ph3rj.scanly.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.vector.ImageVector
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
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = contentColor,
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
            )
        }
    }
    // Use Surface onClick so the ripple/indication is clipped to the pill shape.
    // Modifier.clickable on a Surface lets the ripple bleed outside rounded bounds.
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
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
            modifier = modifier,
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
