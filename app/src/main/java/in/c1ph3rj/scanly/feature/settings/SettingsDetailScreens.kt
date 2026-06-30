package `in`.c1ph3rj.scanly.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.domain.model.LicenseInfo
import `in`.c1ph3rj.scanly.domain.model.SettingsFaq
import `in`.c1ph3rj.scanly.feature.components.ScanlyDetailScaffold

private val DetailRowPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)

@Composable
fun SettingsFaqRoute(
    onNavigateUp: () -> Unit,
    parentEntry: NavBackStackEntry,
    viewModel: SettingsViewModel = hiltViewModel(parentEntry),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsFaqScreen(
        faqs = uiState.content?.faqs.orEmpty(),
        isLoading = uiState.isLoading && uiState.content == null,
        onNavigateUp = onNavigateUp,
    )
}

@Composable
fun SettingsFaqScreen(
    faqs: List<SettingsFaq>,
    isLoading: Boolean,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val expandedFaqIds = remember { mutableStateListOf<String>() }
    val windowSizeInfo = rememberWindowSizeInfo()

    ScanlyDetailScaffold(
        title = "Help & FAQ",
        onNavigateUp = onNavigateUp,
        modifier = modifier,
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = windowSizeInfo.horizontalPadding,
                    end = windowSizeInfo.horizontalPadding,
                    top = 8.dp,
                    bottom = 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "faq_group") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            faqs.forEachIndexed { index, faq ->
                                val expanded = faq.id in expandedFaqIds
                                ExpandableDetailRow(
                                    title = faq.question,
                                    body = faq.answer,
                                    expanded = expanded,
                                    onToggle = {
                                        if (expanded) {
                                            expandedFaqIds.remove(faq.id)
                                        } else {
                                            expandedFaqIds.add(faq.id)
                                        }
                                    },
                                )
                                if (index < faqs.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsLicensesRoute(
    onNavigateUp: () -> Unit,
    parentEntry: NavBackStackEntry,
    viewModel: SettingsViewModel = hiltViewModel(parentEntry),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    SettingsLicensesScreen(
        licenses = uiState.content?.licenses.orEmpty(),
        isLoading = uiState.isLoading && uiState.content == null,
        onNavigateUp = onNavigateUp,
        onOpenWebsite = uriHandler::openUri,
    )
}

@Composable
fun SettingsLicensesScreen(
    licenses: List<LicenseInfo>,
    isLoading: Boolean,
    onNavigateUp: () -> Unit,
    onOpenWebsite: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val expandedLicenseIds = remember { mutableStateListOf<String>() }
    val windowSizeInfo = rememberWindowSizeInfo()

    ScanlyDetailScaffold(
        title = "Open source",
        onNavigateUp = onNavigateUp,
        modifier = modifier,
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = windowSizeInfo.horizontalPadding,
                    end = windowSizeInfo.horizontalPadding,
                    top = 8.dp,
                    bottom = 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "licenses_group") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            licenses.forEachIndexed { index, license ->
                                val expanded = license.id in expandedLicenseIds
                                LicenseDetailRow(
                                    licenseInfo = license,
                                    expanded = expanded,
                                    onToggle = {
                                        if (expanded) {
                                            expandedLicenseIds.remove(license.id)
                                        } else {
                                            expandedLicenseIds.add(license.id)
                                        }
                                    },
                                    onOpenWebsite = onOpenWebsite,
                                )
                                if (index < licenses.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandableDetailRow(
    title: String,
    body: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(DetailRowPadding),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) {
                    Icons.Filled.KeyboardArrowUp
                } else {
                    Icons.Filled.KeyboardArrowDown
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LicenseDetailRow(
    licenseInfo: LicenseInfo,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenWebsite: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(DetailRowPadding),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = licenseInfo.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = licenseInfo.license,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Icon(
                imageVector = if (expanded) {
                    Icons.Filled.KeyboardArrowUp
                } else {
                    Icons.Filled.KeyboardArrowDown
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = licenseInfo.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                licenseInfo.websiteUrl?.let { website ->
                    Text(
                        text = website,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onOpenWebsite(website) },
                    )
                }
            }
        }
    }
}
