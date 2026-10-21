package `in`.c1ph3rj.scanly.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.c1ph3rj.scanly.domain.trash.TrashKind
import `in`.c1ph3rj.scanly.feature.components.ScanlyConfirmDialog
import `in`.c1ph3rj.scanly.feature.components.ScanlyFormDialogShell

/**
 * Asks what deleting a folder should do with its documents: delete them with it, keep them in
 * the library ("Delete folder only"), or cancel. An empty folder has nothing to decide, so it gets
 * a plain confirmation. Either way the folder goes to Recently deleted.
 *
 * [onDelete] receives true to delete the documents with the folder.
 */
@Composable
fun FolderDeleteDialog(
    title: String,
    documentCount: Int,
    onDismiss: () -> Unit,
    onDelete: (includeDocuments: Boolean) -> Unit,
) {
    if (documentCount == 0) {
        ScanlyConfirmDialog(
            title = "Delete folder?",
            text = TrashCopy.folder(title, documentCount = 0),
            onDismiss = onDismiss,
            onConfirm = { onDelete(true) },
            confirmLabel = "Delete",
            confirmDestructive = true,
        )
        return
    }
    val documents = if (documentCount == 1) "1 document" else "$documentCount documents"
    val them = if (documentCount == 1) "it" else "them"
    ScanlyFormDialogShell(onDismiss = onDismiss) {
        Text(
            text = "Delete folder?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "\"$title\" has $documents. What should happen to $them?",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FolderDeleteOption(
                icon = Icons.AutoMirrored.Outlined.DriveFileMove,
                title = "Delete folder only",
                subtitle = "Keep the $documents in your Library",
                onClick = { onDelete(false) },
            )
            FolderDeleteOption(
                icon = Icons.Outlined.DeleteForever,
                title = "Delete folder and documents",
                subtitle = "Both go to Recently deleted for ${TrashKind.FOLDER.retentionLabel()}",
                destructive = true,
                onClick = { onDelete(true) },
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    }
}

/** A full-width choice: icon, label and what it does. Destructive choices use the error colour. */
@Composable
private fun FolderDeleteOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val accent = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (destructive) accent else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
