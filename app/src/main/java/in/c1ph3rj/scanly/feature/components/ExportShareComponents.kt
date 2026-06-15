package `in`.c1ph3rj.scanly.feature.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.c1ph3rj.scanly.domain.model.PdfExportOptions
import `in`.c1ph3rj.scanly.domain.model.PdfPageMargin
import `in`.c1ph3rj.scanly.domain.model.PdfPageOrientation
import `in`.c1ph3rj.scanly.domain.model.PdfPageSize

/**
 * A single tappable export/share action row used across the document and folder
 * export sheets. Optionally shows a supporting description line.
 */
@Composable
fun ExportActionRow(
    icon: ImageVector,
    title: String,
    enabled: Boolean,
    onClick: () -> Unit,
    description: String? = null,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        color = if (enabled) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f)
        },
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Bottom sheet that lets the user tune PDF layout options before generating a PDF
 * (for a single document or a whole folder).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfOptionsSheet(
    options: PdfExportOptions,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onOptionsChanged: (PdfExportOptions) -> Unit,
    onConfirm: () -> Unit,
    titleText: String = "PDF options",
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            PdfOptionSection(title = "Orientation") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PdfPageOrientation.entries.forEach { orientation ->
                        PdfChoiceTile(
                            label = orientation.label,
                            selected = options.orientation == orientation,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onOptionsChanged(options.copy(orientation = orientation))
                            },
                        )
                    }
                }
            }

            PdfOptionSection(title = "Page size") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PdfPageSize.entries.forEach { pageSize ->
                        PdfChoiceTile(
                            label = pageSize.label,
                            selected = options.pageSize == pageSize,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onOptionsChanged(options.copy(pageSize = pageSize))
                            },
                        )
                    }
                }
            }

            PdfOptionSection(title = "Margin") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PdfPageMargin.entries.forEach { margin ->
                        PdfChoiceTile(
                            label = margin.label,
                            selected = options.margin == margin,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onOptionsChanged(options.copy(margin = margin))
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss,
                ) {
                    Text(text = "Cancel")
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onConfirm,
                ) {
                    Text(text = confirmLabel)
                }
            }
        }
    }
}

@Composable
private fun PdfOptionSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}

@Composable
private fun PdfChoiceTile(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Box(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}
