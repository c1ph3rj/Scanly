package `in`.c1ph3rj.scanly.feature.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Segmented list row: each row is its own surface so the pressed highlight follows
 * its shape. Outer corners of the group are larger than the inner ones, and a small
 * gap replaces dividers.
 */
@Composable
fun LibraryListItem(
    first: Boolean,
    last: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val outer = 20.dp
    val inner = 6.dp
    val shape = RoundedCornerShape(
        topStart = if (first) outer else inner,
        topEnd = if (first) outer else inner,
        bottomStart = if (last) outer else inner,
        bottomEnd = if (last) outer else inner,
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = if (last) 0.dp else 3.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        content()
    }
}

/** Paint, ripple, and hit bounds all share the same 48 dp rounded square. */
@Composable
fun LibraryIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
