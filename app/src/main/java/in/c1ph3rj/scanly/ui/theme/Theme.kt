package `in`.c1ph3rj.scanly.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = BrandPrimaryDark,
    onPrimary = DarkBackground,
    primaryContainer = BrandPrimary,
    onPrimaryContainer = DarkOnBackground,
    secondary = BrandSecondary,
    onSecondary = DarkBackground,
    secondaryContainer = BrandSecondary.copy(alpha = 0.2f),
    onSecondaryContainer = BrandSecondary,
    tertiary = BrandTertiaryDark,
    onTertiary = DarkBackground,
    tertiaryContainer = BrandTertiaryDark.copy(alpha = 0.22f),
    onTertiaryContainer = BrandTertiaryDark,
    error = BrandError,
    onError = DarkBackground,
    errorContainer = BrandError.copy(alpha = 0.18f),
    onErrorContainer = Color(0xFFFFB4AB),
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnBackground,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainer = DarkSurfaceVariant,
    surfaceContainerLow = DarkSurface,
    surfaceContainerHigh = DarkSurfaceVariant,
    surfaceContainerHighest = Color(0xFF27272A),
    outline = DarkOutline,
    outlineVariant = DarkOutline.copy(alpha = 0.5f),
)

private val LightColorScheme = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = LightSurface,
    primaryContainer = BrandPrimary.copy(alpha = 0.1f),
    onPrimaryContainer = BrandPrimary,
    secondary = BrandSecondary,
    onSecondary = LightSurface,
    secondaryContainer = BrandSecondary.copy(alpha = 0.1f),
    onSecondaryContainer = BrandSecondary,
    tertiary = BrandTertiary,
    onTertiary = LightSurface,
    tertiaryContainer = BrandTertiary.copy(alpha = 0.12f),
    onTertiaryContainer = BrandTertiary,
    error = BrandError,
    onError = LightSurface,
    errorContainer = BrandError.copy(alpha = 0.12f),
    onErrorContainer = Color(0xFFBA1A1A),
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnBackground,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainer = LightSurfaceVariant,
    surfaceContainerLow = LightSurface,
    surfaceContainerHigh = LightSurfaceVariant,
    surfaceContainerHighest = Color(0xFFE4E4E7),
    outline = LightOutline,
    outlineVariant = LightOutline.copy(alpha = 0.5f),
)

@Composable
fun ScanlyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
