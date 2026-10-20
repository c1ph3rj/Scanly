package `in`.c1ph3rj.scanly.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AssistChip
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ShortText
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.RequestQuote
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.draw.clip
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.core.ui.PreviewDisplaySize
import `in`.c1ph3rj.scanly.core.ui.ScanlyTestTags
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.domain.model.DocumentKind
import `in`.c1ph3rj.scanly.domain.model.DocumentSearchResult
import `in`.c1ph3rj.scanly.domain.model.FolderSearchResult
import `in`.c1ph3rj.scanly.domain.model.PageTextHit
import `in`.c1ph3rj.scanly.domain.model.SearchScope
import `in`.c1ph3rj.scanly.feature.components.DocumentThumbnail
import `in`.c1ph3rj.scanly.feature.components.LibraryFolderArt
import `in`.c1ph3rj.scanly.feature.components.LibraryIconButton
import `in`.c1ph3rj.scanly.feature.components.LibraryListItem
import `in`.c1ph3rj.scanly.feature.components.formatGroupCardMeta
import `in`.c1ph3rj.scanly.feature.components.toRelativeDate
import `in`.c1ph3rj.scanly.feature.text.matchHighlightStyle
import `in`.c1ph3rj.scanly.feature.text.toAnnotatedString

@Composable
fun LibrarySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    inputModifier: Modifier = Modifier,
    onSearch: () -> Unit = {},
    placeholder: String = "Search names and text in pages",
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (isFocused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        modifier = modifier.fillMaxWidth().testTag(ScanlyTestTags.SEARCH_FIELD),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
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
                        text = placeholder,
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
                        .semantics { contentDescription = placeholder },
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
fun LibrarySearchRoute(
    onBack: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenPageText: (documentId: String, pageIndex: Int, query: String) -> Unit,
    viewModel: LibrarySearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    LibrarySearchScreen(
        uiState = uiState,
        query = query,
        onBack = onBack,
        onOpenDocument = onOpenDocument,
        onOpenGroup = onOpenGroup,
        onOpenPageText = onOpenPageText,
        onQueryChange = viewModel::setQuery,
        onClear = viewModel::clearQuery,
        onScopeChange = viewModel::setScope,
        onKindChange = viewModel::setKindFilter,
        onQuerySubmitted = viewModel::rememberCurrentQuery,
        onRemoveRecent = viewModel::removeRecentSearch,
        onClearRecent = viewModel::clearRecentSearches,
    )
}

@Composable
fun LibrarySearchScreen(
    uiState: LibrarySearchUiState,
    onBack: () -> Unit,
    query: String = uiState.query,
    onOpenDocument: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenPageText: (documentId: String, pageIndex: Int, query: String) -> Unit,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onScopeChange: (SearchScope) -> Unit,
    onKindChange: (DocumentKind?) -> Unit,
    onQuerySubmitted: () -> Unit = {},
    onRemoveRecent: (String) -> Unit = {},
    onClearRecent: () -> Unit = {},
) {
    val window = rememberWindowSizeInfo()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // Focus (and raise the keyboard) only on first entry with an empty box. Returning from a
    // result re-enters composition, and re-focusing then would pop the keyboard over the results.
    var initialFocusDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!initialFocusDone && query.isEmpty()) focusRequester.requestFocus()
        initialFocusDone = true
    }
    val results = uiState.results
    val openDocument: (String) -> Unit = { focusManager.clearFocus(); onQuerySubmitted(); onOpenDocument(it) }
    val openPage: (PageTextHit) -> Unit = { hit ->
        focusManager.clearFocus()
        onQuerySubmitted()
        onOpenPageText(hit.documentId, hit.pageIndex, uiState.query)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag(ScanlyTestTags.SEARCH_SCREEN).imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .statusBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = window.horizontalPadding, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LibraryIconButton(onBack, Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    Spacer(Modifier.width(4.dp))
                    LibrarySearchBar(
                        query = query,
                        onQueryChange = onQueryChange,
                        onClear = onClear,
                        modifier = Modifier.weight(1f),
                        inputModifier = Modifier
                            .focusRequester(focusRequester)
                            .testTag(ScanlyTestTags.SEARCH_INPUT),
                        onSearch = { focusManager.clearFocus(); onQuerySubmitted() },
                    )
                }
                SearchFilterRow(
                    scope = uiState.scope,
                    kind = uiState.kindFilter,
                    availableKinds = uiState.availableKinds,
                    onScopeChange = onScopeChange,
                    onKindChange = onKindChange,
                    horizontalPadding = window.horizontalPadding,
                )
                Box(Modifier.fillMaxWidth().height(2.dp)) {
                    if (uiState.isSearchActive && !results.isTextSearchComplete) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                    } else {
                        HorizontalDivider(
                            modifier = Modifier.align(Alignment.BottomCenter),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (window.isTablet) Modifier.widthIn(max = window.contentMaxWidth) else Modifier),
                contentPadding = PaddingValues(
                    start = window.horizontalPadding,
                    end = window.horizontalPadding,
                    top = 16.dp,
                    bottom = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    uiState.isLoading -> Unit
                    !uiState.isSearchActive -> item(key = "landing") {
                        SearchLanding(
                            uiState = uiState,
                            onRecent = { recent -> onQueryChange(recent); focusManager.clearFocus() },
                            onRemoveRecent = onRemoveRecent,
                            onClearRecent = onClearRecent,
                            onBrowseKind = { kind -> focusManager.clearFocus(); onKindChange(kind) },
                        )
                    }
                    results.isEmpty && results.isTextSearchComplete -> item(key = "empty") {
                        NoResultsCard(
                            query = uiState.query,
                            stillReading = uiState.isIndexing && uiState.scope != SearchScope.NAMES,
                            suggestion = uiState.suggestion,
                            onApplySuggestion = onQueryChange,
                        )
                    }
                    else -> {
                        item(key = "summary") {
                            ResultSummary(uiState, Modifier.animateItem())
                        }
                        if (uiState.isIndexing && uiState.scope != SearchScope.NAMES) {
                            item(key = "indexing") {
                                IndexingNotice(uiState.indexProgress?.pendingPages ?: 0, Modifier.animateItem())
                            }
                        }
                        if (results.textMatches.isNotEmpty() && results.nameMatches.isNotEmpty()) {
                            item(key = "text_heading") {
                                ResultSectionTitle("Found in pages", Modifier.animateItem())
                            }
                        }
                        results.textMatches.forEach { result ->
                            // Same key as in the names section, so a document that turns out to
                            // match inside its pages visibly slides up rather than popping in.
                            item(key = "doc_${result.document.id}") {
                                TextResultCard(
                                    result = result,
                                    onOpenDocument = { openDocument(result.document.id) },
                                    onOpenPage = openPage,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                        if (results.nameMatches.isNotEmpty()) {
                            item(key = "name_heading") {
                                ResultSectionTitle(
                                    when {
                                        uiState.isBrowsingKind -> uiState.kindFilter!!.pluralLabel()
                                        results.textMatches.isEmpty() -> "Documents"
                                        else -> "Matching names"
                                    },
                                    Modifier.animateItem().padding(top = if (results.textMatches.isEmpty()) 0.dp else 8.dp),
                                )
                            }
                            itemsIndexed(results.nameMatches, key = { _, result -> "doc_${result.document.id}" }) { index, result ->
                                LibraryListItem(
                                    first = index == 0,
                                    last = index == results.nameMatches.lastIndex,
                                    modifier = Modifier.animateItem(),
                                ) {
                                    NameResultRow(result, onOpen = { openDocument(result.document.id) })
                                }
                            }
                        }
                        if (results.folders.isNotEmpty()) {
                            item(key = "folder_heading") {
                                ResultSectionTitle("Folders", Modifier.animateItem().padding(top = 8.dp))
                            }
                            itemsIndexed(results.folders, key = { _, folder -> "folder_${folder.group.id}" }) { index, folder ->
                                LibraryListItem(
                                    first = index == 0,
                                    last = index == results.folders.lastIndex,
                                    modifier = Modifier.animateItem(),
                                ) {
                                    FolderResultRow(folder, onOpen = { focusManager.clearFocus(); onOpenGroup(folder.group.id) })
                                }
                            }
                        }
                        item(key = "privacy") {
                            PrivacyFootnote(Modifier.animateItem())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchFilterRow(
    scope: SearchScope,
    kind: DocumentKind?,
    availableKinds: List<DocumentKind>,
    onScopeChange: (SearchScope) -> Unit,
    onKindChange: (DocumentKind?) -> Unit,
    horizontalPadding: androidx.compose.ui.unit.Dp,
) {
    val scrollState = rememberScrollState()
    // The type chip is last in the row; bring it into view when a type gets picked.
    LaunchedEffect(kind) {
        if (kind != null) scrollState.animateScrollTo(scrollState.maxValue)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = horizontalPadding, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchScope.entries.forEach { option ->
            val selected = option == scope
            FilterChip(
                selected = selected,
                onClick = { onScopeChange(option) },
                label = { Text(option.label) },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                    selectedLabelColor = MaterialTheme.colorScheme.inverseOnSurface,
                ),
            )
        }
        if (availableKinds.isNotEmpty()) {
            var expanded by remember { mutableStateOf(false) }
            Box {
                FilterChip(
                    selected = kind != null,
                    onClick = { expanded = true },
                    label = { Text(kind?.label ?: "Any type") },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, Modifier.size(18.dp)) },
                    shape = CircleShape,
                    modifier = Modifier.semantics { contentDescription = "Document type: ${kind?.label ?: "Any type"}" },
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    (listOf<DocumentKind?>(null) + availableKinds).forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option?.label ?: "Any type") },
                            leadingIcon = {
                                if (option == kind) Icon(Icons.Filled.Check, contentDescription = null)
                            },
                            onClick = {
                                expanded = false
                                onKindChange(option)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultSummary(uiState: LibrarySearchUiState, modifier: Modifier = Modifier) {
    val results = uiState.results
    val documents = results.documentCount
    val text = when {
        results.totalTextMatches > 0 -> {
            val matches = results.totalTextMatches
            val inDocs = results.textMatches.size
            "$matches ${if (matches == 1) "match" else "matches"} in $inDocs ${if (inDocs == 1) "document" else "documents"}" +
                if (results.nameMatches.isNotEmpty()) " · ${results.nameMatches.size} by name" else ""
        }
        documents > 0 -> "$documents ${if (documents == 1) "document" else "documents"}"
        results.folders.isNotEmpty() -> "${results.folders.size} ${if (results.folders.size == 1) "folder" else "folders"}"
        else -> "Searching text in pages…"
    }
    Text(
        text = text,
        modifier = modifier
            .padding(horizontal = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ResultSectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        modifier = modifier
            .padding(horizontal = 4.dp)
            .semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TextResultCard(
    result: DocumentSearchResult,
    onOpenDocument: () -> Unit,
    onOpenPage: (PageTextHit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val highlight = matchHighlightStyle()
    var expanded by rememberSaveable(result.document.id) { mutableStateOf(false) }
    val visibleHits = if (expanded) result.pageHits else result.pageHits.take(COLLAPSED_PAGE_HITS)
    val hiddenPages = result.pageHits.size - COLLAPSED_PAGE_HITS

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Column(Modifier.animateContentSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClickLabel = "Open document", onClick = onOpenDocument)
                    .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ResultThumbnail(result, Modifier.width(52.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = result.title.toAnnotatedString(highlight),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = resultMeta(result),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Column(Modifier.padding(vertical = 6.dp)) {
                visibleHits.forEach { hit ->
                    SnippetRow(hit, onClick = { onOpenPage(hit) })
                }
            }
            if (hiddenPages > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                ) {
                    Text(
                        if (expanded) {
                            "Show fewer pages"
                        } else {
                            "Show matches on $hiddenPages more ${if (hiddenPages == 1) "page" else "pages"}"
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SnippetRow(hit: PageTextHit, onClick: () -> Unit) {
    val highlight = matchHighlightStyle()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Show page text", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Page ${hit.pageIndex + 1}",
            modifier = Modifier.width(52.dp).padding(top = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = hit.snippet.toAnnotatedString(highlight),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NameResultRow(result: DocumentSearchResult, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ResultThumbnail(result, Modifier.width(44.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = result.title.toAnnotatedString(matchHighlightStyle()),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = resultMeta(result),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResultThumbnail(result: DocumentSearchResult, modifier: Modifier) {
    DocumentThumbnail(
        thumbnailPath = result.document.coverThumbnailPath,
        title = result.document.title,
        contentRevision = result.document.updatedAtMillis,
        displaySize = PreviewDisplaySize.COMPACT,
        modifier = modifier,
        minHeight = 0.dp,
    )
}

@Composable
private fun FolderResultRow(
    folder: FolderSearchResult,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LibraryFolderArt(folder.group, Modifier.size(width = 48.dp, height = 48.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = folder.title.toAnnotatedString(matchHighlightStyle()),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Folder · " + formatGroupCardMeta(folder.group.documentCount, folder.group.totalPageCount),
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
private fun IndexingNotice(pendingPages: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(
                text = "Still reading text in $pendingPages ${if (pendingPages == 1) "page" else "pages"}. " +
                    "New matches will appear here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/**
 * What the search screen shows before anything is typed: recent searches, one-tap
 * browsing by document type, and short tips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchLanding(
    uiState: LibrarySearchUiState,
    onRecent: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecent: () -> Unit,
    onBrowseKind: (DocumentKind) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (uiState.recentSearches.isNotEmpty()) {
            LandingSection(
                title = "Recent",
                action = { TextButton(onClick = onClearRecent) { Text("Clear") } },
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    uiState.recentSearches.forEach { recent ->
                        RecentSearchPill(
                            text = recent,
                            onSearch = { onRecent(recent) },
                            onRemove = { onRemoveRecent(recent) },
                        )
                    }
                }
            }
        }

        if (uiState.kindCounts.isNotEmpty()) {
            LandingSection(title = "Browse by type") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    uiState.kindCounts.entries.sortedBy { it.key.ordinal }.forEach { (kind, count) ->
                        AssistChip(
                            onClick = { onBrowseKind(kind) },
                            label = { Text("${kind.pluralLabel()} · $count") },
                            leadingIcon = {
                                Icon(kind.icon(), contentDescription = null, Modifier.size(18.dp))
                            },
                            shape = CircleShape,
                        )
                    }
                }
            }
        }

        if (uiState.hasLibrary) {
            LandingSection(title = "Search tips") {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        SearchTip(Icons.Outlined.ShortText, "Type part of a word", "“lea” finds lease and leasing")
                        SearchTip(Icons.Outlined.FormatQuote, "Use quotes for a phrase", "“security deposit”, in that order")
                        SearchTip(Icons.Outlined.Spellcheck, "Small typos are fine", "You’ll get the closest word from your scans")
                    }
                }
            }
        }

        PrivacyFootnote()
    }
}

@Composable
private fun LandingSection(
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f).padding(start = 4.dp).semantics { heading() },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            action?.invoke()
        }
        content()
    }
}

@Composable
private fun SearchTip(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, example: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(example, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun DocumentKind.pluralLabel(): String = "${label}s"

private fun DocumentKind.icon(): androidx.compose.ui.graphics.vector.ImageVector = when (this) {
    DocumentKind.INVOICE -> Icons.Outlined.RequestQuote
    DocumentKind.RECEIPT -> Icons.Outlined.ReceiptLong
    DocumentKind.CONTRACT -> Icons.Outlined.Gavel
    DocumentKind.STATEMENT -> Icons.Outlined.AccountBalance
    DocumentKind.LETTER -> Icons.Outlined.MailOutline
    DocumentKind.CERTIFICATE -> Icons.Outlined.WorkspacePremium
    DocumentKind.ID_DOCUMENT -> Icons.Outlined.Badge
    DocumentKind.FORM -> Icons.Outlined.Assignment
    DocumentKind.RESUME -> Icons.Outlined.Person
}

@Composable
fun NoResultsCard(
    query: String,
    stillReading: Boolean = false,
    suggestion: String? = null,
    onApplySuggestion: (String) -> Unit = {},
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (`in`.c1ph3rj.scanly.feature.pip.LocalPipPreferences.current.enabled) {
                `in`.c1ph3rj.scanly.feature.pip.PipSays(
                    line = if (stillReading) {
                        `in`.c1ph3rj.scanly.feature.pip.PipLines.SEARCH_STILL_READING
                    } else {
                        `in`.c1ph3rj.scanly.feature.pip.PipLines.searchNoResults(query)
                    },
                    mood = if (stillReading) {
                        `in`.c1ph3rj.scanly.feature.pip.PipMood.TALK
                    } else {
                        `in`.c1ph3rj.scanly.feature.pip.PipMood.DIZZY
                    },
                    size = 80.dp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "No results found",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Nothing matches “${query.trim()}”. Try fewer words or check the spelling.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (suggestion != null) {
                FilledTonalButton(
                    onClick = { onApplySuggestion(suggestion) },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text("Search for “$suggestion” instead")
                }
            }
            if (stillReading) {
                Text(
                    text = "Some pages are still being read, so matches may appear shortly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PrivacyFootnote(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Lock,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Text is read on this device. Your pages never leave it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun resultMeta(result: DocumentSearchResult): String = listOfNotNull(
    result.kind?.label,
    result.folderTitle,
    when {
        result.textMatchCount > 0 -> "${result.textMatchCount} ${if (result.textMatchCount == 1) "match" else "matches"}"
        else -> "${result.document.pageCount} ${if (result.document.pageCount == 1) "page" else "pages"}"
    },
    result.document.updatedAtMillis.toRelativeDate().takeIf { result.textMatchCount == 0 },
).joinToString(" · ")

private const val COLLAPSED_PAGE_HITS = 3

/**
 * A recent search: tapping the text searches again, the × removes it. Two separate touch targets,
 * each with its ripple clipped to its own shape, inside one 44dp outlined pill.
 */
@Composable
private fun RecentSearchPill(
    text: String,
    onSearch: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp))
                    .clickable(role = Role.Button, onClickLabel = "Search again", onClick = onSearch)
                    .padding(start = 14.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Outlined.History,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 200.dp),
                )
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Remove $text from recent searches",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
