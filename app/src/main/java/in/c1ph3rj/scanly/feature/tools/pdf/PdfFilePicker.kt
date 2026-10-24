package `in`.c1ph3rj.scanly.feature.tools.pdf

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.c1ph3rj.scanly.core.ui.PreviewDisplaySize
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.feature.components.CachedThumbnail
import `in`.c1ph3rj.scanly.feature.tools.ToolBottomBar
import `in`.c1ph3rj.scanly.feature.tools.ToolCard
import `in`.c1ph3rj.scanly.feature.tools.ToolIconWell
import `in`.c1ph3rj.scanly.feature.tools.ToolLargeTitle
import `in`.c1ph3rj.scanly.feature.tools.ToolSearchField
import `in`.c1ph3rj.scanly.feature.tools.ToolSectionLabel
import `in`.c1ph3rj.scanly.feature.tools.ToolTokens
import `in`.c1ph3rj.scanly.feature.tools.ToolTopBar

/** Library ordering offered by the picker's sort pill. */
enum class PdfPickerSort(val label: String) {
    Recent("Recent"),
    Name("Name"),
    MostPages("Most pages"),
}

/**
 * Full-screen "Choose a PDF" step shared by every PDF tool (design: PdfPicker).
 *
 * - "Phone storage" opens the system document picker ([onPickDevice]).
 * - "In Scanly" lists library documents as real page thumbnails. Empty scans (0 pages)
 *   are hidden — they cannot produce a PDF — and counted in a footnote.
 * - Single-select ([selectedOrder] == null): tapping a row calls [onPickLibrary].
 * - Multi-select (merge): pass the ordered selected ids; rows show their merge position
 *   and [onPickLibrary] toggles. Supply [bottomBar] for the confirm action.
 * - Non-PDF tools (Image to text) relabel the device row and the copy with [deviceTitle],
 *   [deviceIcon], [itemNoun], [countLabel] and the hint parameters.
 */
@Composable
fun PdfFilePickerScreen(
    title: String,
    subtitle: String,
    stepText: String?,
    documents: List<ScanDocument>,
    onBack: () -> Unit,
    onPickDevice: () -> Unit,
    onPickLibrary: (ScanDocument) -> Unit,
    modifier: Modifier = Modifier,
    deviceTitle: String = "Phone storage",
    deviceSubtitle: String = "Downloads, Drive and other apps",
    deviceIcon: ImageVector = Icons.Outlined.PhoneAndroid,
    itemNoun: String = "PDFs",
    countLabel: (Int) -> String = ::formatMergeFileCount,
    emptyLibraryHint: String = "No scans yet. Pick a PDF from phone storage, or scan one first.",
    noPagesHint: String = "Your scans have no pages yet. Pick a PDF from phone storage.",
    selectedOrder: List<String>? = null,
    defaultSort: PdfPickerSort = PdfPickerSort.Recent,
    rowMeta: (ScanDocument) -> String = ::defaultPdfPickerMeta,
    headerExtra: (@Composable ColumnScope.() -> Unit)? = null,
    bottomBar: (@Composable ColumnScope.() -> Unit)? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(defaultSort) }
    val usable = remember(documents) { documents.filter { it.pageCount > 0 } }
    val hiddenEmpty = documents.size - usable.size
    val visible = remember(usable, query, sort) {
        val needle = query.trim()
        usable
            .filter { needle.isEmpty() || it.title.contains(needle, ignoreCase = true) }
            .let { list ->
                when (sort) {
                    PdfPickerSort.Recent -> list.sortedByDescending { it.updatedAtMillis }
                    PdfPickerSort.Name -> list.sortedBy { it.title.lowercase() }
                    PdfPickerSort.MostPages -> list.sortedByDescending { it.pageCount }
                }
            }
    }

    val scrollHeader = rememberWindowSizeInfo().isHeightConstrained

    // One readable, centred column on tablets; every tool's "choose a file" step looks the same.
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(modifier = Modifier.widthIn(max = 640.dp).fillMaxSize()) {
            ToolTopBar(onBack = onBack, stepText = stepText)
            val header: @Composable (horizontalPadding: Dp) -> Unit = { horizontalPadding ->
                Column(
                    modifier = Modifier.padding(start = horizontalPadding, end = horizontalPadding, top = 2.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(if (scrollHeader) 10.dp else 14.dp),
                ) {
                    ToolLargeTitle(title = title, subtitle = subtitle)
                    if (usable.isNotEmpty()) {
                        ToolSearchField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = "Search your $itemNoun",
                        )
                    }
                    headerExtra?.invoke(this)
                }
            }
            if (!scrollHeader) header(20.dp)
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // Short windows: the title and search scroll away with the list, so files show.
                if (scrollHeader) {
                    item(key = "picker_header") { header(12.dp) }
                }
                item(key = "device") {
                    ToolCard(
                        onClick = onPickDevice,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                            .padding(bottom = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .heightIn(min = 64.dp)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            ToolIconWell(deviceIcon, size = 44.dp, iconSize = 22.dp, accent = true)
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    deviceTitle,
                                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    deviceSubtitle,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (usable.isNotEmpty()) {
                    item(key = "header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ToolSectionLabel(
                                text = "In Scanly · ${countLabel(usable.size)}",
                                modifier = Modifier.weight(1f),
                            )
                            Surface(
                                onClick = {
                                    val all = PdfPickerSort.entries
                                    sort = all[(all.indexOf(sort) + 1) % all.size]
                                },
                                shape = RoundedCornerShape(18.dp),
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .heightIn(min = 36.dp)
                                    .semantics { stateDescription = "Sorted by ${sort.label}" },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        sort.label,
                                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Icon(Icons.Outlined.SwapVert, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
                items(visible, key = { it.id }) { document ->
                    val position = selectedOrder?.indexOf(document.id)?.takeIf { it >= 0 }
                    PdfPickerRow(
                        document = document,
                        meta = rowMeta(document),
                        multiSelect = selectedOrder != null,
                        selectedPosition = position,
                        onClick = { onPickLibrary(document) },
                    )
                }
                item(key = "footer") {
                    val footer = when {
                        usable.isEmpty() && hiddenEmpty == 0 -> emptyLibraryHint
                        usable.isEmpty() -> noPagesHint
                        visible.isEmpty() -> "No $itemNoun match \"${query.trim()}\"."
                        hiddenEmpty == 1 -> "1 empty scan is hidden"
                        hiddenEmpty > 1 -> "$hiddenEmpty empty scans are hidden"
                        else -> null
                    }
                    if (footer != null) {
                        Text(
                            text = footer,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 14.dp),
                        )
                    }
                }
            }
            if (bottomBar != null) {
                ToolBottomBar(content = bottomBar)
            }
        }
    }
}

@Composable
private fun PdfPickerRow(
    document: ScanDocument,
    meta: String,
    multiSelect: Boolean,
    selectedPosition: Int?,
    onClick: () -> Unit,
) {
    val selected = selectedPosition != null
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(ToolTokens.CardRadius),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                if (multiSelect) {
                    stateDescription = if (selected) "Selected, position ${selectedPosition!! + 1}" else "Not selected"
                }
            },
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 72.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box {
                PdfPaperThumbnail(document = document, width = 44.dp)
                if (selectedPosition != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 6.dp, y = 6.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = (selectedPosition + 1).toString(),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = document.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 19.sp),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (multiSelect) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .then(
                            if (selected) {
                                Modifier.background(MaterialTheme.colorScheme.primary)
                            } else {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * A library document's cover drawn as a small sheet of paper (white, soft shadow,
 * A4-ish ratio). Use for list rows and order lists.
 */
@Composable
fun PdfPaperThumbnail(
    document: ScanDocument,
    width: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val height = width * 1.41f
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier = modifier
            .size(width = width, height = height)
            .shadow(2.dp, shape)
            .clip(shape)
            .background(Color.White),
    ) {
        CachedThumbnail(
            thumbnailPath = document.coverThumbnailPath,
            title = document.title,
            displaySize = PreviewDisplaySize.COMPACT,
            contentRevision = document.updatedAtMillis,
            shape = shape,
            containerColor = Color.White,
            modifier = Modifier.fillMaxSize(),
            placeholderIcon = {
                Icon(
                    Icons.Filled.Description,
                    contentDescription = null,
                    tint = Color(0xFF6B757C),
                    modifier = Modifier.size(width * 0.45f),
                )
            },
        )
    }
}

/** "6 pages · Edited 2 days ago". */
fun defaultPdfPickerMeta(document: ScanDocument): String =
    "${formatPdfPageCount(document.pageCount)} · ${formatPdfRelativeTime(document.updatedAtMillis)}"

/** "Today", "Yesterday", "3 days ago", "12 Sep" — matches the design's list metadata. */
fun formatPdfRelativeTime(millis: Long, now: Long = System.currentTimeMillis()): String {
    if (millis <= 0L) return ""
    return when {
        DateUtils.isToday(millis) -> "Today"
        DateUtils.isToday(millis + DateUtils.DAY_IN_MILLIS) -> "Yesterday"
        now - millis < 7 * DateUtils.DAY_IN_MILLIS ->
            "${((now - millis) / DateUtils.DAY_IN_MILLIS).coerceAtLeast(2)} days ago"
        else -> java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(java.util.Date(millis))
    }
}
