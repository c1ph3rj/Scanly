package `in`.c1ph3rj.scanly.feature.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import `in`.c1ph3rj.scanly.core.ui.ScanlyTestTags
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import `in`.c1ph3rj.scanly.core.ui.WindowWidthClass
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.feature.components.ScanlyAppLogo
import `in`.c1ph3rj.scanly.feature.pip.PipLines
import `in`.c1ph3rj.scanly.feature.pip.PipMood
import `in`.c1ph3rj.scanly.feature.pip.PipSays
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import `in`.c1ph3rj.scanly.ui.theme.ScanlyTheme
import kotlinx.coroutines.delay

@Composable
fun OnboardingScreen(
    uiState: OnboardingUiState,
    onComplete: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val windowSizeInfo = rememberWindowSizeInfo()
    val snackbarHostState = remember { SnackbarHostState() }
    val colorScheme = MaterialTheme.colorScheme
    var animationStage by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        animationStage = 1
        delay(110)
        animationStage = 2
        delay(130)
        animationStage = 3
        delay(90)
        animationStage = 4
        delay(90)
        animationStage = 5
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            onDismissError()
        }
    }

    Scaffold(
        modifier = modifier.testTag("onboarding_screen"),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            colorScheme.background,
                            colorScheme.primaryContainer.copy(alpha = 0.22f),
                            colorScheme.background,
                        ),
                    ),
                )
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(contentPadding),
        ) {
            OnboardingLayout(
                layoutMode = resolveOnboardingLayoutMode(
                    widthClass = windowSizeInfo.widthClass,
                    isLandscape = windowSizeInfo.isLandscape,
                ),
                animationStage = animationStage,
                isCompleting = uiState.isCompleting,
                onComplete = onComplete,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
internal fun OnboardingLayout(
    layoutMode: OnboardingLayoutMode,
    animationStage: Int,
    isCompleting: Boolean,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (layoutMode) {
        OnboardingLayoutMode.COMPACT -> CompactOnboardingLayout(
            animationStage = animationStage,
            isCompleting = isCompleting,
            onComplete = onComplete,
            modifier = modifier,
        )

        OnboardingLayoutMode.MEDIUM -> MediumOnboardingLayout(
            animationStage = animationStage,
            isCompleting = isCompleting,
            onComplete = onComplete,
            modifier = modifier,
        )

        OnboardingLayoutMode.WIDE -> WideOnboardingLayout(
            animationStage = animationStage,
            isCompleting = isCompleting,
            onComplete = onComplete,
            modifier = modifier,
        )
    }
}

@Composable
private fun CompactOnboardingLayout(
    animationStage: Int,
    isCompleting: Boolean,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .testTag("onboarding_layout_compact"),
    ) {
        OnboardingHeader(
            visible = animationStage >= 1,
            modifier = Modifier.padding(top = 12.dp),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            ScannerHero(
                visible = animationStage >= 2,
                compact = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(20.dp))
            OnboardingMessage(visible = animationStage >= 2)
            Spacer(modifier = Modifier.height(18.dp))
            FeatureList(animationStage = animationStage)
            Spacer(modifier = Modifier.height(16.dp))
        }

        OnboardingAction(
            visible = animationStage >= 5,
            isCompleting = isCompleting,
            onComplete = onComplete,
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }
}

@Composable
private fun MediumOnboardingLayout(
    animationStage: Int,
    isCompleting: Boolean,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.testTag("onboarding_layout_medium"),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OnboardingHeader(
                visible = animationStage >= 1,
                modifier = Modifier.padding(top = 16.dp),
            )

            // Centre the hero and copy in the space above the button: on a tall tablet they used
            // to sit at the top with a large empty gap below.
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val viewportHeight = maxHeight
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = viewportHeight),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Spacer(modifier = Modifier.height(28.dp))
                    ScannerHero(
                        visible = animationStage >= 2,
                        compact = false,
                        heroHeight = if (viewportHeight >= 900.dp) 360.dp else 280.dp,
                        modifier = Modifier
                            .widthIn(max = 640.dp)
                            .fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                    OnboardingMessage(
                        visible = animationStage >= 2,
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    FeatureList(
                        animationStage = animationStage,
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            OnboardingAction(
                visible = animationStage >= 5,
                isCompleting = isCompleting,
                onComplete = onComplete,
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun WideOnboardingLayout(
    animationStage: Int,
    isCompleting: Boolean,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.testTag("onboarding_layout_wide"),
        contentAlignment = Alignment.Center,
    ) {
        val heightConstrained = maxHeight < 680.dp
        // Phone landscape (~400 dp tall): titles-only features and a hero that fits the height.
        val veryShort = maxHeight < 480.dp
        val widthConstrained = maxWidth < 960.dp
        val horizontalPadding = if (widthConstrained) 24.dp else 48.dp
        val verticalPadding = if (heightConstrained) 16.dp else 28.dp
        val paneSpacing = if (widthConstrained) 28.dp else 64.dp
        val heroHeight = when {
            veryShort -> (maxHeight - 140.dp).coerceIn(180.dp, 280.dp)
            heightConstrained -> 280.dp
            else -> 440.dp
        }

        Column(
            modifier = Modifier
                .widthIn(max = 1_240.dp)
                .fillMaxSize()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        ) {
            OnboardingHeader(visible = animationStage >= 1)
            Spacer(modifier = Modifier.height(if (heightConstrained) 14.dp else 32.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(paneSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScannerHero(
                    visible = animationStage >= 2,
                    compact = false,
                    heroHeight = heroHeight,
                    modifier = Modifier.weight(1.05f),
                )

                Column(
                    modifier = Modifier
                        .weight(0.95f)
                        .fillMaxHeight(),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        OnboardingMessage(
                            visible = animationStage >= 2,
                            prominent = !heightConstrained,
                            showSupporting = !veryShort,
                        )
                        Spacer(modifier = Modifier.height(if (veryShort) 10.dp else if (heightConstrained) 16.dp else 28.dp))
                        FeatureList(
                            animationStage = animationStage,
                            spacious = !heightConstrained,
                            showDetails = !veryShort,
                        )
                    }
                    Spacer(modifier = Modifier.height(if (heightConstrained) 12.dp else 20.dp))
                    OnboardingAction(
                        visible = animationStage >= 5,
                        isCompleting = isCompleting,
                        onComplete = onComplete,
                    )
                }
            }
        }
    }
}

internal enum class OnboardingLayoutMode {
    COMPACT,
    MEDIUM,
    WIDE,
}

internal fun resolveOnboardingLayoutMode(
    widthClass: WindowWidthClass,
    isLandscape: Boolean,
): OnboardingLayoutMode = when (widthClass) {
    WindowWidthClass.Compact -> OnboardingLayoutMode.COMPACT
    WindowWidthClass.Medium -> {
        if (isLandscape) OnboardingLayoutMode.WIDE else OnboardingLayoutMode.MEDIUM
    }
    WindowWidthClass.Expanded -> OnboardingLayoutMode.WIDE
}

@Composable
private fun OnboardingHeader(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(420)) + slideInVertically(tween(420)) { -it / 3 },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScanlyAppLogo(size = 42.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Scanly.",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.weight(1f))
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.84f),
                contentColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(50),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                ),
            ) {
                Text(
                    text = "OPEN SOURCE",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun OnboardingMessage(
    visible: Boolean,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
    /** False on very short windows, where the feature titles already say it. */
    showSupporting: Boolean = true,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(480)) + slideInVertically(tween(480)) { it / 4 },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                        append("Paper in.")
                    }
                    append(" Ready-to-share out.")
                },
                style = if (prominent) {
                    MaterialTheme.typography.displaySmall
                } else {
                    MaterialTheme.typography.headlineMedium
                },
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (showSupporting) {
                Text(
                    text = "Capture, clean up, organize, and export your documents without leaving Scanly.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FeatureList(
    animationStage: Int,
    modifier: Modifier = Modifier,
    spacious: Boolean = false,
    /** False on very short windows: titles only, so all three fit beside the hero. */
    showDetails: Boolean = true,
) {
    Column(
        modifier = modifier.testTag("onboarding_features"),
        verticalArrangement = Arrangement.spacedBy(if (showDetails) 14.dp else 8.dp),
    ) {
        FeatureRow(
            visible = animationStage >= 3,
            icon = Icons.Filled.CameraAlt,
            title = "Capture clean pages",
            description = "Detect edges, straighten pages, and enhance every scan.",
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            spacious = spacious,
            showDetails = showDetails,
        )
        FeatureRow(
            visible = animationStage >= 4,
            icon = Icons.Filled.Folder,
            title = "Keep documents organized",
            description = "Import images, reorder pages, and group documents into folders.",
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            spacious = spacious,
            showDetails = showDetails,
        )
        FeatureRow(
            visible = animationStage >= 5,
            icon = Icons.Filled.PictureAsPdf,
            title = "Export your way",
            description = "Share polished PDFs or a complete image archive when needed.",
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            spacious = spacious,
            showDetails = showDetails,
        )
    }
}

@Composable
private fun FeatureRow(
    visible: Boolean,
    icon: ImageVector,
    title: String,
    description: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    spacious: Boolean = false,
    showDetails: Boolean = true,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(430)) + slideInVertically(tween(430)) { it / 2 },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (spacious) 16.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(if (spacious) 56.dp else if (showDetails) 48.dp else 40.dp),
                shape = MaterialTheme.shapes.large,
                color = containerColor,
                contentColor = contentColor,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(if (spacious) 28.dp else 25.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = if (spacious) {
                        MaterialTheme.typography.titleLarge
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (showDetails) {
                    Text(
                        text = description,
                        style = if (spacious) {
                            MaterialTheme.typography.bodyLarge
                        } else {
                            MaterialTheme.typography.bodyMedium
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingAction(
    visible: Boolean,
    isCompleting: Boolean,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(480)) + slideInVertically(tween(480)) { it / 2 },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onComplete,
                enabled = !isCompleting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag(ScanlyTestTags.ONBOARDING_GET_STARTED),
                shape = MaterialTheme.shapes.large,
                contentPadding = PaddingValues(horizontal = 22.dp),
                // Disabled colours must stay primary: the default disabled container is a light grey,
                // which hid the onPrimary spinner while the app was completing onboarding.
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary,
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                if (isCompleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = "Get started",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "No account required · processing stays on-device",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ScannerHero(
    visible: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
    heroHeight: Dp = if (compact) 248.dp else 360.dp,
) {
    val pipSize = when {
        compact -> 112.dp
        heroHeight >= 400.dp -> 160.dp
        else -> 128.dp
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(520)) + scaleIn(tween(520), initialScale = 0.94f),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight)
                .testTag("onboarding_pip"),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PipSays(
                    line = PipLines.ONBOARDING,
                    mood = PipMood.IDLE,
                    size = pipSize,
                    bubbleMaxWidth = if (compact) 270.dp else 320.dp,
                )
                // A soft floor so Pip stands on something.
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .width(pipSize * 0.6f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)),
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingScreenPreview() {
    ScanlyTheme {
        OnboardingScreen(
            uiState = OnboardingUiState(status = OnboardingStatus.REQUIRED),
            onComplete = {},
            onDismissError = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 900, heightDp = 600)
@Composable
private fun OnboardingScreenWidePreview() {
    ScanlyTheme(darkTheme = true) {
        OnboardingScreen(
            uiState = OnboardingUiState(status = OnboardingStatus.REQUIRED),
            onComplete = {},
            onDismissError = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 1_280)
@Composable
private fun OnboardingScreenTabletPortraitPreview() {
    ScanlyTheme {
        OnboardingScreen(
            uiState = OnboardingUiState(status = OnboardingStatus.REQUIRED),
            onComplete = {},
            onDismissError = {},
        )
    }
}
