package `in`.c1ph3rj.scanly.feature.tools

import android.app.Activity
import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import `in`.c1ph3rj.scanly.core.ui.WindowWidthClass
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/*
 * Shared building blocks for the redesigned Tools flows (PDF tools + QR).
 *
 * Mirrors the "Scanly Redesign" foundations: 4-pt grid, radii 8 chip · 12 field ·
 * 14 card · 24 sheet, one primary button per screen, neutral icons, accent only for
 * the main action / selected state. Every colour comes from MaterialTheme roles so the
 * screens follow Light, Dark and Pure black without per-screen branching:
 *
 *   design token  → Material role
 *   bg            → background
 *   surf          → surface (cards, fields)
 *   surf2         → surfaceContainerHigh (wells, segmented tracks, search)
 *   line          → outlineVariant (card borders, dividers)
 *   tx / tx2      → onSurface / onSurfaceVariant
 *   acc / onacc   → primary / onPrimary
 *   accsoft/acctx → primaryContainer / onPrimaryContainer
 */
object ToolTokens {
    val ChipRadius = 8.dp
    val FieldRadius = 12.dp
    val CardRadius = 14.dp
    val SheetRadius = 24.dp
    val ButtonHeight = 52.dp
    val FieldHeight = 50.dp
    val ScreenPadding = 20.dp
    /** Material minimum touch target; text actions and segments must not go below this. */
    val MinTouch = 48.dp
}

/** Amber "heads up" colours from the design (warnbg / warntx / warnline). */
@Immutable
data class ToolWarningColors(val container: Color, val content: Color, val border: Color)

@Composable
@ReadOnlyComposable
fun toolWarningColors(): ToolWarningColors =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        ToolWarningColors(Color(0xFF2A2316), Color(0xFFFBBF24), Color(0xFF4A3B1C))
    } else {
        ToolWarningColors(Color(0xFFFFF4D6), Color(0xFF8A5A00), Color(0xFFF3DFA6))
    }

/** True when the current scheme is dark (works for the in-app theme override, not just system). */
@Composable
@ReadOnlyComposable
fun isToolDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

// ---------------------------------------------------------------------------------------------
// Headers
// ---------------------------------------------------------------------------------------------

/**
 * Top row with a 48dp back (or close) button and an optional step label, e.g.
 * "Step 1 of 3 · Choose a file". Handles the status bar inset itself.
 */
@Composable
fun ToolTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    stepText: String? = null,
    backIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    backContentDescription: String = "Back",
    centerStep: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 4.dp, end = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(backIcon, contentDescription = backContentDescription)
        }
        if (stepText != null) {
            Text(
                text = stepText,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = if (centerStep) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        trailing()
        if (centerStep) Spacer(Modifier.width(40.dp))
    }
}

/** Large page title (28sp bold) with an optional supporting line. */
@Composable
fun ToolLargeTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    // Short windows (phone landscape) get a smaller title so the tool's content stays in view.
    val compact = rememberWindowSizeInfo().isHeightConstrained
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = if (compact) 22.sp else 28.sp,
                lineHeight = if (compact) 28.sp else 34.sp,
                letterSpacing = (-0.6).sp,
            ),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (compact) 1 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Compact editor header: back, an 18sp title with a one-line file summary, and an
 * optional text action on the right ("Change").
 */
@Composable
fun ToolCompactHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            ToolTextAction(label = actionLabel, onClick = onAction)
        }
    }
}

/** Accent text button (44dp tall) for secondary inline actions like "Change". */
@Composable
fun ToolTextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    icon: ImageVector? = null,
) {
    Box(
        modifier = modifier
            .heightIn(min = ToolTokens.MinTouch)
            .clip(RoundedCornerShape(10.dp))
            .selectable(selected = false, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.5.sp),
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
        }
    }
}

/** Uppercase group label ("SCAN MODES", "IN SCANLY · 5 PDFS"). */
@Composable
fun ToolSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, letterSpacing = 0.8.sp),
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** 14sp semibold field/section label shown above controls ("Text", "Style", "Apply to"). */
@Composable
fun ToolFieldLabel(
    text: String,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(fontSize = if (muted) 13.sp else 14.sp),
        fontWeight = FontWeight.SemiBold,
        color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

// ---------------------------------------------------------------------------------------------
// Surfaces
// ---------------------------------------------------------------------------------------------

/** Card: surface fill + hairline border, 14dp radius. Clickable when [onClick] is set. */
@Composable
fun ToolCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    shape: RoundedCornerShape = RoundedCornerShape(ToolTokens.CardRadius),
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit,
) {
    val border = if (selected) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }
    val fill = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else color
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = fill,
            border = border,
            content = content,
        )
    } else {
        Surface(modifier = modifier, shape = shape, color = fill, border = border, content = content)
    }
}

/** 38dp rounded icon well used in tool tiles and list rows. */
@Composable
fun ToolIconWell(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    iconSize: Dp = 20.dp,
    accent: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(if (size >= 44.dp) 12.dp else 10.dp))
            .background(
                if (accent) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (accent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(iconSize),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Large-screen step layout
// ---------------------------------------------------------------------------------------------

/** One readable tool column on phones and in portrait. */
val ToolColumnMaxWidth = 640.dp

/** Tool steps that split into two panes use up to this width. */
val ToolSplitMaxWidth = 1100.dp

/**
 * Landscape windows with room lay a tool step out as two panes, like Watermark: the preview or
 * page grid on the start side, the settings beside it, instead of one narrow centred column.
 */
@Composable
fun rememberToolSplitLayout(): Boolean {
    val window = rememberWindowSizeInfo()
    return window.isLandscape && window.widthClass != WindowWidthClass.Compact
}

/**
 * Two panes filling the rest of a tool step: [start] (preview or pages) and [end] (settings,
 * scrolls on its own). Use inside the step's column, between its header and [ToolBottomBar].
 */
@Composable
fun ColumnScope.ToolSplitPanes(
    start: @Composable BoxScope.() -> Unit,
    end: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    startWeight: Float = 0.5f,
    endSpacing: Dp = 20.dp,
) {
    Row(
        modifier = modifier
            .weight(1f)
            .fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .weight(startWeight)
                .fillMaxHeight(),
            content = start,
        )
        Column(
            modifier = Modifier
                .weight(1f - startWeight)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(start = 12.dp, end = ToolTokens.ScreenPadding, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(endSpacing),
            content = end,
        )
    }
}

/** Sticky footer with a hairline top border; holds the screen's primary action. */
@Composable
fun ToolBottomBar(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // The divider spans the screen; the actions keep the 640 dp tool column on tablets.
        // Short windows trim the padding so the bar doesn't take a third of the height.
        val compact = rememberWindowSizeInfo().isHeightConstrained
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(
                    start = ToolTokens.ScreenPadding,
                    end = ToolTokens.ScreenPadding,
                    top = if (compact) 8.dp else 12.dp,
                    bottom = if (compact) 8.dp else 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
            content = content,
        )
    }
}

/** Neutral or warning note with a leading icon (e.g. "Pages are re-encoded as images"). */
@Composable
fun ToolNote(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Info,
    warning: Boolean = false,
) {
    val warn = toolWarningColors()
    val container = if (warning) warn.container else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (warning) warn.content else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Border before clip: drawn after clip, its outer half was cut off by the rounded shape.
            .then(
                if (warning) {
                    Modifier.border(1.dp, warn.border, RoundedCornerShape(ToolTokens.FieldRadius))
                } else {
                    Modifier
                },
            )
            .clip(RoundedCornerShape(ToolTokens.FieldRadius))
            .background(container)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
            color = if (warning) warn.content else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Buttons & choice controls
// ---------------------------------------------------------------------------------------------

/** The one primary button on a screen: 52dp, 14dp radius, accent fill. */
@Composable
fun ToolPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    loading: Boolean = false,
) {
    Surface(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().height(toolButtonHeight()),
        shape = RoundedCornerShape(ToolTokens.CardRadius),
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        ToolButtonContent(text = text, icon = icon, loading = loading)
    }
}

/** Outlined secondary button: transparent with a hairline border. */
@Composable
fun ToolSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    loading: Boolean = false,
) {
    Surface(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().height(toolButtonHeight()),
        shape = RoundedCornerShape(ToolTokens.CardRadius),
        color = Color.Transparent,
        contentColor = if (enabled) contentColor else MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        ToolButtonContent(text = text, icon = icon, loading = loading)
    }
}

/** 52 dp buttons, down to the 48 dp touch minimum on short windows. */
@Composable
private fun toolButtonHeight() =
    if (rememberWindowSizeInfo().isHeightConstrained) ToolTokens.MinTouch else ToolTokens.ButtonHeight

@Composable
private fun ToolButtonContent(text: String, icon: ImageVector?, loading: Boolean) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = androidx.compose.material3.LocalContentColor.current,
            )
            Spacer(Modifier.width(8.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.5.sp),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * iOS-style segmented control: surfaceContainerHigh track with 4dp inset; the selected
 * segment is a raised surface pill. Each option is a radio for accessibility.
 */
@Composable
fun <T> ToolSegmented(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: (T) -> String = { it.toString() },
    icon: ((T) -> ImageVector?)? = null,
    iconOnly: Boolean = false,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(ToolTokens.FieldRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val segmentShape = RoundedCornerShape(9.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(ToolTokens.MinTouch)
                    .then(
                        if (isSelected) {
                            Modifier
                                .shadow(if (isToolDarkTheme()) 0.dp else 1.dp, segmentShape)
                                .clip(segmentShape)
                                .background(MaterialTheme.colorScheme.surface)
                        } else {
                            Modifier.clip(segmentShape)
                        },
                    )
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onSelect(option) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                val tint = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                val vector = icon?.invoke(option)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 6.dp),
                ) {
                    if (vector != null) {
                        Icon(
                            vector,
                            contentDescription = if (iconOnly) label(option) else null,
                            tint = tint,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    if (!iconOnly) {
                        Text(
                            text = label(option),
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.5.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = tint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** 36dp pill chip. Selected = accent-soft fill + 1.5dp accent border. */
@Composable
fun ToolChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    solidWhenSelected: Boolean = false,
) {
    val shape = RoundedCornerShape(18.dp)
    val (fill, textColor, border) = when {
        selected && solidWhenSelected -> Triple(
            MaterialTheme.colorScheme.onSurface,
            MaterialTheme.colorScheme.surface,
            BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface),
        )
        selected -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onSurface,
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
        )
        else -> Triple(
            Color.Transparent,
            MaterialTheme.colorScheme.onSurface,
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        )
    }
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = ToolTokens.MinTouch)
            .semantics { this.selected = selected; role = Role.RadioButton },
        shape = shape,
        color = fill,
        contentColor = textColor,
        border = border,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

/** Round colour swatch radio (48dp target, 28dp dot, accent ring when selected). */
@Composable
fun ToolColorSwatch(
    color: Color,
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(ToolTokens.MinTouch)
            // Ring drawn before clip so its outer half is not cut off.
            .border(
                width = 2.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = CircleShape,
            )
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Fields
// ---------------------------------------------------------------------------------------------

/**
 * Single-line field in the design style: 12dp radius, surface fill, hairline border,
 * accent border on focus. Put a [ToolFieldLabel] above it rather than a floating label.
 */
@Composable
fun ToolTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    enabled: Boolean = true,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    emphasized: Boolean = false,
    textAlign: TextAlign = TextAlign.Start,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        isError = isError,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        suffix = suffix,
        supportingText = supportingText,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            fontSize = if (emphasized) 16.sp else 15.5.sp,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = textAlign,
        ),
        placeholder = placeholder?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        shape = RoundedCornerShape(ToolTokens.FieldRadius),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

/** Search field on a surfaceContainerHigh well, 48dp tall, no border. */
@Composable
fun ToolSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Clear search",
                    )
                }
            }
        } else {
            null
        },
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
        shape = RoundedCornerShape(ToolTokens.FieldRadius),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

/** "File name" editor used on Done screens: editable base name with a fixed extension suffix. */
@Composable
fun ToolFileNameField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    extension: String = ".pdf",
    label: String = "File name",
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ToolFieldLabel(label, muted = true)
        ToolTextField(
            value = value,
            onValueChange = { onValueChange(it.replace('\n', ' ')) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            emphasized = true,
            textAlign = TextAlign.End,
            suffix = {
                Text(
                    text = extension,
                    modifier = Modifier.padding(start = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Detail list
// ---------------------------------------------------------------------------------------------

@Immutable
data class ToolDetailRow(val label: String, val value: String)

/** Definition list in a bordered card: label column (84dp) + value, hairline separators. */
@Composable
fun ToolDetailList(
    rows: List<ToolDetailRow>,
    modifier: Modifier = Modifier,
    labelWidth: Dp = 84.dp,
) {
    ToolCard(modifier = modifier.fillMaxWidth()) {
        Column {
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(start = 14.dp),
                    )
                }
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = row.label,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(labelWidth),
                    )
                    Text(
                        text = row.value,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Two-up outlined shortcut tile with icon + title + caption ("Also lock it"). */
@Composable
fun ToolShortcutTile(
    icon: ImageVector,
    title: String,
    caption: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 60.dp),
        shape = RoundedCornerShape(ToolTokens.FieldRadius),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp), fontWeight = FontWeight.SemiBold)
                Text(
                    caption,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Paper previews
// ---------------------------------------------------------------------------------------------

/**
 * A document page drawn as real paper: white sheet, 3dp radius, soft shadow, A4 ratio by
 * default. Shows [bitmap] when present, a spinner while [loading], else a blank sheet.
 */
@Composable
fun ToolPaperPage(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    aspectRatio: Float? = null,
    elevation: Dp = 6.dp,
    contentDescription: String? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val ratio = aspectRatio
        ?: bitmap?.let { if (it.height > 0) it.width.toFloat() / it.height.toFloat() else null }
        ?: (1f / 1.414f)
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier = modifier
            .aspectRatio(ratio)
            .shadow(elevation, shape, clip = false)
            .clip(shape)
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null && !bitmap.isRecycled) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = Color(0xFF0F766E),
            )
        }
        overlay()
    }
}

/** Small round badge pinned to a thumbnail corner (lock, check, order number). */
@Composable
fun ToolBadge(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.background)
            .padding(2.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

// ---------------------------------------------------------------------------------------------
// Working state
// ---------------------------------------------------------------------------------------------

/**
 * Full-screen "working" state (e.g. Compressing): centred title, caption and a progress
 * bar. Pass [progress] in 0..1 for determinate work, null for indeterminate.
 */
@Composable
fun ToolWorkingState(
    title: String,
    caption: String,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    preview: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (preview != null) {
            preview()
            Spacer(Modifier.height(28.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = caption,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        val trackModifier = Modifier
            .fillMaxWidth(0.7f)
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = trackModifier,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
        } else {
            LinearProgressIndicator(
                modifier = trackModifier,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
        }
    }
}

/** Standard content padding for scrollable tool bodies. */
val ToolContentPadding = PaddingValues(start = ToolTokens.ScreenPadding, end = ToolTokens.ScreenPadding, top = 8.dp, bottom = 20.dp)

/**
 * For always-dark screens (camera, PDF reader): forces light status/navigation bar icons
 * while shown so the clock and indicators stay visible in the light theme, then restores the
 * previous appearance when the screen leaves composition.
 */
@Composable
fun ForceLightSystemBarIcons() {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousStatus = controller?.isAppearanceLightStatusBars
        val previousNavigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            if (controller != null) {
                previousStatus?.let { controller.isAppearanceLightStatusBars = it }
                previousNavigation?.let { controller.isAppearanceLightNavigationBars = it }
            }
        }
    }
}
