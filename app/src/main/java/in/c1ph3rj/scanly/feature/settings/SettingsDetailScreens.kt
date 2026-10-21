package `in`.c1ph3rj.scanly.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }

    val categories = remember(faqs) { faqs.map { it.category }.distinct() }
    val activeCategory = selectedCategory?.takeIf { it in categories }
    val queryWords = remember(query) {
        query.split(WhitespaceRegex).filter { it.isNotBlank() }
    }
    val isSearching = queryWords.isNotEmpty()
    val sections = remember(faqs, activeCategory, queryWords) {
        faqs
            .filter { faq -> activeCategory == null || faq.category == activeCategory }
            .filter { faq -> faq.matchesAllWords(queryWords) }
            .groupBy { it.category }
    }

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
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                FaqSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    modifier = Modifier.padding(
                        start = windowSizeInfo.horizontalPadding,
                        end = windowSizeInfo.horizontalPadding,
                        top = 8.dp,
                    ),
                )
                FaqCategoryChips(
                    categories = categories,
                    selectedCategory = activeCategory,
                    onSelectCategory = { selectedCategory = it },
                    horizontalPadding = windowSizeInfo.horizontalPadding,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(
                        start = windowSizeInfo.horizontalPadding,
                        end = windowSizeInfo.horizontalPadding,
                        bottom = 28.dp,
                    ),
                ) {
                    if (sections.isEmpty()) {
                        item(key = "faq_empty") {
                            FaqEmptyState(query = query.trim())
                        }
                    }
                    for ((category, items) in sections) {
                        item(key = "faq_header_$category") {
                            Text(
                                text = category,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                            )
                        }
                        itemsIndexed(items = items, key = { _, faq -> faq.id }) { index, faq ->
                            val isLast = index == items.lastIndex
                            val expanded = isSearching || faq.id in expandedFaqIds
                            val toggle: () -> Unit = {
                                if (faq.id in expandedFaqIds) {
                                    expandedFaqIds.remove(faq.id)
                                } else {
                                    expandedFaqIds.add(faq.id)
                                }
                            }
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = faqItemShape(isFirst = index == 0, isLast = isLast),
                            ) {
                                Column {
                                    ExpandableDetailRow(
                                        title = faq.question,
                                        body = faq.answer,
                                        expanded = expanded,
                                        onToggle = if (isSearching) null else toggle,
                                    )
                                    if (!isLast) {
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
}

private val WhitespaceRegex = Regex("\\s+")

private fun SettingsFaq.matchesAllWords(words: List<String>): Boolean =
    words.all { word ->
        question.contains(word, ignoreCase = true) || answer.contains(word, ignoreCase = true)
    }

@Composable
private fun FaqSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        placeholder = { Text("Search help") },
        leadingIcon = {
            Icon(imageVector = Icons.Filled.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Clear search")
                }
            }
        },
    )
}

@Composable
private fun FaqCategoryChips(
    categories: List<String>,
    selectedCategory: String?,
    onSelectCategory: (String?) -> Unit,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selectedCategory == null,
            onClick = { onSelectCategory(null) },
            label = { Text("All") },
        )
        categories.forEach { category ->
            FilterChip(
                selected = selectedCategory == category,
                onClick = { onSelectCategory(category) },
                label = { Text(category) },
            )
        }
    }
}

@Composable
private fun FaqEmptyState(
    query: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 48.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (`in`.c1ph3rj.scanly.feature.pip.LocalPipPreferences.current.enabled) {
            `in`.c1ph3rj.scanly.feature.pip.PipSays(
                line = `in`.c1ph3rj.scanly.feature.pip.PipLines.FAQ_EMPTY,
                mood = `in`.c1ph3rj.scanly.feature.pip.PipMood.DIZZY,
                size = 64.dp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Text(
            text = if (query.isEmpty()) {
                "No answers in this category."
            } else {
                "No answers for “$query”."
            },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Try a different word, or browse a category.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun faqItemShape(isFirst: Boolean, isLast: Boolean): Shape {
    val corner = MaterialTheme.shapes.extraLarge
    return when {
        isFirst && isLast -> corner
        isFirst -> corner.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp))
        isLast -> corner.copy(topStart = CornerSize(0.dp), topEnd = CornerSize(0.dp))
        else -> RectangleShape
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
        onOpenWebsite = { uriHandler.openUriSafely(it) },
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
    onToggle: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier)
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
            if (onToggle != null) {
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
