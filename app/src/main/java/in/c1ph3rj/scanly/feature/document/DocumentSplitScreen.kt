package `in`.c1ph3rj.scanly.feature.document

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.core.ui.PreviewDisplaySize
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import `in`.c1ph3rj.scanly.domain.model.previewImagePath
import `in`.c1ph3rj.scanly.feature.components.CachedThumbnail
import `in`.c1ph3rj.scanly.feature.tools.ToolBottomBar
import `in`.c1ph3rj.scanly.feature.tools.ToolCard
import `in`.c1ph3rj.scanly.feature.tools.ToolColumnMaxWidth
import `in`.c1ph3rj.scanly.feature.tools.ToolLargeTitle
import `in`.c1ph3rj.scanly.feature.tools.ToolPrimaryButton
import `in`.c1ph3rj.scanly.feature.tools.ToolSectionLabel
import `in`.c1ph3rj.scanly.feature.tools.ToolTextAction
import `in`.c1ph3rj.scanly.feature.tools.ToolTokens
import `in`.c1ph3rj.scanly.feature.tools.ToolTopBar
import `in`.c1ph3rj.scanly.feature.tools.isToolDarkTheme
import `in`.c1ph3rj.scanly.feature.tools.pdf.formatPdfPageCount
import kotlinx.coroutines.flow.collectLatest

/** Splits a library document into new documents at the cuts the user places between pages. */
object DocumentSplitDestination {
    const val documentIdArgument = "documentId"
    const val routePattern = "document/{$documentIdArgument}/split"

    fun route(documentId: String): String = "document/$documentId/split"
}

@Composable
fun DocumentSplitRoute(
    onNavigateUp: () -> Unit,
    onDone: (groupId: String?, originalKept: Boolean) -> Unit,
    viewModel: DocumentSplitViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is DocumentSplitEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                is DocumentSplitEvent.Done -> onDone(event.groupId, event.originalKept)
            }
        }
    }
    // The original left the library some other way (deleted elsewhere): nothing to split.
    LaunchedEffect(uiState.isLoading, uiState.document, uiState.isSplitting) {
        if (!uiState.isLoading && uiState.document == null && !uiState.isSplitting) onNavigateUp()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.widthIn(max = ToolColumnMaxWidth).fillMaxSize()) {
            ToolTopBar(onBack = onNavigateUp)
            if (uiState.isLoading) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
                }
            } else {
                SplitBody(
                    uiState = uiState,
                    onToggleCut = viewModel::toggleCut,
                    onEveryPage = viewModel::cutEveryPage,
                    onClear = viewModel::clearCuts,
                    onKeepOriginalChange = viewModel::setKeepOriginal,
                    modifier = Modifier.weight(1f),
                )
            }
            val parts = uiState.parts
            ToolBottomBar {
                ToolPrimaryButton(
                    text = if (parts.size >= 2) {
                        pluralStringResource(R.plurals.document_split_create, parts.size, parts.size)
                    } else {
                        stringResource(R.string.document_split_add_cut)
                    },
                    onClick = viewModel::split,
                    enabled = uiState.canSplit,
                    loading = uiState.isSplitting,
                    icon = Icons.AutoMirrored.Outlined.CallSplit,
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SplitBody(
    uiState: DocumentSplitUiState,
    onToggleCut: (Int) -> Unit,
    onEveryPage: () -> Unit,
    onClear: () -> Unit,
    onKeepOriginalChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pages = uiState.pages
    val parts = uiState.parts
    // Which part each page lands in, for its colour.
    val partOfPage = IntArray(pages.size).also { owners ->
        parts.forEachIndexed { partIndex, part -> part.forEach { owners[it] = partIndex } }
    }
    val cutCount = (parts.size - 1).coerceAtLeast(0)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = ToolTokens.ScreenPadding, end = ToolTokens.ScreenPadding, top = 2.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ToolLargeTitle(
            title = stringResource(R.string.document_split_title),
            subtitle = listOfNotNull(uiState.document?.title, formatPdfPageCount(pages.size)).joinToString(" · "),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ToolSectionLabel(
                    text = stringResource(
                        R.string.document_split_pages_cuts,
                        formatPdfPageCount(pages.size),
                        pluralStringResource(R.plurals.document_split_cut_count, cutCount, cutCount),
                    ),
                    modifier = Modifier.weight(1f),
                )
                ToolTextAction(label = stringResource(R.string.document_split_every_page), onClick = onEveryPage)
                ToolTextAction(label = stringResource(R.string.document_split_clear), onClick = onClear)
            }
            Text(
                text = stringResource(R.string.document_split_hint),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            pages.forEachIndexed { position, page ->
                Row(verticalAlignment = Alignment.Top) {
                    PageTile(page = page, number = position + 1, color = partColor(partOfPage.getOrElse(position) { 0 }))
                    if (position < pages.lastIndex) {
                        Seam(
                            cut = position in uiState.cuts,
                            pageNumber = position + 1,
                            onToggle = { onToggleCut(position) },
                        )
                    }
                }
            }
        }
        if (parts.size >= 2) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolSectionLabel(
                    text = stringResource(
                        R.string.pdf_split_you_get,
                        pluralStringResource(R.plurals.common_documents, parts.size, parts.size),
                    ),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    parts.forEachIndexed { index, part -> PartChip(index = index, part = part) }
                }
            }
        }
        ToolCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.document_split_destination_title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            text = if (uiState.document?.groupId != null) {
                                stringResource(R.string.document_split_destination_folder)
                            } else {
                                stringResource(R.string.document_split_destination_library)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 52.dp))
                Row(
                    modifier = Modifier
                        .toggleable(value = uiState.keepOriginal, role = Role.Switch, onValueChange = onKeepOriginalChange)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(Icons.Outlined.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.document_split_keep_original), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            stringResource(R.string.document_split_keep_original_caption),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = uiState.keepOriginal, onCheckedChange = null)
                }
            }
        }
        Text(
            text = stringResource(R.string.document_split_note),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PageTile(page: ScanPage, number: Int, color: Color) {
    val shape = RoundedCornerShape(4.dp)
    Column(
        modifier = Modifier.width(PageWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CachedThumbnail(
            thumbnailPath = page.previewImagePath(PreviewDisplaySize.COMPACT),
            title = stringResource(R.string.trash_use_cases_page, number),
            displaySize = PreviewDisplaySize.COMPACT,
            contentRevision = page.updatedAtMillis,
            modifier = Modifier.width(PageWidth).height(PageHeight),
            shape = shape,
            contentScale = ContentScale.Fit,
            placeholderIcon = null,
            containerColor = Color.White,
        )
        Box(Modifier.width(PageWidth).height(4.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The gap after a page: a dashed line with a small scissors badge; a cut turns it solid. */
@Composable
private fun Seam(cut: Boolean, pageNumber: Int, onToggle: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.outline
    val description = if (cut) {
        stringResource(R.string.document_split_remove_cut, pageNumber)
    } else {
        stringResource(R.string.document_split_cut_after, pageNumber)
    }
    Box(
        modifier = Modifier
            .width(SeamWidth)
            .height(PageHeight)
            .toggleable(value = cut, role = Role.Switch, onValueChange = { onToggle() })
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
            val x = size.width / 2f
            drawLine(
                color = if (cut) accent else idle.copy(alpha = 0.6f),
                start = Offset(x, 4f),
                end = Offset(x, size.height - 4f),
                strokeWidth = if (cut) 2.dp.toPx() else 1.5.dp.toPx(),
                pathEffect = if (cut) null else PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
            )
        }
        Box(
            modifier = Modifier
                .size(if (cut) 28.dp else 22.dp)
                .clip(CircleShape)
                .background(if (cut) accent else MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.ContentCut,
                contentDescription = null,
                tint = if (cut) MaterialTheme.colorScheme.onPrimary else idle,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun PartChip(index: Int, part: List<Int>) {
    val first = part.first() + 1
    val last = part.last() + 1
    val label = if (first == last) {
        stringResource(R.string.document_split_part_label_single, index + 1, first)
    } else {
        stringResource(R.string.document_split_part_label, index + 1, "$first–$last")
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(partColor(index)))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun partColor(index: Int): Color {
    val palette = if (isToolDarkTheme()) DarkColors else LightColors
    return palette[index.mod(palette.size)]
}

private val LightColors = listOf(
    Color(0xFF0F766E), Color(0xFFB45309), Color(0xFF4338CA),
    Color(0xFF9D174D), Color(0xFF166534), Color(0xFF1D4ED8),
)
private val DarkColors = listOf(
    Color(0xFF2DD4BF), Color(0xFFFBBF24), Color(0xFFA5B4FC),
    Color(0xFFF9A8D4), Color(0xFF86EFAC), Color(0xFF93C5FD),
)

private val PageWidth = 80.dp
private val PageHeight = 110.dp
private val SeamWidth = 30.dp
