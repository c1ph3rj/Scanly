package `in`.c1ph3rj.scanly.feature.library

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.core.ui.ScanlyTestTags
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.feature.components.DocumentCard
import `in`.c1ph3rj.scanly.feature.components.FullScreenLoader
import `in`.c1ph3rj.scanly.feature.components.LibraryCardStyle
import `in`.c1ph3rj.scanly.feature.components.LibraryFolderArt
import `in`.c1ph3rj.scanly.feature.components.LibraryIconButton
import `in`.c1ph3rj.scanly.feature.components.LibraryListItem
import `in`.c1ph3rj.scanly.feature.components.LibrarySectionTitle
import `in`.c1ph3rj.scanly.feature.components.formatGroupCardMeta

@Composable
fun LibrarySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    inputModifier: Modifier = Modifier,
    onSearch: () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val searchPlaceholder = "Search documents and folders…"
    val borderColor = if (isFocused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        modifier = modifier.fillMaxWidth().testTag(ScanlyTestTags.SEARCH_FIELD),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(start = 18.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = searchPlaceholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = inputModifier
                        .fillMaxWidth()
                        .semantics { contentDescription = searchPlaceholder },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    singleLine = true,
                    interactionSource = interactionSource,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                )
            }
            if (query.isNotEmpty()) {
                LibraryIconButton(onClear, Icons.Filled.Clear, "Clear search")
            }
        }
    }
}

@Composable
fun NoResultsCard(
    query: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "No results found",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Nothing matches \"${query.trim()}\". Try a different name.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FolderResultRow(
    group: DocumentGroup,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LibraryFolderArt(group, Modifier.size(width = 48.dp, height = 48.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = group.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Folder · " + formatGroupCardMeta(group.documentCount, group.totalPageCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun LibrarySearchRoute(
    onBack: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LibrarySearchScreen(uiState, onBack, onOpenDocument, onOpenGroup,
        viewModel::setSearchQuery, viewModel::clearSearch)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySearchScreen(
    uiState: LibraryUiState,
    onBack: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    val window = rememberWindowSizeInfo()
    val searchState = remember(uiState) { uiState.copy(selectedTab = LibraryTab.All, selectedGroupId = null) }
    val groups = remember(searchState) { searchState.visibleGroups }
    val documents = remember(searchState) { searchState.visibleDocuments }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // Focus once on entry; returning from a result keeps the query and scroll position.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag(ScanlyTestTags.SEARCH_SCREEN).imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Search library", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    LibraryIconButton(onBack, Icons.AutoMirrored.Filled.ArrowBack, "Back")
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = window.horizontalPadding)) {
            LibrarySearchBar(
                query = uiState.searchQuery,
                onQueryChange = onQueryChange,
                onClear = onClear,
                inputModifier = Modifier.focusRequester(focusRequester).testTag(ScanlyTestTags.SEARCH_INPUT),
                onSearch = { focusManager.clearFocus() },
            )
            Spacer(Modifier.height(20.dp))
            if (searchState.isLoading) {
                FullScreenLoader(Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    when {
                        !searchState.isSearchActive -> item {
                            Text("Find a document or folder", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            Text("Search by name across your entire library, including documents inside folders.",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        groups.isEmpty() && documents.isEmpty() -> item { NoResultsCard(searchState.searchQuery) }
                        else -> {
                            item {
                                val count = groups.size + documents.size
                                Text("$count ${if (count == 1) "result" else "results"}",
                                    modifier = Modifier.padding(bottom = 12.dp),
                                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (groups.isNotEmpty()) {
                                item(key = "folders_heading") {
                                    LibrarySectionTitle("Folders", groups.size, Modifier.padding(top = 4.dp, bottom = 10.dp))
                                }
                                itemsIndexed(groups, key = { _, group -> "group_${group.id}" }) { index, group ->
                                    LibraryListItem(first = index == 0, last = index == groups.lastIndex) {
                                        FolderResultRow(group, onOpen = { focusManager.clearFocus(); onOpenGroup(group.id) })
                                    }
                                }
                            }
                            if (documents.isNotEmpty()) {
                                item(key = "documents_heading") {
                                    LibrarySectionTitle(
                                        "Documents",
                                        documents.size,
                                        Modifier.padding(top = if (groups.isNotEmpty()) 20.dp else 4.dp, bottom = 10.dp),
                                    )
                                }
                                itemsIndexed(documents, key = { _, document -> "document_${document.id}" }) { index, document ->
                                    LibraryListItem(first = index == 0, last = index == documents.lastIndex) {
                                        DocumentCard(document, onOpen = { focusManager.clearFocus(); onOpenDocument(document.id) }, style = LibraryCardStyle.PreviewList)
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
