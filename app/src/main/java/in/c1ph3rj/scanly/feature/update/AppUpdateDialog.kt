package `in`.c1ph3rj.scanly.feature.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import `in`.c1ph3rj.scanly.core.common.StorageFormatter
import `in`.c1ph3rj.scanly.domain.model.AppRelease
import `in`.c1ph3rj.scanly.domain.model.AppUpdateCheckResult
import `in`.c1ph3rj.scanly.feature.components.ScanlyFormDialogShell
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val releaseDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

@Composable
fun AppUpdateDialog(
    checkResult: AppUpdateCheckResult,
    isDownloading: Boolean,
    onDismiss: () -> Unit,
    onDownload: (AppRelease) -> Unit,
    modifier: Modifier = Modifier,
) {
    val release = checkResult.latestRelease

    ScanlyFormDialogShell(
        onDismiss = onDismiss,
        modifier = modifier,
        maxWidth = 520.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .padding(10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.NewReleases,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Update available",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = release.tagName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        ReleaseSummary(
            currentVersion = checkResult.installedVersionName,
            release = release,
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "What's new",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    ReleaseMarkdown(
                        bodyMarkdown = release.displayBodyMarkdown(),
                        compact = true,
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(
                onClick = { onDownload(release) },
                enabled = !isDownloading,
                modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Text(
                        text = "Downloading…",
                        modifier = Modifier.padding(start = 8.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = null,
                    )
                    Text(
                        text = if (release.apkAsset != null) {
                            "Download & install"
                        } else {
                            "Open release"
                        },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            TextButton(
                onClick = onDismiss,
                enabled = !isDownloading,
            ) {
                Text(text = "Not now")
            }
        }
    }
}

@Composable
private fun ReleaseSummary(
    currentVersion: String,
    release: AppRelease,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VersionColumn(
                    label = "Current",
                    value = versionLabel(currentVersion),
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                VersionColumn(
                    label = "Latest",
                    value = release.tagName,
                    modifier = Modifier.weight(1f),
                    alignEnd = true,
                )
            }

            val publishedLabel = release.publishedDateLabel()?.let { "Published $it" }
            val apkSizeLabel = release.apkAsset?.sizeBytes?.let { sizeBytes ->
                "APK ${StorageFormatter.formatBytes(sizeBytes)}"
            } ?: if (release.apkAsset == null) {
                "APK asset unavailable"
            } else {
                null
            }

            if (publishedLabel != null || apkSizeLabel != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = publishedLabel.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (apkSizeLabel != null) {
                        Text(
                            text = apkSizeLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun AppRelease.displayBodyMarkdown(): String {
    val lines = bodyMarkdown.lines()
    val firstContentIndex = lines.indexOfFirst { it.isNotBlank() }
    if (firstContentIndex < 0) return bodyMarkdown

    val firstLine = lines[firstContentIndex].trim()
    val headingMatch = headingLineRegex.matchEntire(firstLine) ?: return bodyMarkdown

    val headingText = headingMatch.groupValues[2].trim()
    val duplicatesTitle = headingText.equals(title, ignoreCase = true) ||
        headingText.contains(tagName, ignoreCase = true)
    if (!duplicatesTitle) return bodyMarkdown

    return lines
        .drop(firstContentIndex + 1)
        .dropWhile { it.isBlank() }
        .joinToString("\n")
}

private fun AppRelease.publishedDateLabel(): String? =
    publishedAt?.let { rawDate ->
        runCatching {
            OffsetDateTime.parse(rawDate).format(releaseDateFormatter)
        }.getOrNull()
    }

private fun versionLabel(versionName: String): String =
    if (versionName.startsWith("v", ignoreCase = true)) {
        versionName
    } else {
        "v$versionName"
    }

private val headingLineRegex = Regex("^(#{1,6})\\s+(.+)$")
