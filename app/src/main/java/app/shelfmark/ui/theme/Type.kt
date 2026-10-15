package app.shelfmark.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.shelfmark.R

/*
 * Two typefaces, each with one job:
 *  - Literata: Google's typeface designed for long-form reading on screens (Play Books).
 *    Used for book titles, headings and your own notes.
 *  - Atkinson Hyperlegible Next: designed by the Braille Institute for maximum
 *    character distinction. Used for all interface text.
 * Both are variable fonts, so every weight comes from a single file.
 */

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int, opticalSize: Float? = null) = Font(
    resId = res,
    weight = FontWeight(weight),
    variationSettings = if (opticalSize != null) {
        FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", opticalSize))
    } else {
        FontVariation.Settings(FontVariation.weight(weight))
    },
)

/** Literata tuned for reading sizes. */
val Literata = FontFamily(
    variable(R.font.literata, 400, 12f),
    variable(R.font.literata, 500, 12f),
    variable(R.font.literata, 600, 12f),
    variable(R.font.literata, 700, 12f),
)

/** Literata's display optical size: tighter, more refined at large sizes. */
val LiterataDisplay = FontFamily(
    variable(R.font.literata, 500, 36f),
    variable(R.font.literata, 600, 36f),
)

val Atkinson = FontFamily(
    variable(R.font.atkinson, 400),
    variable(R.font.atkinson, 500),
    variable(R.font.atkinson, 600),
    variable(R.font.atkinson, 700),
)

val ShelfmarkTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = LiterataDisplay, fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = LiterataDisplay, fontWeight = FontWeight.SemiBold,
        fontSize = 27.sp, lineHeight = 34.sp, letterSpacing = (-0.2).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Literata, fontWeight = FontWeight.SemiBold,
        fontSize = 23.sp, lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Literata, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 27.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Literata, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Atkinson, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 21.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Atkinson, fontWeight = FontWeight.Normal,
        fontSize = 17.sp, lineHeight = 25.sp, letterSpacing = 0.1.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Atkinson, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Atkinson, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Atkinson, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Atkinson, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Atkinson, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp,
    ),
)

/** For reading and writing your own notes: serif, generous leading. */
val ReadingStyle = TextStyle(
    fontFamily = Literata,
    fontWeight = FontWeight.Normal,
    fontSize = 18.sp,
    lineHeight = 29.sp,
)
