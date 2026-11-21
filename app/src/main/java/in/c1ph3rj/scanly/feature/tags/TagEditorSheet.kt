package `in`.c1ph3rj.scanly.feature.tags

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.domain.model.DocumentTag
import `in`.c1ph3rj.scanly.domain.model.DocumentTagRules
import `in`.c1ph3rj.scanly.feature.components.ScanlyModalBottomSheet
import `in`.c1ph3rj.scanly.feature.components.ScanlySheetContent

private val ChipListMaxHeight = 280.dp

/**
 * A document's tags as a compact row for its header. With no tags it is a quiet "Add tags"
 * affordance. The whole row is one target that opens the tag editor.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DocumentTagsRow(
    tags: List<DocumentTag>,
    onEditTags: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editLabel = stringResource(R.string.tags_document_edit)
    val rowDescription = if (tags.isEmpty()) {
        stringResource(R.string.tags_document_add)
    } else {
        stringResource(R.string.tags_document_row_description, tags.joinToString(", ") { it.name })
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.large)
            .semantics { contentDescription = rowDescription }
            .clickable(onClickLabel = editLabel, role = Role.Button, onClick = onEditTags)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        // The row's description already names every tag; don't read them twice.
        Row(
            modifier = Modifier.clearAndSetSemantics {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Label,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            if (tags.isEmpty()) {
                Text(
                    text = stringResource(R.string.tags_document_add),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    tags.forEach { tag -> TagPill(tag.name) }
                }
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Read-only tag label; the row around it handles taps. */
@Composable
private fun TagPill(name: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = CircleShape,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/**
 * Picks a document's tags. Every tag is a toggle chip; typing filters them and offers to create
 * a new one (a name that already exists, in any case, selects that tag instead). Nothing is
 * saved until Done, which reports the chosen existing tag ids plus the names of tags to create.
 *
 * @param allTags Every tag, sorted by name.
 * @param selectedTagIds Tags the document carries now.
 * @param onSave Chosen existing tag ids and new tag names (normalized, distinct by
 *   [DocumentTagRules.key]); the caller creates the new tags and applies the set.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TagEditorSheet(
    allTags: List<DocumentTag>,
    selectedTagIds: Set<String>,
    onDismiss: () -> Unit,
    onSave: (tagIds: Set<String>, newTagNames: List<String>) -> Unit,
) {
    // ArrayList so the picks survive rotation through the saveable state bundle.
    var selected by rememberSaveable { mutableStateOf(ArrayList(selectedTagIds)) }
    var pendingNames by rememberSaveable { mutableStateOf(ArrayList<String>()) }
    var input by rememberSaveable { mutableStateOf("") }

    val typedName = DocumentTagRules.normalizeName(input)
    val typedKey = DocumentTagRules.key(input)
    val visibleTags = if (typedName.isEmpty()) {
        allTags
    } else {
        allTags.filter { DocumentTagRules.key(it.name).contains(typedKey) }
    }
    val canCreate = typedName.isNotEmpty() &&
        allTags.none { DocumentTagRules.key(it.name) == typedKey } &&
        pendingNames.none { DocumentTagRules.key(it) == typedKey }

    fun addTyped() {
        if (typedName.isEmpty()) return
        val existing = allTags.firstOrNull { DocumentTagRules.key(it.name) == typedKey }
        when {
            existing != null -> if (existing.id !in selected) selected = ArrayList(selected + existing.id)
            pendingNames.none { DocumentTagRules.key(it) == typedKey } -> pendingNames = ArrayList(pendingNames + typedName)
        }
        input = ""
    }

    ScanlyModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = { MaterialTheme.colorScheme.surface },
    ) {
        ScanlySheetContent {
            Text(
                text = stringResource(R.string.tags_editor_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.take(DocumentTagRules.MAX_NAME_LENGTH) },
                label = { Text(stringResource(R.string.tags_editor_field_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = ::addTyped, enabled = typedName.isNotEmpty()) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.tags_editor_add))
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addTyped() }),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = ChipListMaxHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (allTags.isEmpty() && pendingNames.isEmpty() && !canCreate) {
                    Text(
                        text = stringResource(R.string.tags_editor_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (canCreate) {
                        FilterChip(
                            selected = false,
                            onClick = ::addTyped,
                            label = {
                                Text(
                                    stringResource(R.string.tags_editor_create_chip, typedName),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize))
                            },
                            shape = CircleShape,
                        )
                    }
                    pendingNames.forEach { name ->
                        TagToggleChip(name = name, selected = true) {
                            pendingNames = ArrayList(pendingNames - name)
                        }
                    }
                    visibleTags.forEach { tag ->
                        val isSelected = tag.id in selected
                        TagToggleChip(name = tag.name, selected = isSelected) {
                            selected = ArrayList(if (isSelected) selected - tag.id else selected + tag.id)
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.export_share_cancel))
                }
                Button(
                    onClick = {
                        // A name typed but not yet added still counts, so Done never drops it.
                        val names = if (canCreate) pendingNames + typedName else pendingNames.toList()
                        val ids = selected.toSet() +
                            listOfNotNull(allTags.firstOrNull { typedName.isNotEmpty() && DocumentTagRules.key(it.name) == typedKey }?.id)
                        onSave(ids.filterTo(mutableSetOf()) { id -> allTags.any { it.id == id } }, names)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.tags_editor_done))
                }
            }
        }
    }
}

/** A tag as a toggle chip; selected chips show a check (the chip itself announces its state). */
@Composable
private fun TagToggleChip(name: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.inverseSurface,
            selectedLabelColor = MaterialTheme.colorScheme.inverseOnSurface,
            selectedLeadingIconColor = MaterialTheme.colorScheme.inverseOnSurface,
        ),
    )
}
