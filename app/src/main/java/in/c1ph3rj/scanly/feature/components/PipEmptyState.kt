package `in`.c1ph3rj.scanly.feature.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import `in`.c1ph3rj.scanly.feature.pip.LocalPipPreferences
import `in`.c1ph3rj.scanly.feature.pip.PipLook
import `in`.c1ph3rj.scanly.feature.pip.PipMood
import `in`.c1ph3rj.scanly.feature.pip.PipSays

/**
 * Shared zero-content and first-use state: Pip says one short line above the title, description
 * and actions. With Pip switched off in settings the card keeps only the plain copy, which always
 * carries the full meaning on its own.
 */
@Composable
fun PipEmptyState(
    pipLine: String,
    pipMood: PipMood,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    pipLook: PipLook = PipLook.AHEAD,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionIcon: ImageVector? = null,
    actionEnabled: Boolean = true,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    secondaryActionEnabled: Boolean = true,
    compact: Boolean = false,
) {
    val pipEnabled = LocalPipPreferences.current.enabled
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (compact) 22.dp else 28.dp,
                    vertical = if (compact) 24.dp else 30.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            if (pipEnabled) {
                PipSays(
                    line = pipLine,
                    mood = pipMood,
                    look = pipLook,
                    size = if (compact) 80.dp else 96.dp,
                )
                Spacer(Modifier.height(if (compact) 16.dp else 20.dp))
            }
            Text(
                text = title,
                style = if (compact) {
                    MaterialTheme.typography.titleLarge
                } else {
                    MaterialTheme.typography.headlineSmall
                },
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(if (compact) 8.dp else 10.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(if (compact) 14.dp else 18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onAction,
                        enabled = actionEnabled,
                        modifier = Modifier.height(50.dp),
                        shape = RoundedCornerShape(25.dp),
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    ) {
                        if (actionIcon != null) {
                            Icon(
                                imageVector = actionIcon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        }
                        Text(actionLabel)
                    }
                    if (secondaryActionLabel != null && onSecondaryAction != null) {
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = onSecondaryAction,
                            enabled = secondaryActionEnabled,
                            modifier = Modifier.height(50.dp),
                        ) {
                            Text(secondaryActionLabel)
                        }
                    }
                }
            }
        }
    }
}
