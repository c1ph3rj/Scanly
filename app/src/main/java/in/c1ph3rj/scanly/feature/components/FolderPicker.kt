package `in`.c1ph3rj.scanly.feature.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.model.GroupTitleFormat

/**
 * Unified folder picker used wherever a document can be moved between folders
 * (Library cards and the document detail screen). It clearly shows the document's
 * current folder, lets the user switch to another folder, remove it from its
 * current folder ("No folder"), or create a new folder and move into it in one step.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MoveToFolderSheet(
    currentGroupId: String?,
    groups: List<DocumentGroup>,
    onDismiss: () -> Unit,
    onSelectFolder: (String?) -> Unit,
    onCreateFolderAndMove: (String) -> Unit,
    onSuggestFolderName: (suspend (GroupTitleFormat) -> String)? = null,
) {
    var creatingFolder by rememberSaveable { mutableStateOf(false) }
    var newFolderName by rememberSaveable { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        ScanlySheetContent {
            Text(
                text = "Move to folder",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            FolderPickerRow(
                label = "No folder",
                icon = Icons.Filled.FolderOff,
                selected = currentGroupId == null,
                onClick = { onSelectFolder(null) },
            )

            if (groups.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(groups, key = { it.id }) { group ->
                        FolderPickerRow(
                            label = group.title,
                            icon = Icons.Filled.Folder,
                            selected = currentGroupId == group.id,
                            onClick = { onSelectFolder(group.id) },
                        )
                    }
                }
            }

            if (creatingFolder) {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("New folder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (onSuggestFolderName != null) {
                    GroupTitleSuggestRow(
                        onSuggestTitle = onSuggestFolderName,
                        onSuggested = { newFolderName = it },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        onClick = {
                            creatingFolder = false
                            newFolderName = ""
                        },
                    ) { Text("Cancel") }
                    Button(
                        onClick = {
                            val name = newFolderName.trim()
                            if (name.isNotEmpty()) onCreateFolderAndMove(name)
                        },
                        enabled = newFolderName.isNotBlank(),
                    ) { Text("Create & move") }
                }
            } else {
                FolderPickerRow(
                    label = "Create new folder",
                    icon = Icons.Filled.CreateNewFolder,
                    selected = false,
                    onClick = { creatingFolder = true },
                )
            }
        }
    }
}

@Composable
fun FolderPickerRow(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedAccentColor = MaterialTheme.colorScheme.primary
    val rowShape = MaterialTheme.shapes.large
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = rowShape,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) {
                selectedAccentColor.copy(alpha = 0.64f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (selected) {
                Box(modifier = Modifier.matchParentSize()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .width(4.dp)
                            .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                            .background(selectedAccentColor),
                    )
                }
            }
            Row(
                modifier = Modifier.padding(
                    start = if (selected) 18.dp else 14.dp,
                    top = 12.dp,
                    end = 14.dp,
                    bottom = 12.dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) {
                        selectedAccentColor
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = selectedAccentColor,
                    )
                }
            }
        }
    }
}
