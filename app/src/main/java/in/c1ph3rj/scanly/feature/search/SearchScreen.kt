package `in`.c1ph3rj.scanly.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import `in`.c1ph3rj.scanly.core.ui.ChromeIconButton
import `in`.c1ph3rj.scanly.core.ui.MetricChip
import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.feature.home.DocumentThumbnail

object SearchDestination {
    const val route = "search"
}

@Composable
fun SearchRoute(
    onNavigateUp: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onOpenGroups: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    SearchScreen(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onOpenDocument = onOpenDocument,
        onOpenGroups = onOpenGroups,
    )
}

@Composable
fun SearchScreen(
    uiState: SearchUiState,
    onNavigateUp: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onOpenGroups: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(SearchResultFilter.Default) }
    val normalizedQuery = query.trim()
    val matchingGroups = uiState.groups.filter { group ->
        normalizedQuery.isNotBlank() && group.name.contains(normalizedQuery, ignoreCase = true)
    }
    val matchingDocuments = uiState.documents.filter { document ->
        normalizedQuery.isNotBlank() &&
            (
                document.title.contains(normalizedQuery, ignoreCase = true) ||
                    document.groupName?.contains(normalizedQuery, ignoreCase = true) == true
                )
    }
    val showGroups = selectedFilter != SearchResultFilter.Documents
    val showDocuments = selectedFilter != SearchResultFilter.Groups
    val hasVisibleResults =
        (showGroups && matchingGroups.isNotEmpty()) ||
            (showDocuments && matchingDocuments.isNotEmpty())

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SearchHeader(onNavigateUp = onNavigateUp)
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                        )
                    },
                    placeholder = { Text(text = "Search documents or groups") },
                    shape = MaterialTheme.shapes.large,
                )
            }
            item {
                SearchFilterChips(
                    selectedFilter = selectedFilter,
                    onFilterSelected = { selectedFilter = it },
                )
            }

            if (normalizedQuery.isBlank()) {
                item { SearchHintCard() }
            } else {
                if (showGroups) {
                    item {
                        ResultSectionHeader(
                            title = "Groups",
                            count = matchingGroups.size,
                        )
                    }
                    items(
                        items = matchingGroups,
                        key = { group -> group.id },
                    ) { group ->
                        GroupResultRow(
                            group = group,
                            onClick = onOpenGroups,
                        )
                    }
                }

                if (showDocuments) {
                    item {
                        ResultSectionHeader(
                            title = "Documents",
                            count = matchingDocuments.size,
                        )
                    }
                    items(
                        items = matchingDocuments,
                        key = { document -> document.id },
                    ) { document ->
                        DocumentResultRow(
                            document = document,
                            onClick = { onOpenDocument(document.id) },
                        )
                    }
                }

                if (!hasVisibleResults) {
                    item {
                        EmptySearchResult(
                            query = normalizedQuery,
                            filter = selectedFilter,
                        )
                    }
                }
            }
        }
    }
}

private enum class SearchResultFilter(
    val label: String,
) {
    Default("Default"),
    Groups("Groups"),
    Documents("Documents"),
}

@Composable
private fun SearchHeader(
    onNavigateUp: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChromeIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            onClick = onNavigateUp,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Search",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Find documents and groups",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResultSectionHeader(
    title: String,
    count: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchFilterChips(
    selectedFilter: SearchResultFilter,
    onFilterSelected: (SearchResultFilter) -> Unit,
) {
    val filters = remember { SearchResultFilter.entries.toList() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        filters.forEach { filter ->
            val selected = filter == selectedFilter
            Surface(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clickable { onFilterSelected(filter) },
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
                contentColor = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                shape = MaterialTheme.shapes.large,
            ) {
                Text(
                    text = filter.label,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun GroupResultRow(
    group: DocumentGroup,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.large,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${group.documentCount} documents · ${group.pageCount} pages",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DocumentResultRow(
    document: ScanDocument,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DocumentThumbnail(
                thumbnailPath = document.coverThumbnailPath,
                title = document.title,
                modifier = Modifier.size(width = 72.dp, height = 72.dp),
                minHeight = 72.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = document.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricChip(
                        label = document.groupName ?: "Ungrouped",
                        icon = Icons.Filled.Folder,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    MetricChip(
                        label = document.pageCount.toString(),
                        icon = Icons.Filled.Description,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchHintCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            text = "Type a document title or group name.",
            modifier = Modifier.padding(18.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptySearchResult(
    query: String,
    filter: SearchResultFilter,
) {
    val resultType = when (filter) {
        SearchResultFilter.Default -> "results"
        SearchResultFilter.Groups -> "groups"
        SearchResultFilter.Documents -> "documents"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            text = "No $resultType for \"$query\"",
            modifier = Modifier.padding(18.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
