package app.shelfmark.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.shelfmark.R
import app.shelfmark.data.Book
import app.shelfmark.ui.theme.Dimens
import app.shelfmark.ui.theme.ReadingStyle

/** The button on books being read: moves the book to Read. */
@Composable
fun FinishButton(onClick: () -> Unit, modifier: Modifier = Modifier, large: Boolean = false) {
    FilledTonalButton(
        onClick = onClick,
        // The small one keeps the standard height, so it stays easy to hit.
        modifier = if (large) modifier.fillMaxWidth().height(Dimens.buttonHeight) else modifier,
        contentPadding = if (large) ButtonDefaults.ContentPadding else ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.mark_finished))
    }
}

/**
 * Asked right after a book is finished: how many stars and what you thought.
 * Skipping keeps the book on Read without a rating; both can be added later.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinishSheet(
    book: Book,
    onSave: (rating: Int, notes: String) -> Unit,
    onSkip: () -> Unit,
) {
    var rating by rememberSaveable(book.id) { mutableIntStateOf(book.rating) }
    var notes by rememberSaveable(book.id) { mutableStateOf(book.notes) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onSkip, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.gutter)
                .padding(bottom = Dimens.bottomGap)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.finished_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                book.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            RatingPicker(rating, onRatingChange = { rating = it })
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 6,
                label = { Text(stringResource(R.string.your_notes)) },
                placeholder = { Text(stringResource(R.string.notes_placeholder), style = ReadingStyle) },
                textStyle = ReadingStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onSkip) { Text(stringResource(R.string.skip_for_now)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onSave(rating, notes) }) { Text(stringResource(R.string.save)) }
            }
        }
    }
}
