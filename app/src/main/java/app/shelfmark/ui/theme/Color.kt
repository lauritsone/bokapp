package app.shelfmark.ui.theme

import androidx.compose.runtime.Immutable
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
    // Teal, for lending: apart from the indigo, green and plum of the shelves.
    tertiary = Color(0xFF1E5560),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD3ECEF),
    onTertiaryContainer = Color(0xFF0B3138),
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
    tertiary = Color(0xFF9DD3DB),
    onTertiary = Color(0xFF00363D),
    tertiaryContainer = Color(0xFF1C4A52),
    onTertiaryContainer = Color(0xFFCDEFF3),
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

/** Colours the Material scheme has no slot for: two shelves, and strong matches. */
@Immutable
data class ExtendedColors(
    val readContainer: Color,
    val onReadContainer: Color,
    val wantContainer: Color,
    val onWantContainer: Color,
    val strongMatchContainer: Color,
    val onStrongMatchContainer: Color,
)

val LightExtendedColors = ExtendedColors(
    readContainer = Color(0xFFDCEFE3),
    onReadContainer = Color(0xFF1E5A36),
    wantContainer = Color(0xFFF2E3F2),
    onWantContainer = Color(0xFF5B2D5E),
    strongMatchContainer = Color(0xFFFBEBC8),
    onStrongMatchContainer = Color(0xFF5E3F00),
)

val DarkExtendedColors = ExtendedColors(
    readContainer = Color(0xFF1F3A2A),
    onReadContainer = Color(0xFFB5E3C4),
    wantContainer = Color(0xFF3E2440),
    onWantContainer = Color(0xFFEBC4EC),
    strongMatchContainer = Color(0xFF4A3708),
    onStrongMatchContainer = Color(0xFFF9DC9C),
)

/** The scanner draws over the camera picture, which is dark whatever the app's theme. */
object CameraColors {
    val background = Color.Black
    val content = Color.White
    val contentDim = Color.White.copy(alpha = 0.8f)
    val scrim = Color.Black.copy(alpha = 0.55f)
    val controls = Color.Black.copy(alpha = 0.5f)
    val accent = StarDark
    val error = DarkColors.error
}
