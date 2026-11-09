package `in`.c1ph3rj.scanly.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
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
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.core.ui.ScanlyTestTags
import `in`.c1ph3rj.scanly.feature.pip.Pip
import `in`.c1ph3rj.scanly.feature.pip.PipLook
import `in`.c1ph3rj.scanly.feature.pip.PipMood
import `in`.c1ph3rj.scanly.ui.theme.ScanlyTheme
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import `in`.c1ph3rj.scanly.core.common.appString

private class OnboardingFeature(val icon: ImageVector, @StringRes val label: Int)

private enum class OnboardingPage(
    @param:StringRes val chapterRes: Int,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    @DrawableRes val illustration: Int,
    val mood: PipMood,
    val features: List<OnboardingFeature>,
    val assuranceIcon: ImageVector,
    @StringRes val assurance: Int,
) {
    CAPTURE(
        R.string.onboarding_page_capture, R.string.onboarding_page_paper_to_polished,
        R.string.onboarding_page_capture_straighten_and_clean_up, R.drawable.onboarding_capture,
        PipMood.IDLE,
        listOf(
            OnboardingFeature(Icons.Outlined.DocumentScanner, R.string.onboarding_feature_edge_detection),
            OnboardingFeature(Icons.Outlined.Crop, R.string.onboarding_feature_crop_straighten),
            OnboardingFeature(Icons.Outlined.AutoAwesome, R.string.onboarding_feature_filters),
        ),
        Icons.Outlined.WifiOff, R.string.onboarding_assurance_offline,
    ),
    FIND(
        R.string.onboarding_page_find, R.string.onboarding_page_find_more_than_files,
        R.string.onboarding_page_recognize_text_on_your_device, R.drawable.onboarding_find,
        PipMood.WOW,
        listOf(
            OnboardingFeature(Icons.Outlined.TextFields, R.string.onboarding_feature_ocr),
            OnboardingFeature(Icons.Outlined.Search, R.string.onboarding_feature_search),
            OnboardingFeature(Icons.Outlined.ContentCopy, R.string.onboarding_feature_copy_text),
        ),
        Icons.Outlined.Lock, R.string.onboarding_assurance_on_device,
    ),
    SHARE(
        R.string.onboarding_page_share, R.string.onboarding_page_ready_when_you_are,
        R.string.onboarding_page_keep_pages_together_share_a, R.drawable.onboarding_share,
        PipMood.HAPPY,
        listOf(
            OnboardingFeature(Icons.Outlined.PictureAsPdf, R.string.onboarding_feature_multipage_pdf),
            OnboardingFeature(Icons.Outlined.Lock, R.string.onboarding_feature_password),
            OnboardingFeature(Icons.Outlined.Draw, R.string.onboarding_feature_sign),
        ),
        Icons.Outlined.Lock, R.string.onboarding_no_account_no_cloud_upload,
    ),
    ;

    val chapter: String get() = appString(chapterRes)
    val title: String get() = appString(titleRes)
    val description: String get() = appString(descriptionRes)
}

internal enum class OnboardingLayoutMode { COMPACT, MEDIUM, WIDE }

/** Use the actual available bounds, so split-screen and resizable windows adapt too. */
internal fun resolveOnboardingLayoutMode(widthDp: Float, heightDp: Float): OnboardingLayoutMode = when {
    widthDp < 600f -> OnboardingLayoutMode.COMPACT
    widthDp < 840f && heightDp >= widthDp -> OnboardingLayoutMode.MEDIUM
    else -> OnboardingLayoutMode.WIDE
}

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
    val enabled = interactionEnabled && !uiState.isCompleting
    val pageIndex = pager.currentPage
    val lastPage = OnboardingPage.entries.lastIndex
    val navEnabled = enabled && !pager.isScrollInProgress
    val goTo: (Int) -> Unit = { target ->
        if (enabled && !pager.isScrollInProgress) {
            scope.launch { pager.animateScrollToPage(target) }
        }
    }

    BackHandler(enabled = enabled && pageIndex > 0) { goTo(pageIndex - 1) }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbar.showSnackbar(it)
            onDismissError()
        }
    }

    Scaffold(
        modifier = modifier.testTag("onboarding_screen"),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            val mode = resolveOnboardingLayoutMode(maxWidth.value, maxHeight.value)
            val horizontalPadding = if (maxWidth < 360.dp) 20.dp else 24.dp
            val short = maxHeight < 480.dp
            Column(
                modifier = Modifier
                    .widthIn(max = 1_240.dp)
                    .fillMaxSize()
                    .testTag("onboarding_layout_${mode.name.lowercase()}"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Header stays fixed across pages: the back action lives next to Continue.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding, vertical = if (short) 0.dp else 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.onboarding_scanly),
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, lineHeight = 30.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    if (pageIndex < lastPage) {
                        TextButton(
                            onClick = { goTo(lastPage) },
                            enabled = navEnabled,
                            modifier = Modifier.testTag("onboarding_skip"),
                        ) {
                            Text(
                                stringResource(R.string.onboarding_skip),
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                val pagerState = stringResource(R.string.onboarding_page_of_3_2, pageIndex + 1)
                HorizontalPager(
                    state = pager,
                    userScrollEnabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("onboarding_pager")
                        .semantics { stateDescription = pagerState },
                ) { index ->
                    OnboardingPageContent(
                        page = OnboardingPage.entries[index],
                        pager = pager,
                        index = index,
                        mode = mode,
                        horizontalPadding = horizontalPadding,
                    )
                }

                Column(
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding)
                        .padding(top = if (short) 0.dp else 4.dp, bottom = if (short) 8.dp else 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (short) 2.dp else 8.dp),
                ) {
                    PageIndicator(pageIndex = pageIndex, enabled = navEnabled, onSelect = goTo)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AnimatedVisibility(
                            visible = pageIndex > 0,
                            enter = fadeIn() + expandHorizontally(),
                            exit = fadeOut() + shrinkHorizontally(),
                        ) {
                            FilledTonalIconButton(
                                onClick = { goTo(pageIndex - 1) },
                                enabled = navEnabled,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .size(56.dp)
                                    .testTag("onboarding_back"),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.onboarding_previous_page),
                                )
                            }
                        }
                        Button(
                            onClick = {
                                if (pageIndex == lastPage) onComplete() else goTo(pageIndex + 1)
                            },
                            enabled = navEnabled,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 56.dp)
                                .testTag(
                                    if (pageIndex == lastPage) ScanlyTestTags.ONBOARDING_GET_STARTED
                                    else "onboarding_continue",
                                ),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                            colors = ButtonDefaults.buttonColors(
                                disabledContainerColor = MaterialTheme.colorScheme.primary,
                                disabledContentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            if (uiState.isCompleting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text(
                                    if (pageIndex == lastPage) stringResource(R.string.onboarding_get_started)
                                    else stringResource(R.string.onboarding_continue),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 24.sp),
                                    fontWeight = FontWeight.Bold,
                                )
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
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    Row(
        modifier = Modifier.selectableGroup(),
        horizontalArrangement = Arrangement.Center,
    ) {
        OnboardingPage.entries.forEachIndexed { index, page ->
            val selected = pageIndex == index
            val width by animateDpAsState(if (selected) 24.dp else 8.dp, label = "indicatorWidth")
            val color by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                label = "indicatorColor",
            )
            val description = stringResource(
                R.string.onboarding_page_of_3,
                page.chapter.lowercase(locale).replaceFirstChar { it.titlecase(locale) },
                index + 1,
            )
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .width(40.dp)
                    .selectable(
                        selected = selected,
                        enabled = enabled,
                        role = Role.Tab,
                        onClick = { onSelect(index) },
                    )
                    .semantics { contentDescription = description }
                    .testTag("onboarding_dot_$index"),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(width = width, height = 8.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    pager: PagerState,
    index: Int,
    mode: OnboardingLayoutMode,
    horizontalPadding: Dp,
) {
    // -1..1 distance of this page from the settled position; drives a light parallax.
    val offset = { ((pager.currentPage - index) + pager.currentPageOffsetFraction).coerceIn(-1f, 1f) }
    val artMotion = Modifier.graphicsLayer {
        val o = offset()
        val scale = 1f - 0.08f * o.absoluteValue
        scaleX = scale
        scaleY = scale
        alpha = 1f - 0.5f * o.absoluteValue
    }
    val copyMotion = Modifier.graphicsLayer {
        val o = offset()
        translationX = o * size.width * 0.25f
        alpha = 1f - o.absoluteValue
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = horizontalPadding)
            .testTag("onboarding_page_${page.name.lowercase()}"),
        contentAlignment = Alignment.Center,
    ) {
        val viewportHeight = maxHeight
        if (mode == OnboardingLayoutMode.WIDE) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                OnboardingIllustration(
                    page = page,
                    modifier = Modifier
                        .weight(1.1f)
                        .then(artMotion),
                    height = viewportHeight.coerceAtMost(420.dp),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .then(copyMotion),
                    verticalArrangement = Arrangement.Center,
                ) {
                    OnboardingCopy(page, prominent = viewportHeight >= 400.dp)
                    Spacer(Modifier.height(20.dp))
                    FeatureCard(page)
                }
            }
        } else {
            val illustrationHeight = minOf(
                (viewportHeight * 0.4f).coerceIn(140.dp, 340.dp),
                minOf(maxWidth, 520.dp) * (1058f / 1487f),
            )
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = viewportHeight)
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                OnboardingIllustration(
                    page = page,
                    height = illustrationHeight,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(artMotion),
                )
                Spacer(Modifier.height(if (viewportHeight < 400.dp) 12.dp else 20.dp))
                Column(copyMotion) {
                    OnboardingCopy(page, prominent = mode == OnboardingLayoutMode.MEDIUM)
                    Spacer(Modifier.height(if (viewportHeight < 400.dp) 12.dp else 20.dp))
                    FeatureCard(page)
                }
            }
        }
    }
}

@Composable
private fun OnboardingCopy(page: OnboardingPage, prominent: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            page.chapter,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, letterSpacing = 1.5.sp),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            page.title,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = if (prominent) 36.sp else 30.sp,
                lineHeight = if (prominent) 42.sp else 36.sp,
            ),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            page.description,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 24.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Concrete capabilities per chapter, so each page shows what the app does, not only a slogan. */
@Composable
private fun FeatureCard(page: OnboardingPage) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("onboarding_features_${page.name.lowercase()}"),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            page.features.forEach { feature ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            feature.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text(
                        stringResource(feature.label),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    page.assuranceIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    stringResource(page.assurance),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OnboardingIllustration(page: OnboardingPage, height: Dp, modifier: Modifier = Modifier) {
    val glow = MaterialTheme.colorScheme.primary
    BoxWithConstraints(
        modifier = modifier.height(height),
        contentAlignment = Alignment.Center,
    ) {
        // Fit the entire asset and anchor the real, motion-aware Pip to that same rectangle.
        val artWidth = minOf(maxWidth, maxHeight * (1487f / 1058f))
        val artHeight = artWidth * (1058f / 1487f)
        // Soft brand glow so the art sits in light instead of floating on a flat background.
        Box(
            Modifier
                .size(artWidth * 1.15f, artHeight * 1.15f)
                .background(
                    Brush.radialGradient(
                        listOf(glow.copy(alpha = 0.18f), Color.Transparent),
                    ),
                ),
        )
        Box(Modifier.size(artWidth, artHeight)) {
            Image(
                painter = painterResource(page.illustration),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            Pip(
                mood = page.mood,
                look = PipLook.RIGHT,
                size = (artWidth * 0.24f).coerceIn(48.dp, 112.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = artWidth * 0.02f, bottom = artHeight * 0.03f),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(showBackground = true, widthDp = 320, heightDp = 568)
@Preview(showBackground = true, widthDp = 800, heightDp = 1_280)
@Preview(showBackground = true, widthDp = 900, heightDp = 420)
@Composable
private fun OnboardingScreenPreview() {
    ScanlyTheme {
        OnboardingScreen(OnboardingUiState(status = OnboardingStatus.REQUIRED), {}, {})
    }
}
