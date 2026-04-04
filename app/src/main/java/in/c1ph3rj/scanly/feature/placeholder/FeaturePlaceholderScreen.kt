package `in`.c1ph3rj.scanly.feature.placeholder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
@OptIn(ExperimentalMaterial3Api::class)
fun FeaturePlaceholderScreen(
    destination: ScanlyDestination,
    onNavigateUp: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = destination.title) },
                navigationIcon = {
                    TextButton(onClick = onNavigateUp) {
                        Text(text = "Back")
                    }
                },
            )
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
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = destination.summary,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            text = "This route is intentionally in place now so the app shell stays stable while ${destination.sprintLabel} implementation lands.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "What this placeholder protects",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "- Navigation routes stay fixed while feature code grows.\n- Each feature can get its own ViewModel and state contract later.\n- We avoid rebuilding the app shell every sprint.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
