package `in`.c1ph3rj.scanly.feature.placeholder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `in`.c1ph3rj.scanly.feature.components.ScanlyDetailScaffold
import `in`.c1ph3rj.scanly.navigation.ScanlyDestination

@Composable
fun FeaturePlaceholderRoute(
    destination: ScanlyDestination,
    onNavigateUp: () -> Unit,
) {
    FeaturePlaceholderScreen(
        destination = destination,
        onNavigateUp = onNavigateUp,
    )
}

@Composable
fun FeaturePlaceholderScreen(
    destination: ScanlyDestination,
    onNavigateUp: () -> Unit,
) {
    ScanlyDetailScaffold(
        title = destination.title,
        onNavigateUp = onNavigateUp,
        modifier = Modifier.fillMaxSize(),
        actions = {
            TextButton(onClick = onNavigateUp) {
                Text(text = "Back")
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Text(
                            text = destination.summary,
                            modifier = Modifier.padding(20.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            item {
                Text(
                    text = "Planned for ${destination.sprintLabel}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
