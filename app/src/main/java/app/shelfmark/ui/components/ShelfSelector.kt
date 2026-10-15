package app.shelfmark.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import app.shelfmark.R
import app.shelfmark.data.Shelf

@get:StringRes
val Shelf.label: Int
    get() = when (this) {
        Shelf.Read -> R.string.shelf_read
        Shelf.Reading -> R.string.shelf_reading
        Shelf.WantToRead -> R.string.shelf_want
    }

/** The order shelves are shown in everywhere. */
val ShelfOrder = listOf(Shelf.Read, Shelf.Reading, Shelf.WantToRead)

/** Read · Reading · Want to read, as one segmented control. */
@Composable
fun ShelfSelector(shelf: Shelf, onChange: (Shelf) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        ShelfOrder.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == shelf,
                onClick = { onChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index, ShelfOrder.size),
                icon = {},
                label = {
                    Text(
                        stringResource(option.label),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}
