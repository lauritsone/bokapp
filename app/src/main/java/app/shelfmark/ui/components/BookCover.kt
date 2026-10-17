package app.shelfmark.ui.components

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.shelfmark.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.shelfmark.ui.theme.Literata
import coil.compose.AsyncImage

// Cloth-binding tones used when a book has no cover image.
private val bindingTones = listOf(
    Color(0xFF26336B), Color(0xFF4B3A6E), Color(0xFF1E5560),
    Color(0xFF6A3A3A), Color(0xFF3B5A3A), Color(0xFF5C4A2A),
)

/** Spine side is squarer than the fore-edge, like a real book. */
val CoverShape = RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp, topEnd = 7.dp, bottomEnd = 7.dp)

/**
 * A book cover at 2:3. Shows a typeset cloth "binding" underneath, so a missing
 * or still-loading image always looks intentional. Set the width via [modifier].
 */
@Composable
fun BookCover(
    model: Any?,
    title: String,
    modifier: Modifier = Modifier,
    elevation: Dp = 4.dp,
) {
    val tone = bindingTones[(title.hashCode() and Int.MAX_VALUE) % bindingTones.size]
    BoxWithConstraints(
        modifier
            .aspectRatio(2f / 3f)
            .shadow(elevation, CoverShape)
            .clip(CoverShape)
            .background(tone),
    ) {
        // Once the cover image is showing, the typeset title under it is skipped: measuring
        // it for every cover scrolled into view is wasted work.
        var loaded by remember(model) { mutableStateOf(false) }
        if (!loaded) {
            val text = title.ifBlank { stringResource(R.string.untitled) }
            val textWidth = maxWidth * (1f - CoverStartPadding - CoverEndPadding)
            val fontSize = rememberCoverFontSize(text, textWidth, baseSize = (maxWidth.value / 7.5f).coerceIn(8f, 24f))
            Text(
                text = text,
                color = Color.White.copy(alpha = 0.92f),
                style = coverTitleStyle(fontSize).copy(hyphens = Hyphens.Auto),
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(
                    start = maxWidth * CoverStartPadding,
                    end = maxWidth * CoverEndPadding,
                    top = maxWidth * 0.12f,
                ),
            )
        }
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { loaded = true },
                onError = { loaded = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Spine shading on top of everything
        Box(
            Modifier
                .fillMaxHeight()
                .width(maxWidth * 0.07f)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Black.copy(alpha = 0.28f), Color.White.copy(alpha = 0.06f), Color.Transparent),
                    ),
                ),
        )
    }
}

private const val CoverStartPadding = 0.14f
private const val CoverEndPadding = 0.08f

/** Smallest the title may shrink to, as a share of its normal size, before long words are hyphenated. */
private const val MinFontScale = 0.65f

private fun coverTitleStyle(fontSize: TextUnit) = TextStyle(
    fontFamily = Literata,
    fontWeight = FontWeight.SemiBold,
    fontSize = fontSize,
    lineHeight = fontSize * 1.18f,
)

/**
 * The title's font size, shrunk until its widest word fits on one line, so words like
 * "Snømannen" aren't broken mid-word on a small cover. Past [MinFontScale] it stops
 * shrinking and lets hyphenation break the word instead.
 */
@Composable
private fun rememberCoverFontSize(text: String, width: Dp, baseSize: Float): TextUnit {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(text, width, baseSize, density) {
        val available = with(density) { width.toPx() }
        val widest = text.split(Regex("\\s+")).filter { it.isNotEmpty() }.maxOfOrNull { word ->
            measurer.measure(word, coverTitleStyle(baseSize.sp), maxLines = 1, softWrap = false).size.width
        } ?: 0
        val scale = if (widest > available) (available / widest * 0.98f).coerceAtLeast(MinFontScale) else 1f
        (baseSize * scale).sp
    }
}
