package `in`.c1ph3rj.scanly.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ScanlyMint,
    onPrimary = Ink950,
    primaryContainer = ScanlyMintDeep,
    onPrimaryContainer = Mist100,
    secondary = ScanlyAmber,
    onSecondary = Ink950,
    secondaryContainer = ColorTokensDark.secondaryContainer,
    onSecondaryContainer = Mist100,
    tertiary = ScanlyMintSoft,
    onTertiary = Ink950,
    tertiaryContainer = Ink700,
    onTertiaryContainer = Mist100,
    error = ScanlyError,
    onError = Ink950,
    background = Ink950,
    onBackground = Mist100,
    surface = Ink900,
    onSurface = Mist100,
    surfaceVariant = Ink800,
    onSurfaceVariant = Slate300,
    surfaceContainer = Ink850,
    surfaceContainerLow = Ink900,
    surfaceContainerHigh = Ink800,
    surfaceContainerHighest = Ink700,
    outline = Slate400.copy(alpha = 0.4f),
    outlineVariant = Slate400.copy(alpha = 0.18f),
)

private val LightColorScheme = lightColorScheme(
    primary = ScanlyMintDeep,
    onPrimary = ColorTokensLight.onPrimary,
    primaryContainer = ColorTokensLight.primaryContainer,
    onPrimaryContainer = Stone900,
    secondary = ScanlyAmber,
    onSecondary = Stone900,
    secondaryContainer = ColorTokensLight.secondaryContainer,
    onSecondaryContainer = Stone900,
    tertiary = ScanlyMint,
    onTertiary = Stone900,
    tertiaryContainer = ColorTokensLight.tertiaryContainer,
    onTertiaryContainer = Stone900,
    error = ScanlyError,
    onError = Paper50,
    background = Paper50,
    onBackground = Stone900,
    surface = Paper50,
    onSurface = Stone900,
    surfaceVariant = Paper100,
    onSurfaceVariant = Stone700,
    surfaceContainer = Paper100,
    surfaceContainerLow = Paper50,
    surfaceContainerHigh = ColorTokensLight.surfaceContainerHigh,
    surfaceContainerHighest = ColorTokensLight.surfaceContainerHighest,
    outline = Stone700.copy(alpha = 0.28f),
    outlineVariant = Stone700.copy(alpha = 0.12f),
)

@Composable
fun ScanlyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor -> if (darkTheme) DarkColorScheme else LightColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}

private object ColorTokensDark {
    val secondaryContainer = Ink700
}

private object ColorTokensLight {
    val onPrimary = Paper50
    val primaryContainer = Color(0xFFDAF6EF)
    val secondaryContainer = Color(0xFFFFE9BD)
    val tertiaryContainer = Color(0xFFD9F5EC)
    val surfaceContainerHigh = Color(0xFFE8EEEA)
    val surfaceContainerHighest = Color(0xFFDDE6E1)
}
