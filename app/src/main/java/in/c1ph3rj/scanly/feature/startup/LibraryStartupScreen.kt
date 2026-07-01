package `in`.c1ph3rj.scanly.feature.startup

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import `in`.c1ph3rj.scanly.domain.model.LibraryAccessState
import `in`.c1ph3rj.scanly.domain.model.LibraryStartupStatus
import `in`.c1ph3rj.scanly.feature.components.LibraryStartupProgressPanel

@Composable
fun LibraryStartupScreen(
    state: LibraryAccessState,
    onChooseFolder: () -> Unit,
    onRetry: () -> Unit,
) {
    val needsFolder = state.status == LibraryStartupStatus.STORAGE_SETUP_REQUIRED ||
        state.status == LibraryStartupStatus.RECONNECT_REQUIRED ||
        state.status == LibraryStartupStatus.REPAIR_REQUIRED
    val isFirstSetup = state.status == LibraryStartupStatus.STORAGE_SETUP_REQUIRED

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (needsFolder) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp),
                    )
                    Text(
                        text = when (state.status) {
                            LibraryStartupStatus.STORAGE_SETUP_REQUIRED -> "Set up your Scanly library"
                            LibraryStartupStatus.REPAIR_REQUIRED -> "Repair your Scanly library"
                            else -> "Reconnect your Scanly library"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = state.message ?: folderPickerIntro(isFirstSetup),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (isFirstSetup) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Why choose a folder?",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            FolderBenefitRow(
                                icon = Icons.Outlined.PhoneAndroid,
                                text = "Your scans are saved as real files on your phone — not hidden inside the app.",
                            )
                            FolderBenefitRow(
                                icon = Icons.Outlined.Backup,
                                text = "They stay put when you update Scanly, and remain on your device even if you uninstall.",
                            )
                            FolderBenefitRow(
                                icon = Icons.Outlined.Folder,
                                text = "You pick the location, so backups and file managers can reach your documents.",
                            )
                        }
                    }

                    FolderPickerGuideCard(isFirstSetup = isFirstSetup)

                    Button(
                        onClick = onChooseFolder,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (isFirstSetup) "Choose folder" else "Select library folder")
                    }

                    if (state.status == LibraryStartupStatus.REPAIR_REQUIRED) {
                        OutlinedButton(
                            onClick = onRetry,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Try automatic repair")
                        }
                    }
                } else {
                    LibraryStartupProgressPanel(
                        status = state.status,
                        displayPath = state.displayPath,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderBenefitRow(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FolderPickerGuideCard(
    isFirstSetup: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.TouchApp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = if (isFirstSetup) "How to pick a folder" else "How to reconnect",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (isFirstSetup) {
                GuideStep(number = 1, text = "Tap Choose folder below to open your phone's file picker.")
                GuideStep(
                    number = 2,
                    text = "Open Documents (recommended) or Downloads, then create or select a folder named Scanly.",
                )
                GuideStep(
                    number = 3,
                    text = "Confirm with Use this folder. Scanly will store all of your scans there.",
                )
                Text(
                    text = "Tip: Avoid cloud-only folders or app-private storage — they may not stay available offline.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                GuideStep(number = 1, text = "Tap Select library folder below.")
                GuideStep(
                    number = 2,
                    text = "Navigate to the same Scanly folder you chose before — usually in Documents or Downloads.",
                )
                GuideStep(number = 3, text = "Confirm with Use this folder to restore access.")
            }
        }
    }
}

@Composable
private fun GuideStep(
    number: Int,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "$number.",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun folderPickerIntro(isFirstSetup: Boolean): String =
    if (isFirstSetup) {
        "Before you start scanning, choose where Scanly should keep your documents."
    } else {
        "Scanly lost access to your library folder. Select the same folder again to continue."
    }
