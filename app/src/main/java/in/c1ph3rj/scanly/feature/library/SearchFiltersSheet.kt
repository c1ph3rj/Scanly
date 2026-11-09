package `in`.c1ph3rj.scanly.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.c1ph3rj.scanly.domain.model.DateFilter
import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.model.DocumentKind
import `in`.c1ph3rj.scanly.domain.model.FolderFilter
import `in`.c1ph3rj.scanly.domain.model.SearchFilters
import `in`.c1ph3rj.scanly.domain.model.SearchSort
import `in`.c1ph3rj.scanly.feature.components.ScanlyModalBottomSheet
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import `in`.c1ph3rj.scanly.R
import androidx.compose.ui.res.stringResource
import `in`.c1ph3rj.scanly.core.common.appString
import androidx.compose.ui.res.pluralStringResource

/**
 * Type, date, folder and sort for search, in one sheet. Choices apply as they are tapped, so the
 * results behind the sheet (and the count on its button) stay live.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchFiltersSheet(
    filters: SearchFilters,
    availableKinds: List<DocumentKind>,
    kindCounts: Map<DocumentKind, Int>,
    folders: List<DocumentGroup>,
    resultCount: Int,
    showsResults: Boolean,
    onFiltersChange: (SearchFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    var pickingRange by rememberSaveable { mutableStateOf(false) }
    ScanlyModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                stringResource(R.string.filter_picker_filters),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            if (availableKinds.isNotEmpty()) {
                FilterSection(stringResource(R.string.search_filters_type)) {
                    SheetChip(stringResource(R.string.search_filters_any_type), filters.kind == null) { onFiltersChange(filters.copy(kind = null)) }
                    availableKinds.forEach { kind ->
                        val count = kindCounts[kind]
                        SheetChip(if (count != null) "${kind.label} · $count" else kind.label, filters.kind == kind) {
                            onFiltersChange(filters.copy(kind = kind))
                        }
                    }
                }
            }
            FilterSection(stringResource(R.string.search_filters_date_modified)) {
                DateFilter.entries.forEach { option ->
                    val label = if (option == DateFilter.CUSTOM) customRangeLabel(filters) ?: option.label else option.label
                    SheetChip(label, filters.date == option) {
                        if (option == DateFilter.CUSTOM) pickingRange = true else onFiltersChange(filters.copy(date = option))
                    }
                }
            }
            if (folders.isNotEmpty()) {
                FilterSection(stringResource(R.string.home_folder)) {
                    SheetChip(stringResource(R.string.search_filters_any_folder), filters.folder == FolderFilter.Any) {
                        onFiltersChange(filters.copy(folder = FolderFilter.Any))
                    }
                    SheetChip(stringResource(R.string.search_filters_not_in_a_folder), filters.folder == FolderFilter.None) {
                        onFiltersChange(filters.copy(folder = FolderFilter.None))
                    }
                    folders.forEach { group ->
                        SheetChip(group.title, filters.folder == FolderFilter.In(group.id)) {
                            onFiltersChange(filters.copy(folder = FolderFilter.In(group.id)))
                        }
                    }
                }
            }
            FilterSection(stringResource(R.string.search_filters_sort_by)) {
                SearchSort.entries.forEach { option ->
                    SheetChip(option.label, filters.sort == option) { onFiltersChange(filters.copy(sort = option)) }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(onClick = { onFiltersChange(SearchFilters()) }, enabled = filters.activeCount > 0) {
                    Text(stringResource(R.string.page_crop_reset))
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = onDismiss) {
                    Text(
                        if (showsResults) {
                            pluralStringResource(R.plurals.search_filters_show_results, resultCount, resultCount)
                        } else {
                            stringResource(R.string.camera_capture_chrome_done)
                        },
                    )
                }
            }
        }
    }
    if (pickingRange) {
        CustomRangeDialog(
            filters = filters,
            onPick = { start, end ->
                pickingRange = false
                onFiltersChange(filters.copy(date = DateFilter.CUSTOM, customStartUtcMillis = start, customEndUtcMillis = end))
            },
            onDismiss = { pickingRange = false },
        )
    }
}

/**
 * Removable chips for the filters in effect, shown in the filter row so they stay visible after
 * the sheet closes. Tapping one clears that filter.
 */
@Composable
fun ActiveFilterChips(
    filters: SearchFilters,
    folders: List<DocumentGroup>,
    onFiltersChange: (SearchFilters) -> Unit,
) {
    filters.kind?.let { kind ->
        RemovableChip(kind.label) { onFiltersChange(filters.copy(kind = null)) }
    }
    if (filters.date != DateFilter.ANY) {
        val label = if (filters.date == DateFilter.CUSTOM) customRangeLabel(filters) ?: filters.date.label else filters.date.label
        RemovableChip(label) {
            onFiltersChange(filters.copy(date = DateFilter.ANY, customStartUtcMillis = null, customEndUtcMillis = null))
        }
    }
    when (val folder = filters.folder) {
        FolderFilter.Any -> Unit
        FolderFilter.None -> RemovableChip(stringResource(R.string.search_filters_not_in_a_folder)) { onFiltersChange(filters.copy(folder = FolderFilter.Any)) }
        is FolderFilter.In -> folders.firstOrNull { it.id == folder.groupId }?.let { group ->
            RemovableChip(stringResource(R.string.search_filters_in, group.title)) { onFiltersChange(filters.copy(folder = FolderFilter.Any)) }
        }
    }
    if (filters.sort != SearchSort.BEST_MATCH) {
        RemovableChip(filters.sort.label) { onFiltersChange(filters.copy(sort = SearchSort.BEST_MATCH)) }
    }
}

@Composable
private fun RemovableChip(label: String, onRemove: () -> Unit) {
    InputChip(
        selected = true,
        onClick = onRemove,
        label = { Text(label, maxLines = 1) },
        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, Modifier.size(InputChipDefaults.IconSize)) },
        shape = CircleShape,
        modifier = Modifier.semantics { contentDescription = appString(R.string.search_filters_remove_filter, label) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) { content() }
    }
}

@Composable
private fun SheetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.inverseSurface,
            selectedLabelColor = MaterialTheme.colorScheme.inverseOnSurface,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomRangeDialog(filters: SearchFilters, onPick: (start: Long, end: Long?) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = filters.customStartUtcMillis,
        initialSelectedEndDateMillis = filters.customEndUtcMillis,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedStartDateMillis?.let { onPick(it, state.selectedEndDateMillis) } },
                enabled = state.selectedStartDateMillis != null,
            ) { Text(stringResource(R.string.search_filters_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.export_share_cancel)) } },
    ) {
        DateRangePicker(state = state, modifier = Modifier.weight(1f))
    }
}

/** "2 Nov 2026 – 9 Nov 2026" for a custom range, or null when none is chosen. */
private fun customRangeLabel(filters: SearchFilters): String? {
    val start = filters.customStartUtcMillis ?: return null
    // Built per call so a language change while the app runs is picked up.
    val dayFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
    fun day(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().format(dayFormat)
    val end = filters.customEndUtcMillis?.takeIf { it != start }
    return if (end == null) day(start) else "${day(start)} – ${day(end)}"
}
