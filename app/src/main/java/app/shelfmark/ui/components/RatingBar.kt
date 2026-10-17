package app.shelfmark.ui.components

import app.shelfmark.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import app.shelfmark.ui.icons.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.shelfmark.ui.theme.LocalStarColor

/**
 * Five stars. Pass [onRatingChange] to make it interactive; tapping the
 * current rating again clears it.
 */
@Composable
fun RatingBar(
    rating: Int,
    modifier: Modifier = Modifier,
    starSize: Dp = 18.dp,
    spacing: Dp = 2.dp,
    onRatingChange: ((Int) -> Unit)? = null,
) {
    val starColor = LocalStarColor.current
    val emptyColor = MaterialTheme.colorScheme.outline
    val ratedDescription = stringResource(R.string.rated_out_of_5, rating)
    val a11y = if (onRatingChange == null) {
        Modifier.semantics(mergeDescendants = true) { contentDescription = ratedDescription }
    } else {
        // TalkBack reads the current rating along with each star's action.
        Modifier.semantics { stateDescription = ratedDescription }
    }
    Row(modifier.then(a11y), horizontalArrangement = Arrangement.spacedBy(spacing)) {
        for (i in 1..5) {
            val filled = i <= rating
            val scale by animateFloatAsState(
                targetValue = if (filled) 1f else 0.9f,
                animationSpec = spring(dampingRatio = 0.45f, stiffness = 500f),
                label = "star$i",
            )
            val rateLabel = stringResource(R.string.rate_out_of_5, i)
            val starsLabel = pluralStringResource(R.plurals.stars, i, i)
            val tap = if (onRatingChange != null) {
                Modifier
                    .clip(CircleShape)
                    .clickable(onClickLabel = rateLabel) {
                        onRatingChange(if (rating == i) 0 else i)
                    }
            } else {
                Modifier
            }
            Icon(
                imageVector = if (filled) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                contentDescription = if (onRatingChange != null) starsLabel else null,
                tint = if (filled) starColor else emptyColor,
                modifier = Modifier
                    // A finger-sized target around each star that can be tapped.
                    .then(if (onRatingChange != null) Modifier.minimumInteractiveComponentSize() else Modifier)
                    .size(starSize)
                    .then(tap)
                    .graphicsLayer { scaleX = scale; scaleY = scale },
            )
        }
    }
}

@Composable
fun ratingLabel(rating: Int): String = stringResource(
    when (rating) {
        1 -> R.string.rating_1
        2 -> R.string.rating_2
        3 -> R.string.rating_3
        4 -> R.string.rating_4
        5 -> R.string.rating_5
        else -> R.string.rating_none
    },
)
