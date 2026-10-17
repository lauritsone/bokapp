package app.shelfmark.ui.components

import android.util.LruCache
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

/** The cover sizes used across the app, each with its own shadow. */
enum class CoverSize(val width: Dp, val elevation: Dp) {
    /** In compact lists: series, search results. */
    Thumb(40.dp, 2.dp),

    /** In the library and suggestion lists. */
    Standard(68.dp, 3.dp),

    /** Beside a form or in a sheet. */
    Medium(104.dp, 6.dp),

    /** At the top of a book's page. */
    Hero(164.dp, 14.dp),
}

/**
 * A book cover at 2:3. Shows a typeset cloth "binding" underneath, so a missing
 * or still-loading image always looks intentional.
 */
@Composable
fun BookCover(
    model: Any?,
    title: String,
    size: CoverSize,
    modifier: Modifier = Modifier,
) {
    val tone = bindingTones[(title.hashCode() and Int.MAX_VALUE) % bindingTones.size]
    val width = size.width
    Box(
        modifier
            .width(width)
            .aspectRatio(2f / 3f)
            .shadow(size.elevation, CoverShape)
            .clip(CoverShape)
            .background(tone),
    ) {
        // Once the cover image is showing, the typeset title under it is no longer drawn.
        var loaded by remember(model) { mutableStateOf(false) }
        if (!loaded) {
            val text = title.ifBlank { stringResource(R.string.untitled) }
            val textWidth = width * (1f - CoverStartPadding - CoverEndPadding)
            val fontSize = rememberCoverFontSize(text, textWidth, baseSize = (width.value / 7.5f).coerceIn(8f, 24f))
            Text(
                text = text,
                color = Color.White.copy(alpha = 0.92f),
                style = coverTitleStyle(fontSize).copy(hyphens = Hyphens.Auto),
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(
                    start = width * CoverStartPadding,
                    end = width * CoverEndPadding,
                    top = width * 0.12f,
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
                .width(width * 0.07f)
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
        // The same covers scroll past again and again; each title is measured once.
        val key = "$text|${width.value}|$baseSize|${density.density}|${density.fontScale}"
        fontSizes[key]?.sp ?: run {
            val available = with(density) { width.toPx() }
            val widest = text.split(whitespace).filter { it.isNotEmpty() }.maxOfOrNull { word ->
                measurer.measure(word, coverTitleStyle(baseSize.sp), maxLines = 1, softWrap = false).size.width
            } ?: 0
            val scale = if (widest > available) (available / widest * 0.98f).coerceAtLeast(MinFontScale) else 1f
            (baseSize * scale).also { fontSizes.put(key, it) }.sp
        }
    }
}

/** Font sizes already worked out, by title, width and screen density. */
private val fontSizes = LruCache<String, Float>(512)

private val whitespace = Regex("\\s+")
