package `in`.c1ph3rj.scanly.feature.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.domain.model.ScanMode

enum class ScanModeSelectorLayout {
    /** A compact pill of labels, for a portrait camera. */
    HORIZONTAL,

    /** A rail of icon + label rows, for the narrow side dock of a landscape camera. */
    VERTICAL,
}

/** Document / ID card / Book switcher. [dark] styles it for use over the camera. */
@Composable
fun ScanModeSelector(
    selectedMode: ScanMode,
    onModeSelected: (ScanMode) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    dark: Boolean = false,
    layout: ScanModeSelectorLayout = ScanModeSelectorLayout.HORIZONTAL,
) {
    val vertical = layout == ScanModeSelectorLayout.VERTICAL
    val container = if (dark) Color.Black.copy(alpha = 0.72f) else MaterialTheme.colorScheme.surfaceContainerHigh
    val groupDescription = stringResource(R.string.scan_mode_selector_description)
    Surface(
        modifier = modifier.semantics { contentDescription = groupDescription },
        shape = RoundedCornerShape(if (vertical) 26.dp else 24.dp),
        color = container,
        border = BorderStroke(
            1.dp,
            if (dark) Color.White.copy(alpha = 0.14f) else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        val options: @Composable () -> Unit = {
            ScanMode.entries.forEach { mode ->
                ScanModeOption(
                    mode = mode,
                    selected = mode == selectedMode,
                    enabled = enabled,
                    dark = dark,
                    vertical = vertical,
                    onClick = { onModeSelected(mode) },
                )
            }
        }
        if (vertical) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) { options() }
        } else {
            Row(
                modifier = Modifier
                    .padding(4.dp)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) { options() }
        }
    }
}

@Composable
private fun ScanModeOption(
    mode: ScanMode,
    selected: Boolean,
    enabled: Boolean,
    dark: Boolean,
    vertical: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        dark -> Color.White.copy(alpha = if (enabled) 0.76f else 0.42f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val label = stringResource(mode.labelRes)
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .then(
                if (vertical) {
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                } else {
                    Modifier.height(40.dp)
                },
            )
            .semantics {
                this.selected = selected
                role = Role.RadioButton
            },
        shape = RoundedCornerShape(if (vertical) 22.dp else 20.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = contentColor,
    ) {
        if (vertical) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = mode.selectorIcon,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                    tint = contentColor,
                )
                ModeLabel(label = label, selected = selected, color = contentColor)
            }
        } else {
            Box(
                modifier = Modifier.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                ModeLabel(label = label, selected = selected, color = contentColor)
            }
        }
    }
}

@Composable
private fun ModeLabel(label: String, selected: Boolean, color: Color) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = color,
    )
}

val ScanMode.selectorIcon: ImageVector
    get() = when (this) {
        ScanMode.DOCUMENT -> Icons.Outlined.Description
        ScanMode.ID_CARD -> Icons.Outlined.Badge
        ScanMode.BOOK -> Icons.AutoMirrored.Outlined.MenuBook
    }
