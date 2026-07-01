package `in`.c1ph3rj.scanly.feature.startup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import `in`.c1ph3rj.scanly.domain.model.LibraryAccessState
import `in`.c1ph3rj.scanly.domain.model.LibraryStartupStatus

@Composable
fun LibraryStartupScreen(
    state: LibraryAccessState,
    onChooseFolder: () -> Unit,
    onRetry: () -> Unit,
) {
    val needsFolder = state.status == LibraryStartupStatus.STORAGE_SETUP_REQUIRED ||
        state.status == LibraryStartupStatus.RECONNECT_REQUIRED ||
        state.status == LibraryStartupStatus.REPAIR_REQUIRED
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (needsFolder) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = if (state.status == LibraryStartupStatus.STORAGE_SETUP_REQUIRED) {
                            "Choose a Scanly library folder"
                        } else {
                            "Reconnect your Scanly library"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = state.message ?: "Choose or create a dedicated Scanly folder in local shared storage. Your documents will remain there after uninstall.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(22.dp))
                    Button(onClick = onChooseFolder, modifier = Modifier.fillMaxWidth()) {
                        Text("Select folder")
                    }
                    if (state.status == LibraryStartupStatus.REPAIR_REQUIRED) {
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Retry recovery") }
                    }
                } else {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = when (state.status) {
                            LibraryStartupStatus.RECOVERING_OPERATIONS -> "Recovering interrupted changes…"
                            LibraryStartupStatus.REBUILDING_DATABASE -> "Rebuilding your library index…"
                            LibraryStartupStatus.APPLYING_DELTA -> "Loading library changes…"
                            LibraryStartupStatus.UNSUPPORTED_LIBRARY_VERSION -> "A newer Scanly version is required."
                            else -> "Loading your Scanly library…"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

