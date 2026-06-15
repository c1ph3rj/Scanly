package `in`.c1ph3rj.scanly.feature.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
private fun fabMenuElevation() = FloatingActionButtonDefaults.elevation(
    defaultElevation = 2.dp,
    pressedElevation = 4.dp,
    focusedElevation = 2.dp,
    hoveredElevation = 3.dp,
)

@Composable
private fun fabMenuItemElevation() = FloatingActionButtonDefaults.elevation(
    defaultElevation = 1.dp,
    pressedElevation = 2.dp,
    focusedElevation = 1.dp,
    hoveredElevation = 2.dp,
)

@Composable
fun FabMenuScrim(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrimBase = MaterialTheme.colorScheme.scrim
    val alpha by animateFloatAsState(
        targetValue = if (visible) 0.52f else 0f,
        animationSpec = tween(durationMillis = if (visible) 220 else 180),
        label = "fabScrimAlpha",
    )

    BackHandler(enabled = visible, onBack = onDismiss)

    if (alpha > 0.01f) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha }
                .background(scrimBase)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
    }
}

@Composable
fun ScanlyExpandableFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onNewFolder: () -> Unit,
    onNewDocument: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AnimatedFabMenuItem(visible = expanded, index = 0) {
            ScanlyFabMenuAction(
                text = "New Folder",
                icon = Icons.Filled.CreateNewFolder,
                onClick = {
                    onExpandedChange(false)
                    onNewFolder()
                },
            )
        }
        AnimatedFabMenuItem(visible = expanded, index = 1) {
            ScanlyFabMenuAction(
                text = "New Document",
                icon = Icons.Filled.Description,
                onClick = {
                    onExpandedChange(false)
                    onNewDocument()
                },
            )
        }
        FloatingActionButton(
            onClick = { onExpandedChange(!expanded) },
            modifier = Modifier.graphicsLayer {
                shape = CircleShape
                clip = true
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = fabMenuElevation(),
        ) {
            AnimatedFabIconRotation(expanded = expanded) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = if (expanded) "Close menu" else "Open add menu",
                )
            }
        }
    }
}

@Composable
fun ScanlyExtendedFab(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Add,
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        icon = { Icon(icon, contentDescription = null) },
        text = { Text(text) },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = fabMenuElevation(),
    )
}

@Composable
private fun ScanlyFabMenuAction(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        text = { Text(text) },
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        elevation = fabMenuItemElevation(),
    )
}

@Composable
fun AnimatedFabMenuItem(
    visible: Boolean,
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val delay = index * 40
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(200, delayMillis = delay)) +
            slideInVertically(
                initialOffsetY = { it / 3 },
                animationSpec = tween(220, delayMillis = delay),
            ),
        exit = fadeOut(animationSpec = tween(140)) +
            slideOutVertically(
                targetOffsetY = { it / 3 },
                animationSpec = tween(140),
            ),
    ) {
        content()
    }
}

@Composable
fun AnimatedFabIconRotation(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = tween(200),
        label = "fabIconRotation",
    )
    Box(modifier = modifier.graphicsLayer { rotationZ = rotation }) {
        content()
    }
}
