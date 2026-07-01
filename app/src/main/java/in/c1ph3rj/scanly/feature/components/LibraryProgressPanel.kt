package `in`.c1ph3rj.scanly.feature.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import `in`.c1ph3rj.scanly.domain.model.LibraryStartupStatus

enum class LibraryProgressStepState {
    PENDING,
    ACTIVE,
    DONE,
}

data class LibraryProgressStep(
    val label: String,
    val state: LibraryProgressStepState,
)

@Composable
fun LibraryStartupProgressPanel(
    status: LibraryStartupStatus,
    displayPath: String?,
    modifier: Modifier = Modifier,
) {
    if (status == LibraryStartupStatus.UNSUPPORTED_LIBRARY_VERSION) {
        LibraryProgressMessage(
            icon = Icons.Filled.FolderOpen,
            title = "Update required",
            subtitle = "This library needs a newer version of Scanly. Install the latest update and try again.",
            modifier = modifier,
        )
        return
    }

    LibraryProgressPanel(
        icon = Icons.Filled.FolderOpen,
        title = startupProgressTitle(status),
        subtitle = startupProgressSubtitle(status, displayPath),
        steps = startupProgressSteps(status),
        footnote = "This may take a moment on large libraries.",
        modifier = modifier,
    )
}

@Composable
fun LibraryMaintenanceProgressPanel(
    title: String,
    subtitle: String,
    steps: List<LibraryProgressStep>,
    footnote: String = "This may take a moment on large libraries.",
    modifier: Modifier = Modifier,
) {
    LibraryProgressPanel(
        icon = Icons.Filled.Refresh,
        title = title,
        subtitle = subtitle,
        steps = steps,
        footnote = footnote,
        modifier = modifier,
    )
}

@Composable
private fun LibraryProgressMessage(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProgressIconBadge(icon = icon)
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LibraryProgressPanel(
    icon: ImageVector,
    title: String,
    subtitle: String,
    steps: List<LibraryProgressStep>,
    footnote: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProgressIconBadge(icon = icon)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                steps.forEach { step ->
                    LibraryProgressStepRow(step = step)
                }
            }
        }
        Text(
            text = footnote,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ProgressIconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(72.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(34.dp),
        )
    }
}

@Composable
private fun LibraryProgressStepRow(
    step: LibraryProgressStep,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (step.state) {
            LibraryProgressStepState.DONE -> Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            LibraryProgressStepState.ACTIVE -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            LibraryProgressStepState.PENDING -> Icon(
                imageVector = Icons.Outlined.Circle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = step.label,
            style = MaterialTheme.typography.bodyMedium,
            color = when (step.state) {
                LibraryProgressStepState.ACTIVE -> MaterialTheme.colorScheme.onSurface
                LibraryProgressStepState.DONE -> MaterialTheme.colorScheme.onSurfaceVariant
                LibraryProgressStepState.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
            },
            fontWeight = if (step.state == LibraryProgressStepState.ACTIVE) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

fun startupProgressTitle(status: LibraryStartupStatus): String = when (status) {
    LibraryStartupStatus.RECOVERING_OPERATIONS -> "Recovering your library"
    LibraryStartupStatus.REBUILDING_DATABASE -> "Refreshing your library"
    LibraryStartupStatus.APPLYING_DELTA -> "Setting up your library"
    LibraryStartupStatus.CHECKING -> "Connecting to your folder"
    else -> "Opening your Scanly library"
}

fun startupProgressSubtitle(status: LibraryStartupStatus, displayPath: String?): String {
    val pathHint = displayPath?.let { "Location: $it" }
    val statusMessage = when (status) {
        LibraryStartupStatus.RECOVERING_OPERATIONS ->
            "Finishing changes that were interrupted the last time Scanly ran."
        LibraryStartupStatus.REBUILDING_DATABASE ->
            "Scanly is rescanning your folder so every document and thumbnail is up to date."
        LibraryStartupStatus.APPLYING_DELTA ->
            "Scanly is reading your folder and preparing your documents for the app."
        LibraryStartupStatus.CHECKING ->
            "Verifying access to the folder you selected."
        else -> "Hang tight while Scanly gets everything ready."
    }
    return listOfNotNull(statusMessage, pathHint).joinToString("\n")
}

fun startupProgressSteps(status: LibraryStartupStatus): List<LibraryProgressStep> {
    val activeIndex = when (status) {
        LibraryStartupStatus.CHECKING -> 0
        LibraryStartupStatus.APPLYING_DELTA,
        LibraryStartupStatus.RECOVERING_OPERATIONS,
        -> 1
        LibraryStartupStatus.REBUILDING_DATABASE -> 2
        else -> 0
    }
    val labels = listOf(
        "Connect to your folder",
        "Read documents and pages",
        "Prepare your library",
    )
    return labels.mapIndexed { index, label ->
        LibraryProgressStep(
            label = label,
            state = when {
                index < activeIndex -> LibraryProgressStepState.DONE
                index == activeIndex -> LibraryProgressStepState.ACTIVE
                else -> LibraryProgressStepState.PENDING
            },
        )
    }
}
