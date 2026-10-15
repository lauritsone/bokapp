package app.shelfmark.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// "Ex libris" palette: bookplate indigo ink on cool paper, with a marigold star.
private val Indigo = Color(0xFF26336B)
private val Ink = Color(0xFF161A26)

val StarLight = Color(0xFFDB9112)
val StarDark = Color(0xFFF3B740)

val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE2F7),
    onPrimaryContainer = Color(0xFF121C4A),
    secondary = Color(0xFF5A6280),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1E4F0),
    onSecondaryContainer = Color(0xFF1C2238),
    background = Color(0xFFF3F4F7),
    onBackground = Ink,
    surface = Color(0xFFF3F4F7),
    onSurface = Ink,
    surfaceVariant = Color(0xFFE0E3EA),
    onSurfaceVariant = Color(0xFF4A5163),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFECEEF3),
    surfaceContainer = Color(0xFFE7E9EF),
    surfaceContainerHigh = Color(0xFFE1E4EB),
    surfaceContainerHighest = Color(0xFFDADEE6),
    outline = Color(0xFF767D90),
    outlineVariant = Color(0xFFC6CBD7),
    error = Color(0xFFB3261E),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFFB7C3F5),
    onPrimary = Color(0xFF1A2558),
    primaryContainer = Color(0xFF2E3B78),
    onPrimaryContainer = Color(0xFFDDE2FF),
    secondary = Color(0xFFBFC5DD),
    onSecondary = Color(0xFF282E44),
    secondaryContainer = Color(0xFF353C55),
    onSecondaryContainer = Color(0xFFDDE1F4),
    background = Color(0xFF0F121A),
    onBackground = Color(0xFFE6E8EF),
    surface = Color(0xFF0F121A),
    onSurface = Color(0xFFE6E8EF),
    surfaceVariant = Color(0xFF2C3243),
    onSurfaceVariant = Color(0xFFADB3C3),
    surfaceContainerLowest = Color(0xFF0A0C12),
    surfaceContainerLow = Color(0xFF161A24),
    surfaceContainer = Color(0xFF1B2030),
    surfaceContainerHigh = Color(0xFF232838),
    surfaceContainerHighest = Color(0xFF2C3243),
    outline = Color(0xFF858CA0),
    outlineVariant = Color(0xFF3A4053),
    error = Color(0xFFF2B8B5),
)
