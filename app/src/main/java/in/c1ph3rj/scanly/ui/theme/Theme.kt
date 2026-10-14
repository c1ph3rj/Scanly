package `in`.c1ph3rj.scanly.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = BrandPrimaryDark,
    onPrimary = DarkBackground,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnBackground,
    secondary = BrandSecondary,
    onSecondary = DarkBackground,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = Color(0xFFC9FFF7),
    tertiary = BrandTertiaryDark,
    onTertiary = DarkBackground,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = Color(0xFFD5ECE7),
    error = BrandError,
    onError = DarkBackground,
    errorContainer = DarkErrorContainer,
    onErrorContainer = Color(0xFFFFB4AB),
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnBackground,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
)

/**
 * Material 3 dark scheme with pure black planes for OLED/AMOLED panels.
 * Large surfaces stay #000 so pixels can power off; elevated containers
 * use minimal lift so cards and sheets remain readable.
 */
private val PureBlackColorScheme = darkColorScheme(
    primary = BrandPrimaryDark,
    onPrimary = AmoledBlack,
    primaryContainer = AmoledPrimaryContainer,
    onPrimaryContainer = DarkOnBackground,
    secondary = BrandSecondary,
    onSecondary = AmoledBlack,
    secondaryContainer = AmoledSecondaryContainer,
    onSecondaryContainer = Color(0xFFC9FFF7),
    tertiary = BrandTertiaryDark,
    onTertiary = AmoledBlack,
    tertiaryContainer = AmoledTertiaryContainer,
    onTertiaryContainer = Color(0xFFD5ECE7),
    error = BrandError,
    onError = AmoledBlack,
    errorContainer = AmoledErrorContainer,
    onErrorContainer = Color(0xFFFFB4AB),
    background = AmoledBlack,
    onBackground = DarkOnBackground,
    surface = AmoledBlack,
    onSurface = DarkOnBackground,
    surfaceVariant = AmoledSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = AmoledSurfaceContainerLowest,
    surfaceContainerLow = AmoledSurfaceContainerLow,
    surfaceContainer = AmoledSurfaceContainer,
    surfaceContainerHigh = AmoledSurfaceContainerHigh,
    surfaceContainerHighest = AmoledSurfaceContainerHighest,
    outline = AmoledOutline,
    outlineVariant = AmoledOutlineVariant,
)

private val LightColorScheme = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = BrandSecondary,
    onSecondary = Color(0xFF00332E),
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = BrandTertiary,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = BrandError,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF8C1D18),
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnBackground,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
)

@Composable
fun ScanlyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        darkTheme && pureBlack -> PureBlackColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(LocalPureBlack provides (darkTheme && pureBlack)) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}

/** True when the pure-black (AMOLED) dark theme is active. */
val LocalPureBlack = staticCompositionLocalOf { false }

/**
 * Pure black has no room "below" the background, so elevated surfaces (sheets) must step up
 * visibly. Inside a sheet every surface token moves up one level: the sheet itself reads as
 * raised dark grey and its cards stay lighter than it, keeping the usual hierarchy.
 */
fun ColorScheme.liftedForPureBlackSheet(): ColorScheme = copy(
    surface = AmoledSheetSurface,
    surfaceContainerLowest = AmoledSurfaceContainerHigh,
    surfaceContainerLow = AmoledSheetSurface,
    surfaceContainer = AmoledSheetContainer,
    surfaceContainerHigh = AmoledSheetContainerHigh,
    surfaceContainerHighest = AmoledSheetContainerHighest,
    surfaceVariant = AmoledSheetContainerHigh,
)
