package app.shelfmark.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.shelfmark.ui.theme.Dimens

/**
 * The one layout for empty, offline and "nothing here" states: an icon (or [illustration]),
 * a title, a line of text and the actions that lead on.
 */
@Composable
fun EmptyState(
    title: String?,
    modifier: Modifier = Modifier,
    body: String? = null,
    icon: ImageVector? = null,
    illustration: (@Composable () -> Unit)? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    secondaryColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 48.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            illustration != null -> {
                illustration()
                Spacer(Modifier.height(28.dp))
            }
            icon != null -> {
                Icon(icon, contentDescription = null, tint = secondaryColor, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(20.dp))
            }
        }
        title?.let {
            Text(it, style = MaterialTheme.typography.titleLarge, color = contentColor, textAlign = TextAlign.Center)
        }
        body?.let {
            if (title != null) Spacer(Modifier.height(8.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodyLarge,
                color = secondaryColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 360.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        actions()
    }
}

/** A whole screen still loading, with an optional line on what is happening. */
@Composable
fun LoadingState(modifier: Modifier = Modifier, text: String? = null) {
    Column(
        modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        text?.let {
            Spacer(Modifier.height(20.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The spinner inside a button while its action runs, in place of the button's icon. */
@Composable
fun ButtonSpinner() {
    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
}

/** The spinner in a top-bar action while it runs, in place of the action's icon. */
@Composable
fun BarSpinner(color: Color = MaterialTheme.colorScheme.primary) {
    CircularProgressIndicator(Modifier.size(24.dp), color = color, strokeWidth = 2.dp)
}

/** A rounded card for a group of related content; every info card in the app looks like this. */
@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    padding: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(color)
            .then(if (padding) Modifier.padding(Dimens.cardPadding) else Modifier),
        content = content,
    )
}

/** A page section's heading, with an optional action (such as Edit) at the end. */
@Composable
fun SectionTitle(text: String, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        action?.invoke()
    }
    Spacer(Modifier.height(Dimens.headerGap))
}

/**
 * A book in a compact list (a series, search results): a small cover, the title and one or
 * two lines under it. [current] marks the book whose page this is.
 */
@Composable
fun CompactBookRow(
    cover: Any?,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    detail: String? = null,
    current: Boolean = false,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .background(if (current) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(cover, title, CoverSize.Thumb)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (current) FontWeight.SemiBold else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            detail?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
