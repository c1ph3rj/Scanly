package `in`.c1ph3rj.scanly.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.core.ui.ScanlyTestTags
import `in`.c1ph3rj.scanly.feature.pip.pipMotionEnabled
import `in`.c1ph3rj.scanly.ui.theme.ScanlyTheme
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private enum class OnboardingPage(
    @param:StringRes val chapter: Int,
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    /** A short privacy assurance where the page needs one. */
    @param:StringRes val assurance: Int?,
) {
    CAPTURE(R.string.onboarding_page_capture, R.string.onboarding_page_paper_to_polished,
        R.string.onboarding_page_capture_straighten_and_clean_up, R.string.onboarding_assurance_offline),
    FIND(R.string.onboarding_page_find, R.string.onboarding_page_find_more_than_files,
        R.string.onboarding_page_recognize_text_on_your_device, null),
    SHARE(R.string.onboarding_page_share, R.string.onboarding_page_ready_when_you_are,
        R.string.onboarding_page_keep_pages_together_share_a, R.string.onboarding_no_account_no_cloud_upload),
}

internal enum class OnboardingLayoutMode { COMPACT, MEDIUM, WIDE }

internal fun resolveOnboardingLayoutMode(widthDp: Float, heightDp: Float): OnboardingLayoutMode = when {
    widthDp < 600f -> OnboardingLayoutMode.COMPACT
    widthDp < 840f && heightDp >= widthDp -> OnboardingLayoutMode.MEDIUM
    else -> OnboardingLayoutMode.WIDE
}

/** Fades and lifts an element in during its [from]..[to] slice of the opening sequence. */
private fun Modifier.entrance(intro: Animatable<Float, *>, from: Float, to: Float, rise: Dp = 22.dp) = graphicsLayer {
    val t = EmphasizedEasing.transform(((intro.value - from) / (to - from)).coerceIn(0f, 1f))
    alpha = t
    translationY = (1f - t) * rise.toPx()
}

/**
 * First-run introduction: three pages that each *show* one outcome with a short animated scene
 * (see OnboardingDemos.kt) in the app's own theme. Paging is by swipe, Continue, Back or the
 * progress pills; scenes never block navigation and settle on their end state with motion off.
 */
@Composable
fun OnboardingScreen(
    uiState: OnboardingUiState,
    onComplete: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
    interactionEnabled: Boolean = true,
) {
    val pager = rememberPagerState { OnboardingPage.entries.size }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val motion = pipMotionEnabled()
    val enabled = interactionEnabled && uiState.status == OnboardingStatus.REQUIRED && !uiState.isCompleting
    val pageIndex = pager.settledPage
    val lastIndex = OnboardingPage.entries.lastIndex
    val navEnabled = enabled && !pager.isScrollInProgress
    val goTo: (Int) -> Unit = { target ->
        if (navEnabled) scope.launch {
            if (motion) pager.animateScrollToPage(target, animationSpec = tween(520, easing = EmphasizedEasing))
            else pager.scrollToPage(target)
        }
    }
    // One opening sequence: header, scene, copy and controls arrive in turn.
    val intro = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(motion) {
        if (motion) intro.animateTo(1f, tween(900, easing = LinearEasing)) else intro.snapTo(1f)
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
                    Modifier.fillMaxWidth().padding(horizontal = inset)
                        .heightIn(min = if (short) 48.dp else 64.dp).entrance(intro, 0f, 0.4f, rise = 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.onboarding_scanly), fontWeight = FontWeight.Bold,
                        fontSize = 22.sp, letterSpacing = (-0.6).sp, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.weight(1f))
                    AnimatedVisibility(pageIndex < lastIndex, enter = fadeIn(tween(200)), exit = fadeOut(tween(160))) {
                        TextButton(
                            onClick = { goTo(lastIndex) }, enabled = navEnabled,
                            modifier = Modifier.testTag("onboarding_skip"),
                        ) {
                            Text(stringResource(R.string.onboarding_skip), fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                val pagerDescription = stringResource(R.string.onboarding_page_of_3_2, pageIndex + 1)
                HorizontalPager(
                    state = pager, userScrollEnabled = enabled, beyondViewportPageCount = 1,
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("onboarding_pager")
                        .semantics { stateDescription = pagerDescription },
                ) { index ->
                    StoryPage(OnboardingPage.entries[index], pager, index, mode, inset, motion, intro)
                }
                Column(
                    Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = inset)
                        .padding(top = if (short) 0.dp else 4.dp, bottom = if (short) 8.dp else 24.dp)
                        .entrance(intro, 0.45f, 1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PageIndicator(pager, pageIndex, enabled, goTo)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        AnimatedVisibility(
                            visible = pageIndex > 0,
                            enter = fadeIn(tween(200)) + expandHorizontally(tween(320, easing = EmphasizedEasing)),
                            exit = fadeOut(tween(140)) + shrinkHorizontally(tween(320, easing = EmphasizedEasing)),
                        ) {
                            FilledTonalIconButton(
                                onClick = { goTo(pageIndex - 1) }, enabled = navEnabled,
                                modifier = Modifier.padding(end = 12.dp).size(56.dp).testTag("onboarding_back"),
                                shape = RoundedCornerShape(18.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    disabledContentColor = MaterialTheme.colorScheme.onSurface),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack,
                                    stringResource(R.string.onboarding_previous_page))
                            }
                        }
                        PrimaryAction(
                            last = pageIndex == lastIndex, completing = uiState.isCompleting, enabled = navEnabled,
                            onClick = { if (pageIndex == lastIndex) onComplete() else goTo(pageIndex + 1) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrimaryAction(
    last: Boolean, completing: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.97f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "cta_press",
    )
    Button(
        onClick = onClick, enabled = enabled, interactionSource = interaction,
        modifier = modifier.heightIn(min = 56.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .testTag(if (last) ScanlyTestTags.ONBOARDING_GET_STARTED else "onboarding_continue"),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        // Paging briefly disables the button; it must not flicker grey while it does.
        colors = ButtonDefaults.buttonColors(
            disabledContainerColor = MaterialTheme.colorScheme.primary,
            disabledContentColor = MaterialTheme.colorScheme.onPrimary),
    ) {
        if (completing) CircularProgressIndicator(Modifier.size(22.dp),
            color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
        else AnimatedContent(
            targetState = last,
            transitionSpec = {
                (fadeIn(tween(220, delayMillis = 60)) + slideInVertically(tween(280, easing = EmphasizedEasing)) { it / 2 })
                    .togetherWith(fadeOut(tween(120)) + slideOutVertically(tween(200)) { -it / 2 })
            },
            contentAlignment = Alignment.Center,
            label = "cta_label",
        ) { isLast ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(if (isLast) R.string.onboarding_get_started else R.string.onboarding_continue),
                    fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
                if (!isLast) {
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(20.dp))
                }
            }
        }
    }
}

/** Progress pills: the current one stretches, and the stretch follows the finger between pages. */
@Composable
private fun PageIndicator(pager: PagerState, pageIndex: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    val active = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.Center) {
        OnboardingPage.entries.forEachIndexed { index, page ->
            val label = stringResource(R.string.onboarding_page_of_3, stringResource(page.chapter), index + 1)
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            val pressed by interaction.collectIsPressedAsState()
            Box(
                Modifier.size(width = 40.dp, height = 48.dp).clip(CircleShape)
                    .selectable(selected = pageIndex == index, enabled = enabled,
                        interactionSource = interaction, indication = null,
                        role = Role.Tab, onClick = { onSelect(index) })
                    .semantics { contentDescription = label }.testTag("onboarding_dot_" + index),
                contentAlignment = Alignment.Center,
            ) {
                // Focus and press feedback hug the pill instead of filling the touch target.
                Box(Modifier.size(width = 36.dp, height = 24.dp).clip(CircleShape)
                    .background(if (pressed) active.copy(alpha = 0.12f) else Color.Transparent)
                    .border(if (focused) 1.5.dp else 0.dp, if (focused) active else Color.Transparent, CircleShape))
                Canvas(Modifier.size(width = 26.dp, height = 8.dp)) {
                    val position = pager.currentPage + pager.currentPageOffsetFraction
                    val t = (1f - (position - index).absoluteValue).coerceIn(0f, 1f)
                    val w = lerp(size.height, size.width, t)
                    drawRoundRect(lerp(idle, active, t), Offset((size.width - w) / 2f, 0f),
                        Size(w, size.height), CornerRadius(size.height / 2f))
                }
            }
        }
    }
}

@Composable
private fun StoryPage(
    page: OnboardingPage, pager: PagerState, index: Int, mode: OnboardingLayoutMode,
    inset: Dp, motion: Boolean, intro: Animatable<Float, *>,
) {
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.TopCenter) {
        val viewport = maxHeight
        val pageWidth = minOf(maxWidth, 520.dp)
        val settled = pager.settledPage == index
        // Signed distance from the centre of the screen in pages: layer-only work during a drag.
        val distance = {
            if (motion) ((pager.currentPage - index) + pager.currentPageOffsetFraction).coerceIn(-1f, 1f) else 0f
        }
        // The scene sinks back a little as it leaves; the words trail behind it at their own pace.
        val sceneMotion = Modifier.graphicsLayer {
            val d = distance().absoluteValue
            alpha = 1f - 0.35f * d
            scaleX = 1f - 0.06f * d; scaleY = 1f - 0.06f * d
        }.entrance(intro, 0.1f, 0.65f, rise = 30.dp)
        val drift: (Dp) -> Modifier = { amount ->
            Modifier.graphicsLayer {
                val d = distance()
                translationX = -d * amount.toPx()
                alpha = 1f - d.absoluteValue
            }
        }
        if (mode == OnboardingLayoutMode.WIDE) {
            Row(Modifier.fillMaxSize().padding(horizontal = inset),
                horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(0.9f).fillMaxHeight().verticalScroll(rememberScrollState())
                    .entrance(intro, 0.25f, 0.8f), verticalArrangement = Arrangement.Center) {
                    StoryCopy(page, index, compact = viewport < 340.dp, drift = drift)
                }
                Column(Modifier.weight(1.1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center) {
                    Scene(page, settled, motion, (viewport - 16.dp).coerceAtLeast(150.dp), sceneMotion)
                }
            }
        } else {
            Column(
                Modifier.widthIn(max = 520.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                    .heightIn(min = viewport).padding(top = if (viewport >= 560.dp) 56.dp else 4.dp, bottom = 8.dp),
                verticalArrangement = if (mode == OnboardingLayoutMode.MEDIUM) Arrangement.Center else Arrangement.Top,
            ) {
                Scene(page, settled, motion, minOf((pageWidth - (inset + 8.dp) * 2) / 1.2f, viewport * 0.52f).coerceIn(160.dp, 400.dp),
                    Modifier.padding(horizontal = inset + 8.dp).then(sceneMotion))
                Spacer(Modifier.height(28.dp))
                StoryCopy(page, index, compact = pageWidth < 360.dp, drift = drift,
                    modifier = Modifier.padding(horizontal = inset).entrance(intro, 0.25f, 0.8f))
            }
        }
    }
}

@Composable
private fun Scene(page: OnboardingPage, settled: Boolean, motion: Boolean, height: Dp, modifier: Modifier) {
    when (page) {
        OnboardingPage.CAPTURE -> CaptureScene(settled, motion, height, modifier.fillMaxWidth())
        OnboardingPage.FIND -> FindScene(settled, motion, height, modifier)
        OnboardingPage.SHARE -> FinishScene(settled, motion, height, modifier.fillMaxWidth())
    }
}

@Composable
private fun StoryCopy(
    page: OnboardingPage, index: Int, compact: Boolean, drift: (Dp) -> Modifier, modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(stringResource(R.string.onboarding_chapter_number, index + 1, stringResource(page.chapter)),
            modifier = drift(24.dp), color = MaterialTheme.colorScheme.primary, fontSize = 14.sp,
            lineHeight = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp)
        Spacer(Modifier.height(10.dp))
        Text(stringResource(page.title), fontWeight = FontWeight.Bold,
            fontSize = if (compact) 30.sp else 36.sp, lineHeight = if (compact) 34.sp else 40.sp,
            letterSpacing = (-1).sp, color = MaterialTheme.colorScheme.onBackground,
            modifier = drift(56.dp).semantics { heading() })
        Spacer(Modifier.height(12.dp))
        Text(stringResource(page.description), fontSize = 17.sp, lineHeight = 24.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = drift(96.dp))
        page.assurance?.let { Assurance(it, drift(128.dp).padding(top = 16.dp)) }
    }
}

@Composable
private fun Assurance(@StringRes text: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.heightIn(min = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (text == R.string.onboarding_no_account_no_cloud_upload) Icons.Outlined.Lock else Icons.Outlined.Smartphone,
            null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(text), fontSize = 14.sp, lineHeight = 20.sp,
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
