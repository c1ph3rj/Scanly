package `in`.c1ph3rj.scanly.feature.tools.qr

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.c1ph3rj.scanly.domain.model.QrHistoryEntry
import `in`.c1ph3rj.scanly.domain.model.QrOrigin
import `in`.c1ph3rj.scanly.domain.model.headline
import `in`.c1ph3rj.scanly.domain.model.summary
import `in`.c1ph3rj.scanly.feature.tools.ToolIconWell
import `in`.c1ph3rj.scanly.feature.tools.ToolLargeTitle
import `in`.c1ph3rj.scanly.feature.tools.ToolSearchField
import `in`.c1ph3rj.scanly.feature.tools.ToolSectionLabel
import `in`.c1ph3rj.scanly.feature.tools.ToolSegmented
import `in`.c1ph3rj.scanly.feature.tools.ToolTopBar
import java.time.ZoneId

/** All scanned and created codes, filterable and searchable. Tapping a row opens its result. */
@Composable
fun QrHistoryScreen(
    entries: List<QrHistoryEntry>,
    query: String,
    filter: QrHistoryFilter,
    onQueryChange: (String) -> Unit,
    onFilterChange: (QrHistoryFilter) -> Unit,
    onBack: () -> Unit,
    onOpen: (QrHistoryEntry) -> Unit,
    onClear: () -> Unit,
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val now = System.currentTimeMillis()
    val visible = remember(entries, query, filter) {
        entries.filter { qrHistoryMatches(it, query, filter) }
    }
    val sections = remember(visible, now) {
        QrHistorySection.entries.mapNotNull { section ->
            val items = visible.filter { qrHistorySectionOf(it.createdAtMillis, now, zone) == section }
            if (items.isEmpty()) null else section to items
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize(),
        ) {
            ToolTopBar(onBack = onBack)
            ToolLargeTitle(
                title = "QR history",
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(modifier = Modifier.height(14.dp))
            ToolSearchField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = "Search links, networks, names",
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(modifier = Modifier.height(10.dp))
            ToolSegmented(
                options = QrHistoryFilter.entries,
                selected = filter,
                onSelect = onFilterChange,
                label = { option -> option.label },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(modifier = Modifier.height(6.dp))

            when {
                entries.isEmpty() -> Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    QrEmptyState(
                        title = "No codes yet",
                        body = "Scanned and created codes are kept here.",
                    )
                }
                sections.isEmpty() -> Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    QrEmptyState(
                        title = "No matches",
                        body = "Try another search or filter.",
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
                ) {
                    sections.forEach { (section, sectionEntries) ->
                        item(key = "header-${section.name}") {
                            ToolSectionLabel(
                                text = section.label,
                                modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 6.dp),
                            )
                        }
                        items(items = sectionEntries, key = { it.id }) { entry ->
                            QrHistoryRow(
                                entry = entry,
                                nowMillis = now,
                                onClick = { onOpen(entry) },
                            )
                        }
                    }
                    item(key = "footer") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "History stays on this phone.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(
                                onClick = { confirmClear = true },
                                modifier = Modifier.heightIn(min = 44.dp),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                            ) {
                                Text(
                                    text = "Clear history…",
                                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear QR history?") },
            text = {
                Text("Every scanned and created code is removed from this phone. This can't be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onClear()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Clear history")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun QrHistoryRow(
    entry: QrHistoryEntry,
    nowMillis: Long,
    onClick: () -> Unit,
) {
    val payload = remember(entry.raw) { entry.payload }
    val scanned = entry.origin == QrOrigin.Scanned
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ToolIconWell(
                icon = qrKindIcon(payload),
                size = 40.dp,
                iconSize = 20.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = payload.headline,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = payload.summary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = formatQrHistoryTime(entry.createdAtMillis),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                QrOriginTag(scanned = scanned)
            }
        }
    }
}

@Composable
private fun QrOriginTag(scanned: Boolean) {
    Box(
        modifier = Modifier
            .heightIn(min = 20.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (scanned) MaterialTheme.colorScheme.surfaceContainerHigh
                else MaterialTheme.colorScheme.primaryContainer,
            )
            .padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (scanned) "Scanned" else "Created",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            fontWeight = FontWeight.SemiBold,
            color = if (scanned) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
            maxLines = 1,
        )
    }
}
