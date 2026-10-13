package `in`.c1ph3rj.scanly.feature.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Section title with an optional count and an optional "See all"-style pill action. */
@Composable
fun LibrarySectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionIcon: ImageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LibrarySectionTitle(title, count, modifier = Modifier.weight(1f))
        if (actionLabel != null && onAction != null) {
            // Only the action is pressable; its padding keeps the label clear of the highlight edge.
            // The offset lines the chevron up with the content edge.
            Row(
                modifier = Modifier
                    .offset(x = 8.dp)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(start = 14.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    actionIcon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
fun LibrarySectionTitle(title: String, count: Int?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (count != null) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
