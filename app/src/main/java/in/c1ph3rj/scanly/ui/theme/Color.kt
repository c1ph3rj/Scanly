package `in`.c1ph3rj.scanly.ui.theme

import androidx.compose.ui.graphics.Color

// Premium Brand Colors
val BrandPrimary = Color(0xFF0F766E)     // Deep Teal (redesign accent, 5.4:1 on white)
val BrandPrimaryDark = Color(0xFF2DD4BF) // Crisp emerald for dark surfaces
val BrandSecondary = Color(0xFF00BFA5)   // Vibrant Teal/Cyan
val BrandTertiary = Color(0xFF00574B)    // Deep accent for light surfaces
val BrandTertiaryDark = Color(0xFF80CBC4) // Soft accent for dark surfaces
val BrandError = Color(0xFFFA4D56)       // Premium Red

// Dark Mode Tints
val DarkBackground = Color(0xFF0C0F0E)
val DarkSurface = Color(0xFF111513)
val DarkSurfaceVariant = Color(0xFF26302D)
val DarkSurfaceContainerLowest = Color(0xFF090C0B)
val DarkSurfaceContainerLow = Color(0xFF101412)
val DarkSurfaceContainer = Color(0xFF171D1A)
val DarkSurfaceContainerHigh = Color(0xFF1F2723)
val DarkSurfaceContainerHighest = Color(0xFF29322F)
val DarkOnBackground = Color(0xFFF2F7F5)
val DarkOnSurfaceVariant = Color(0xFFAEBDB8)
val DarkOutline = Color(0xFF52625D)
val DarkOutlineVariant = Color(0xFF2F3B37)
val DarkPrimaryContainer = Color(0xFF00534A)
val DarkSecondaryContainer = Color(0xFF164E48)
val DarkTertiaryContainer = Color(0xFF253C37)
val DarkErrorContainer = Color(0xFF5D1F25)

// Pure black (AMOLED) — keep large planes at #000 for battery savings
val AmoledBlack = Color(0xFF000000)
val AmoledSurfaceVariant = Color(0xFF1A1F1D)
val AmoledSurfaceContainerLowest = Color(0xFF000000)
val AmoledSurfaceContainerLow = Color(0xFF0A0C0B)
val AmoledSurfaceContainer = Color(0xFF101413)
val AmoledSurfaceContainerHigh = Color(0xFF171B19)
val AmoledSurfaceContainerHighest = Color(0xFF1F2422)
val AmoledOutline = Color(0xFF4A5652)
val AmoledOutlineVariant = Color(0xFF242B29)
val AmoledPrimaryContainer = Color(0xFF003D37)
val AmoledSecondaryContainer = Color(0xFF0E3A36)
val AmoledTertiaryContainer = Color(0xFF1A2E2A)
val AmoledErrorContainer = Color(0xFF4A151A)

// Pure-black sheet elevation ramp (see liftedForPureBlackSheet).
val AmoledSheetSurface = Color(0xFF181D1B)
val AmoledSheetContainer = Color(0xFF212725)
val AmoledSheetContainerHigh = Color(0xFF29302D)
val AmoledSheetContainerHighest = Color(0xFF313936)

// Light Mode Tints
// Cool-neutral ladder from the redesign foundations. Cards (surface / surfaceContainer) are
// pure white on a #F1F3F5 page so they separate clearly; wells, tracks and chips step down to
// #ECEFF2 so they stay visible *inside* white cards; hairlines are dark enough to read on both.
// The previous green-tinted ladder kept page and cards within ~2% luminance, which made cards
// and section backgrounds nearly invisible.
val LightBackground = Color(0xFFF1F3F5)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE3E7EB)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFF7F8FA)
val LightSurfaceContainer = Color(0xFFFFFFFF)
val LightSurfaceContainerHigh = Color(0xFFECEFF2)
val LightSurfaceContainerHighest = Color(0xFFE1E5E9)
val LightOnBackground = Color(0xFF101418)
val LightOnSurfaceVariant = Color(0xFF545E65)
val LightOutline = Color(0xFF7C878F)
val LightOutlineVariant = Color(0xFFD7DDE2)
val LightPrimaryContainer = Color(0xFFD4EFEB)
val LightOnPrimaryContainer = Color(0xFF0A4F49)
val LightSecondaryContainer = Color(0xFFCFF0EB)
val LightOnSecondaryContainer = Color(0xFF005048)
val LightTertiaryContainer = Color(0xFFDDEAE7)
val LightOnTertiaryContainer = Color(0xFF0A3D36)
