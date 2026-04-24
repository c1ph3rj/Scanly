package `in`.c1ph3rj.scanly.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.c1ph3rj.scanly.core.ui.ChromeIconButton

object PrivacyPolicyDestination {
    const val route = "settings/privacy-policy"
}

@Composable
fun PrivacyPolicyRoute(
    onNavigateUp: () -> Unit,
) {
    PrivacyPolicyScreen(onNavigateUp = onNavigateUp)
}

@Composable
private fun PrivacyPolicyScreen(
    onNavigateUp: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ChromeIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        onClick = onNavigateUp,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Privacy policy",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Your data stays on your device by default",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            items(privacyPolicySections) { section ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = section.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private data class PrivacyPolicySection(
    val title: String,
    val body: String,
)

private val privacyPolicySections = listOf(
    PrivacyPolicySection(
        title = "What we collect",
        body = "Scanly does not require account sign-in and does not collect personal profile data. Documents, groups, and pages you create are stored locally on your device.",
    ),
    PrivacyPolicySection(
        title = "Camera and photos",
        body = "Camera access is used only when you start a scan session. Photo picker access is used only when you choose to import images. Media is processed to build your local documents.",
    ),
    PrivacyPolicySection(
        title = "Data sharing",
        body = "Scanly does not sell or share your document data with third parties. If this changes in future updates, this policy will be updated in-app.",
    ),
    PrivacyPolicySection(
        title = "Your control",
        body = "You can rename, organize, and delete your documents at any time. Uninstalling the app removes app-local data unless backed up by your device settings.",
    ),
    PrivacyPolicySection(
        title = "Contact",
        body = "For privacy questions, use the project links in Settings > About.",
    ),
)

