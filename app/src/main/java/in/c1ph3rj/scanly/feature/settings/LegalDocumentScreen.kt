package `in`.c1ph3rj.scanly.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import `in`.c1ph3rj.scanly.feature.update.ReleaseMarkdown

enum class LegalDocumentType(
    val assetPath: String,
    val title: String,
) {
    Privacy(
        assetPath = "settings/privacy_policy.md",
        title = "Privacy Policy",
    ),
    Terms(
        assetPath = "settings/terms_of_service.md",
        title = "Terms & Conditions",
    ),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalDocumentRoute(
    documentType: LegalDocumentType,
    onNavigateUp: () -> Unit,
) {
    val context = LocalContext.current
    val bodyMarkdown = remember(documentType) {
        runCatching {
            context.assets.open(documentType.assetPath).bufferedReader().use { it.readText() }
        }.getOrElse {
            "This document could not be loaded."
        }
    }

    LegalDocumentScreen(
        title = documentType.title,
        bodyMarkdown = bodyMarkdown,
        onNavigateUp = onNavigateUp,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalDocumentScreen(
    title: String,
    bodyMarkdown: String,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(text = title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        ) {
            item(key = "legal-body") {
                ReleaseMarkdown(bodyMarkdown = bodyMarkdown)
            }
        }
    }
}
