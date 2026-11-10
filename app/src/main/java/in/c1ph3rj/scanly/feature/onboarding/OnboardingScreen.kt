package `in`.c1ph3rj.scanly.feature.onboarding

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.core.ui.ScanlyTestTags
import `in`.c1ph3rj.scanly.feature.pip.LocalPipPreferences
import `in`.c1ph3rj.scanly.feature.pip.Pip
import `in`.c1ph3rj.scanly.feature.pip.PipLook
import `in`.c1ph3rj.scanly.feature.pip.PipMood
import `in`.c1ph3rj.scanly.feature.pip.pipMotionEnabled
import `in`.c1ph3rj.scanly.ui.theme.ScanlyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

private enum class OnboardingPage(
    @param:StringRes val chapter: Int,
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    @param:DrawableRes val art: Int,
    val mood: PipMood,
) {
    CAPTURE(R.string.onboarding_page_capture, R.string.onboarding_page_paper_to_polished,
        R.string.onboarding_page_capture_straighten_and_clean_up, R.drawable.atelier_capture, PipMood.IDLE),
    FIND(R.string.onboarding_page_find, R.string.onboarding_page_find_more_than_files,
        R.string.onboarding_page_recognize_text_on_your_device, R.drawable.atelier_find, PipMood.WOW),
    SHARE(R.string.onboarding_page_share, R.string.onboarding_page_ready_when_you_are,
        R.string.onboarding_page_keep_pages_together_share_a, R.drawable.atelier_finish, PipMood.HAPPY),
}

internal enum class OnboardingLayoutMode { COMPACT, MEDIUM, WIDE }

internal fun resolveOnboardingLayoutMode(widthDp: Float, heightDp: Float): OnboardingLayoutMode = when {
    widthDp < 600f -> OnboardingLayoutMode.COMPACT
    widthDp < 840f && heightDp >= widthDp -> OnboardingLayoutMode.MEDIUM
    else -> OnboardingLayoutMode.WIDE
}

/** Local palette: the editorial paper identity does not change the rest of Scanly's theme. */
@Composable
private fun atelierColors(): ColorScheme {
    val base = MaterialTheme.colorScheme
    return if (base.background.luminance() < 0.5f) base.copy(
        primary = Color(0xFF80D4C2), onPrimary = Color(0xFF103C33),
        secondaryContainer = Color(0xFF38432A), onSecondaryContainer = Color(0xFFE6F39D),
    ) else base.copy(
        background = Color(0xFFF6F3EB), surface = Color(0xFFF6F3EB),
        onBackground = Color(0xFF153B32), onSurface = Color(0xFF153B32),
        onSurfaceVariant = Color(0xFF46574F), primary = Color(0xFF0F766E), onPrimary = Color.White,
        surfaceContainer = Color(0xFFECE8DC), outlineVariant = Color(0xFFD8D6C9),
        secondaryContainer = Color(0xFFE9F19A), onSecondaryContainer = Color(0xFF153B32),
    )
}

@Composable
fun OnboardingScreen(
    uiState: OnboardingUiState,
    onComplete: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
    interactionEnabled: Boolean = true,
) {
    MaterialTheme(colorScheme = atelierColors()) {
        AtelierOnboarding(uiState, onComplete, onDismissError, modifier, interactionEnabled)
    }
}

@Composable
private fun AtelierOnboarding(
    uiState: OnboardingUiState, onComplete: () -> Unit, onDismissError: () -> Unit,
    modifier: Modifier, interactionEnabled: Boolean,
) {
    val pager = rememberPagerState { OnboardingPage.entries.size }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val motion = pipMotionEnabled()
    val enabled = interactionEnabled && uiState.status == OnboardingStatus.REQUIRED && !uiState.isCompleting
    val pageIndex = pager.settledPage
    val navEnabled = enabled && !pager.isScrollInProgress
    val goTo: (Int) -> Unit = { target ->
        if (navEnabled) scope.launch {
            if (motion) pager.animateScrollToPage(target, animationSpec = tween(420, easing = FastOutSlowInEasing))
            else pager.scrollToPage(target)
        }
    }
    // Decode every modest-sized asset off the UI thread, before a swipe can reveal it.
    val resources = LocalContext.current.resources
    val artwork by produceState<Map<Int, ImageBitmap>>(emptyMap(), resources) {
        value = withContext(Dispatchers.IO) {
            OnboardingPage.entries.associate { page ->
                page.art to BitmapFactory.decodeResource(resources, page.art,
                    BitmapFactory.Options().apply { inScaled = false })
                    .apply { prepareToDraw() }.asImageBitmap()
            }
        }
    }
    BackHandler(enabled = enabled && pageIndex > 0) { goTo(pageIndex - 1) }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { snackbar.showSnackbar(it); onDismissError() }
    }
    Scaffold(
        modifier = modifier.testTag("onboarding_screen"),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        BoxWithConstraints(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            val mode = resolveOnboardingLayoutMode(maxWidth.value, maxHeight.value)
            val short = maxHeight < 480.dp
            val inset = if (maxWidth < 360.dp) 20.dp else 24.dp
            Column(
                Modifier.widthIn(max = 1120.dp).fillMaxSize()
                    .testTag("onboarding_layout_" + mode.name.lowercase()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = inset).heightIn(min = if (short) 48.dp else 64.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.onboarding_scanly), fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Black, fontSize = 26.sp, letterSpacing = (-1).sp)
                    Spacer(Modifier.weight(1f))
                    if (pageIndex < 2) TextButton(
                        onClick = { goTo(2) }, enabled = navEnabled,
                        modifier = Modifier.testTag("onboarding_skip"),
                    ) {
                        Text(stringResource(R.string.onboarding_skip), fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                val pagerDescription = stringResource(R.string.onboarding_page_of_3_2, pageIndex + 1)
                HorizontalPager(
                    state = pager, userScrollEnabled = enabled, beyondViewportPageCount = 1,
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("onboarding_pager")
                        .semantics { stateDescription = pagerDescription },
                ) { index ->
                    StoryPage(OnboardingPage.entries[index], pager, index, mode, inset,
                        artwork[OnboardingPage.entries[index].art], motion)
                }
                Column(
                    Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = inset)
                        .padding(top = if (short) 0.dp else 4.dp, bottom = if (short) 8.dp else 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PageIndicator(pageIndex, enabled, goTo)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        AnimatedVisibility(
                            visible = pageIndex > 0,
                            enter = fadeIn(tween(180)) + expandHorizontally(tween(240)),
                            exit = fadeOut(tween(140)) + shrinkHorizontally(tween(240)),
                        ) {
                            FilledTonalIconButton(
                                onClick = { goTo(pageIndex - 1) }, enabled = navEnabled,
                                modifier = Modifier.size(56.dp).testTag("onboarding_back"),
                                shape = RoundedCornerShape(12.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    contentColor = MaterialTheme.colorScheme.onSurface),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack,
                                    stringResource(R.string.onboarding_previous_page))
                            }
                        }
                        Button(
                            onClick = { if (pageIndex == 2) onComplete() else goTo(pageIndex + 1) },
                            enabled = navEnabled,
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp)
                                .testTag(if (pageIndex == 2) ScanlyTestTags.ONBOARDING_GET_STARTED else "onboarding_continue"),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                            colors = ButtonDefaults.buttonColors(
                                disabledContainerColor = MaterialTheme.colorScheme.primary,
                                disabledContentColor = MaterialTheme.colorScheme.onPrimary),
                        ) {
                            if (uiState.isCompleting) CircularProgressIndicator(Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            else {
                                Text(stringResource(if (pageIndex == 2) R.string.onboarding_get_started
                                    else R.string.onboarding_continue), fontSize = 18.sp,
                                    lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
                                if (pageIndex < 2) {
                                    Spacer(Modifier.width(12.dp))
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageIndicator(pageIndex: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.Center) {
        OnboardingPage.entries.forEachIndexed { index, page ->
            val label = stringResource(R.string.onboarding_page_of_3, stringResource(page.chapter), index + 1)
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            val pressed by interaction.collectIsPressedAsState()
            Box(
                Modifier.size(48.dp).clip(CircleShape)
                    .selectable(selected = pageIndex == index, enabled = enabled,
                    interactionSource = interaction, indication = null,
                    role = Role.Tab, onClick = { onSelect(index) })
                    .semantics { contentDescription = label }.testTag("onboarding_dot_" + index),
                contentAlignment = Alignment.Center,
            ) {
                // Keep the 48dp target, but draw focus/press feedback only around the dot.
                Box(Modifier.size(26.dp).clip(CircleShape)
                    .background(if (pressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                    .border(if (focused) 1.5.dp else 0.dp,
                        if (focused) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape))
                Box(Modifier.size(8.dp).clip(CircleShape).background(
                    if (pageIndex == index) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)))
            }
        }
    }
}

@Composable
private fun StoryPage(
    page: OnboardingPage, pager: PagerState, index: Int, mode: OnboardingLayoutMode,
    inset: Dp, bitmap: ImageBitmap?, motion: Boolean,
) {
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.TopCenter) {
        val viewport = maxHeight
        val pageWidth = minOf(maxWidth, 520.dp)
        val artMotion = Modifier.graphicsLayer {
            // Layer-only work during a drag: no decoding or measuring on each frame.
            val distance = if (motion) ((pager.currentPage - index) + pager.currentPageOffsetFraction)
                .coerceIn(-1f, 1f) else 0f
            alpha = 1f - 0.12f * distance.absoluteValue
            translationX = distance * size.width * 0.035f
        }
        if (mode == OnboardingLayoutMode.WIDE) {
            Row(Modifier.fillMaxSize().padding(horizontal = inset),
                horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(0.9f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center) {
                    StoryCopy(page, index, compact = viewport < 340.dp)
                    if (page != OnboardingPage.FIND) Assurance(page, Modifier.padding(top = 16.dp))
                }
                StoryArt(page, bitmap, (viewport - 16.dp).coerceAtLeast(140.dp),
                    active = pager.settledPage == index && !pager.isScrollInProgress,
                    fitImage = true,
                    modifier = Modifier.weight(1.1f).then(artMotion))
            }
        } else {
            Column(
                Modifier.widthIn(max = 520.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                    .heightIn(min = viewport).padding(top = 12.dp, bottom = 8.dp),
                verticalArrangement = if (mode == OnboardingLayoutMode.MEDIUM) Arrangement.Center else Arrangement.Top,
            ) {
                StoryCopy(page, index, compact = pageWidth < 360.dp, modifier = Modifier.padding(horizontal = inset))
                Spacer(Modifier.height(20.dp))
                StoryArt(page, bitmap, minOf(pageWidth * 1.05f, viewport * 0.62f).coerceIn(200.dp, 420.dp),
                    active = pager.settledPage == index && !pager.isScrollInProgress,
                    modifier = Modifier.fillMaxWidth().then(artMotion))
                if (page != OnboardingPage.FIND) Assurance(page, Modifier.padding(horizontal = inset, vertical = 12.dp))
            }
        }
    }
}

@Composable
private fun StoryCopy(page: OnboardingPage, index: Int, compact: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.onboarding_chapter_number, index + 1, stringResource(page.chapter)),
            modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 13.sp,
            lineHeight = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp)
        Text(stringResource(page.title), fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black,
            fontSize = if (compact) 34.sp else 40.sp, lineHeight = if (compact) 37.sp else 43.sp,
            letterSpacing = (-1.3).sp, modifier = Modifier.semantics { heading() })
        Text(stringResource(page.description), fontSize = 17.sp, lineHeight = 24.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StoryArt(
    page: OnboardingPage, bitmap: ImageBitmap?, height: Dp, active: Boolean,
    modifier: Modifier = Modifier,
    fitImage: Boolean = false,
) {
    val reveal by animateFloatAsState(if (bitmap != null) 1f else 0f,
        animationSpec = tween(if (pipMotionEnabled()) 180 else 0), label = "paper_reveal")
    if (page == OnboardingPage.FIND) {
        // A single preview owns the query, image and result. Its natural image ratio avoids
        // stretching the paper or exposing contrasting strips between separate components.
        val previewModifier = if (fitImage) modifier.heightIn(max = height)
            .verticalScroll(rememberScrollState()) else modifier
        SearchPreview(bitmap, reveal, active,
            previewModifier.padding(horizontal = if (fitImage) 0.dp else 24.dp))
        return
    }
    Box(modifier.height(height).testTag("onboarding_art_" + page.name.lowercase())) {
        if (bitmap != null) Image(bitmap, contentDescription = null,
            contentScale = if (fitImage) ContentScale.Fit else ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = reveal }.testTag("onboarding_paper"))
        if (page == OnboardingPage.CAPTURE) Box(Modifier.align(Alignment.Center)
            .fillMaxHeight().width(1.dp).background(Color(0xFFF6F3EB).copy(alpha = 0.8f)))
        if (page == OnboardingPage.CAPTURE) Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 72.dp, end = 24.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SampleLabel(stringResource(R.string.filter_customize_original))
            SampleLabel(stringResource(R.string.filter_customize_magic_color))
        }
        PaperPip(page.mood, active, PipLook.RIGHT,
            Modifier.align(Alignment.BottomStart).padding(start = 12.dp,
                bottom = if (page == OnboardingPage.SHARE) 76.dp else 24.dp))
        if (page == OnboardingPage.SHARE) Surface(
            Modifier.align(Alignment.BottomCenter).padding(start = 24.dp, end = 24.dp, bottom = 12.dp).fillMaxWidth(),
            shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface,
        ) {
            FilePreviewLabel()
        }
    }
}

@Composable
private fun SearchPreview(bitmap: ImageBitmap?, reveal: Float, active: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier.fillMaxWidth().testTag("onboarding_search_preview"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Column(Modifier.testTag("onboarding_art_find")) {
            Row(
                Modifier.padding(14.dp).fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(50))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.onboarding_demo_query), fontSize = 16.sp, lineHeight = 22.sp)
            }
            Box(Modifier.fillMaxWidth().aspectRatio(10f / 7f).clipToBounds()) {
                if (bitmap != null) Image(bitmap, contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = reveal }.testTag("onboarding_paper"))
                // The empty corner of this sample keeps Pip clear of the highlighted paragraph.
                PaperPip(PipMood.WOW, active, PipLook.LEFT,
                    Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 8.dp))
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.onboarding_demo_result,
                    stringResource(R.string.onboarding_demo_document), 2),
                    fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.onboarding_demo_found), fontSize = 12.sp, lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PaperPip(mood: PipMood, active: Boolean, look: PipLook, modifier: Modifier = Modifier) {
    val preferences = LocalPipPreferences.current
    if (preferences.enabled) CompositionLocalProvider(
        LocalPipPreferences provides preferences.copy(motion = preferences.motion && active),
    ) {
        Pip(mood, look = look, size = 48.dp, modifier = modifier)
    }
}

@Composable
private fun FilePreviewLabel() {
    val largeText = LocalDensity.current.fontScale > 1.3f
    val filename = stringResource(R.string.onboarding_demo_filename)
    val pageCount = pluralStringResource(R.plurals.common_pages, 3, 3)
    val protectedLabel = stringResource(R.string.onboarding_demo_protected)
    if (largeText) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(filename, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(pageCount, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Lock, null, Modifier.size(18.dp))
                Text(protectedLabel, fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
    } else {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(filename, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(pageCount, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.Lock, null, Modifier.size(18.dp))
            Text(protectedLabel, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.widthIn(max = 86.dp))
        }
    }
}

@Composable
private fun SampleLabel(text: String) {
    Text(text, fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp))
}

@Composable
private fun Assurance(page: OnboardingPage, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = 28.dp), horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Smartphone, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Text(stringResource(if (page == OnboardingPage.SHARE) R.string.onboarding_no_account_no_cloud_upload
            else R.string.onboarding_assurance_offline), fontSize = 13.sp, lineHeight = 19.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(showBackground = true, widthDp = 320, heightDp = 568)
@Preview(showBackground = true, widthDp = 800, heightDp = 1280)
@Preview(showBackground = true, widthDp = 900, heightDp = 420)
@Composable
private fun OnboardingScreenPreview() {
    ScanlyTheme { OnboardingScreen(OnboardingUiState(status = OnboardingStatus.REQUIRED), {}, {}) }
}
