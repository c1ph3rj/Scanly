package `in`.c1ph3rj.scanly.feature.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.core.common.AppLanguage
import `in`.c1ph3rj.scanly.core.common.AppLocales
import `in`.c1ph3rj.scanly.feature.camera.CameraPermissionSupport
import java.util.Locale

/**
 * First screen of a fresh install: the Scanly logo, what the app does, and the app language.
 *
 * Picking a language applies it at once (through [AppLocales]), so the rest of this screen and the
 * introduction that follows are already in that language. "Phone language" clears the choice.
 */
@Composable
fun WelcomeRoute(onContinue: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Re-read on every configuration (language) change; local state answers taps instantly.
    // Both reads go to LocaleManager / preferences, so they run once per configuration, not per frame.
    val configuration = LocalConfiguration.current
    var selected by remember(configuration) { mutableStateOf(AppLocales.selectedLanguage(context)) }
    val deviceLocale = remember(configuration) { AppLocales.deviceLocale(context) }
    // One stable callback, so rows don't recompose just because this route did.
    // Keyed like `selected` above, so it always writes to the current state holder.
    val onLanguageSelected: (AppLanguage?) -> Unit = remember(context, configuration) {
        { language ->
            if (language != selected) {
                selected = language
                CameraPermissionSupport.findActivity(context)?.let { AppLocales.setLanguage(it, language) }
            }
        }
    }
    WelcomeScreen(
        selected = selected,
        deviceLocale = deviceLocale,
        onLanguageSelected = onLanguageSelected,
        onContinue = onContinue,
        modifier = modifier,
    )
}

@Composable
fun WelcomeScreen(
    selected: AppLanguage?,
    deviceLocale: Locale,
    onLanguageSelected: (AppLanguage?) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Hoisted here so the query survives one-/two-pane switches, but only LanguagePicker reads
    // its value: typing recomposes the list, not the logo, header or layout.
    val query = rememberSaveable { mutableStateOf("") }
    val uiLocale: Locale = LocalConfiguration.current.locales[0]
    // Locale display names are ICU lookups: resolve all of them once per UI language.
    val entries = remember(uiLocale) { languageEntries(uiLocale) }
    val deviceLanguageName = remember(deviceLocale) {
        AppLanguage.from(deviceLocale)?.nativeName ?: deviceLocale.getDisplayName(deviceLocale)
    }

    Surface(
        modifier = modifier.fillMaxSize().testTag("welcome_screen"),
        color = MaterialTheme.colorScheme.background,
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.TopCenter,
        ) {
            // Side by side once the window is wide (tablet, unfolded) or short (phone landscape).
            val twoPane = maxWidth >= 600.dp || (maxHeight < 520.dp && maxWidth > maxHeight)
            val inset = if (maxWidth < 360.dp) 16.dp else 24.dp
            val compactHeader = maxHeight < 700.dp
            // Small phones: drop the feature chips so the list keeps several visible rows.
            val tinyHeader = maxHeight < 640.dp
            val veryShort = maxHeight < 480.dp
            val picker: @Composable (Modifier) -> Unit = { pickerModifier ->
                LanguagePicker(
                    queryState = query,
                    entries = entries,
                    selected = selected,
                    deviceLanguageName = deviceLanguageName,
                    onLanguageSelected = onLanguageSelected,
                    modifier = pickerModifier,
                )
            }

            if (twoPane) {
                Row(
                    modifier = Modifier
                        .widthIn(max = 1040.dp)
                        .fillMaxSize()
                        .padding(horizontal = inset, vertical = 16.dp)
                        .testTag("welcome_layout_two_pane"),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    // Summary and Continue on the left, so the list gets the full height on the right.
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            WelcomeHeader(compact = veryShort, showChips = true, centered = false)
                        }
                        ContinueButton(onContinue, Modifier.padding(top = 12.dp).widthIn(max = 420.dp))
                    }
                    picker(Modifier.weight(1f).widthIn(max = 480.dp).fillMaxHeight().imePadding())
                }
            } else {
                Column(
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxSize()
                        .padding(horizontal = inset)
                        .padding(top = if (compactHeader) 16.dp else 32.dp, bottom = 16.dp)
                        .imePadding()
                        .testTag("welcome_layout_single_pane"),
                ) {
                    WelcomeHeader(compact = compactHeader, showChips = !tinyHeader, centered = true)
                    picker(Modifier.weight(1f).padding(top = if (compactHeader) 16.dp else 24.dp))
                    ContinueButton(onContinue, Modifier.padding(top = 16.dp))
                }
            }
        }
    }
}

/** The adaptive launcher icon's artwork, cropped to its square so it reads as the app icon. */
@Composable
fun ScanlyLogo(size: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size * 0.28f)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            // Keeps the black tile's edge visible on dark and pure black backgrounds.
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            // The foreground keeps a transparent safe zone around a black square; zoom past it.
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = LOGO_CROP_SCALE
                scaleY = LOGO_CROP_SCALE
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WelcomeHeader(compact: Boolean, showChips: Boolean, centered: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    val align = if (centered) Alignment.CenterHorizontally else Alignment.Start
    val textAlign = if (centered) TextAlign.Center else TextAlign.Start
    val appName = stringResource(R.string.app_name)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align,
    ) {
        ScanlyLogo(
            size = if (!showChips) 48.dp else if (compact) 56.dp else 72.dp,
            modifier = Modifier.testTag("welcome_logo"),
        )
        Text(
            text = buildAnnotatedString {
                append(appName)
                withStyle(SpanStyle(color = accent)) { append(".") }
            },
            fontSize = if (compact) 32.sp else 40.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.8).sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .padding(top = if (compact) 10.dp else 16.dp)
                .semantics { heading() },
        )
        Text(
            text = stringResource(R.string.welcome_greeting),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            textAlign = textAlign,
            modifier = Modifier.padding(top = 2.dp).testTag("welcome_greeting"),
        )
        Text(
            text = stringResource(R.string.welcome_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = textAlign,
            modifier = Modifier.padding(top = if (compact) 6.dp else 10.dp).widthIn(max = 420.dp),
        )
        if (showChips) {
            FlowRow(
                modifier = Modifier.padding(top = if (compact) 10.dp else 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, align),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FeatureChip(Icons.Outlined.CropFree, stringResource(R.string.onboarding_page_capture))
                FeatureChip(Icons.Outlined.Search, stringResource(R.string.onboarding_page_find))
                FeatureChip(Icons.Outlined.TaskAlt, stringResource(R.string.onboarding_page_share))
            }
        }
        if (!compact) {
            Row(
                modifier = Modifier.padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(R.string.onboarding_assurance_offline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FeatureChip(icon: ImageVector, label: String) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .heightIn(min = 32.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun LanguagePicker(
    queryState: MutableState<String>,
    entries: List<LanguageEntry>,
    selected: AppLanguage?,
    deviceLanguageName: String,
    onLanguageSelected: (AppLanguage?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by queryState
    val onQueryChange: (String) -> Unit = { query = it }
    val trimmed = query.trim()
    // Plain string checks over precomputed names: cheap enough to run on every keystroke.
    val languages = remember(trimmed, entries) { entries.filter { it.matches(trimmed) } }
    val focusManager = LocalFocusManager.current
    val title = stringResource(R.string.settings_language_app_language)
    val listState = rememberLazyListState()
    // New results start at the top (clearing the search brings "Phone language" back into view).
    LaunchedEffect(trimmed) { listState.scrollToItem(0) }
    val onPick: (AppLanguage?) -> Unit = remember(focusManager, onLanguageSelected) {
        { language -> focusManager.clearFocus(); onLanguageSelected(language) }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(top = 16.dp, start = 12.dp, end = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 10.dp).semantics { heading() },
        )
        // BasicTextField, not material3 TextField: the latter's indicator animation reads
        // composition locals after detach and crashes when rotation swaps one/two-pane layouts.
        val textColor = MaterialTheme.colorScheme.onSurface
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = title }
                .testTag("welcome_search"),
            decorationBox = { field ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(modifier = Modifier.weight(1f).padding(vertical = 14.dp)) {
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(R.string.welcome_search_hint),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        field()
                    }
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }, modifier = Modifier.testTag("welcome_search_clear")) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.group_detail_clear))
                        }
                    }
                }
            },
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f).selectableGroup().testTag("welcome_language_list"),
            contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp),
        ) {
            if (query.isBlank()) {
                item(key = "phone") {
                    LanguageRow(
                        title = stringResource(R.string.settings_language_follow_phone),
                        subtitle = stringResource(R.string.settings_language_uses_phone_language, deviceLanguageName),
                        badge = null,
                        selected = selected == null,
                        onClick = { onPick(null) },
                        modifier = Modifier.testTag("welcome_phone_language"),
                    )
                }
                item(key = "divider") {
                    Spacer(
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth().height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
            languageItems(languages, selected, onPick)
            if (languages.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.welcome_no_language_match, trimmed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 20.dp).testTag("welcome_no_match"),
                    )
                }
            }
        }
    }
}

private fun LazyListScope.languageItems(
    entries: List<LanguageEntry>,
    selected: AppLanguage?,
    onClick: (AppLanguage?) -> Unit,
) {
    items(entries, key = { it.language.tag }, contentType = { "language" }) { entry ->
        LanguageRow(
            title = entry.language.nativeName,
            subtitle = entry.subtitle,
            badge = entry.badge,
            selected = selected == entry.language,
            onClick = { onClick(entry.language) },
            modifier = Modifier.testTag("welcome_language_${entry.language.tag}"),
        )
    }
}

/** A language with every name the picker shows or searches, resolved once per UI locale. */
@Immutable
internal class LanguageEntry(
    val language: AppLanguage,
    private val localName: String,
    private val englishName: String,
) {
    /** The name in the current UI language, unless it just repeats the native name. */
    val subtitle: String? = localName.takeUnless { it.equals(language.nativeName, ignoreCase = true) }
    val badge: String = language.tag.take(2).uppercase(Locale.ROOT)

    /** Native name, current-language name, English name or tag prefix; blank matches everything. */
    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim()
        return language.nativeName.contains(q, ignoreCase = true) ||
            localName.contains(q, ignoreCase = true) ||
            englishName.contains(q, ignoreCase = true) ||
            language.tag.startsWith(q, ignoreCase = true)
    }
}

internal fun languageEntries(uiLocale: Locale): List<LanguageEntry> =
    AppLanguage.entries.map { language ->
        LanguageEntry(
            language = language,
            localName = language.displayName(uiLocale),
            englishName = language.displayName(Locale.ENGLISH),
        )
    }

@Composable
private fun LanguageRow(
    title: String,
    subtitle: String?,
    badge: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = tween(180),
        label = "welcome_language_row",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .heightIn(min = 56.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (badge == null) {
                Icon(
                    Icons.Outlined.Smartphone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ContinueButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("welcome_continue"),
    ) {
        Text(
            stringResource(R.string.onboarding_continue),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.size(8.dp))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}

/** The launcher foreground's black square spans ~75% of its canvas. */
private const val LOGO_CROP_SCALE = 1.36f
