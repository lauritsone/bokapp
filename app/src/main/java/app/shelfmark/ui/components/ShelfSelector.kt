package app.shelfmark.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.shelfmark.R
import app.shelfmark.data.Shelf

@get:StringRes
val Shelf.label: Int
    get() = when (this) {
        Shelf.Read -> R.string.shelf_read
        Shelf.Reading -> R.string.shelf_reading
        Shelf.WantToRead -> R.string.shelf_want
    }

val Shelf.icon: ImageVector
    get() = when (this) {
        Shelf.Read -> Icons.Rounded.TaskAlt
        Shelf.Reading -> Icons.AutoMirrored.Rounded.MenuBook
        Shelf.WantToRead -> Icons.Rounded.BookmarkBorder
    }

data class ShelfColors(val container: Color, val content: Color)

/** Each shelf has its own colour so a book's shelf reads at a glance: green, indigo, plum. */
@Composable
fun Shelf.colors(): ShelfColors {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when (this) {
        Shelf.Read -> if (dark) ShelfColors(Color(0xFF1F3A2A), Color(0xFFB5E3C4)) else ShelfColors(Color(0xFFDCEFE3), Color(0xFF1E5A36))
        Shelf.Reading -> ShelfColors(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        Shelf.WantToRead -> if (dark) ShelfColors(Color(0xFF3E2440), Color(0xFFEBC4EC)) else ShelfColors(Color(0xFFF2E3F2), Color(0xFF5B2D5E))
    }
}

/** The order shelves are shown in everywhere. */
val ShelfOrder = listOf(Shelf.Read, Shelf.Reading, Shelf.WantToRead)

/** A small coloured label with the shelf's icon and name. */
@Composable
fun ShelfBadge(shelf: Shelf, modifier: Modifier = Modifier) {
    val colors = shelf.colors()
    Row(
        modifier
            .background(colors.container, RoundedCornerShape(50))
            .padding(start = 8.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(shelf.icon, contentDescription = null, tint = colors.content, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(stringResource(shelf.label), color = colors.content, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/** Read · Reading · Want to read, as one segmented control; the chosen shelf takes its colour. */
@Composable
fun ShelfSelector(shelf: Shelf, onChange: (Shelf) -> Unit, modifier: Modifier = Modifier) {
    // Equal heights when a label wraps onto two lines at large text sizes.
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        ShelfOrder.forEachIndexed { index, option ->
            val colors = option.colors()
            SegmentedButton(
                selected = option == shelf,
                onClick = { onChange(option) },
                modifier = Modifier.fillMaxHeight(),
                shape = SegmentedButtonDefaults.itemShape(index, ShelfOrder.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = colors.container,
                    activeContentColor = colors.content,
                ),
                icon = {},
                label = {
                    Text(
                        stringResource(option.label),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                    )
                },
            )
        }
    }
}
