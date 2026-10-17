package app.shelfmark.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.shelfmark.R
import app.shelfmark.data.Book
import app.shelfmark.data.Shelf
import app.shelfmark.data.progress
import app.shelfmark.ui.components.BookCover
import app.shelfmark.ui.components.formatDate
import app.shelfmark.data.coverModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Page and percentage for a book on the Reading shelf, with a way to update the page. */
@Composable
fun ProgressCard(book: Book, onSetPage: (Int?) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val progress = book.progress()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.update_progress)) { editing = true }
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.progress), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            progress?.let {
                Text("${(it * 100).toInt()} %", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(progress = { progress ?: 0f }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Text(
            when {
                book.currentPage == null -> stringResource(R.string.progress_not_started)
                book.pageCount != null -> stringResource(R.string.page_of, book.currentPage, book.pageCount)
                else -> stringResource(R.string.page_n, book.currentPage)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (editing) {
        PageDialog(book, onDismiss = { editing = false }, onSave = { onSetPage(it); editing = false })
    }
}

@Composable
private fun PageDialog(book: Book, onDismiss: () -> Unit, onSave: (Int?) -> Unit) {
    var text by rememberSaveable { mutableStateOf(book.currentPage?.toString().orEmpty()) }
    val total = book.pageCount
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_progress)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.filter(Char::isDigit).take(5) },
                label = { Text(stringResource(R.string.current_page)) },
                suffix = total?.let { { Text(stringResource(R.string.of_pages, it)) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text.toIntOrNull()) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Who has the book, with a button for when it comes back; or a button to lend it out. */
@Composable
fun LendingSection(book: Book, onLend: (String) -> Unit, onReturned: () -> Unit) {
    var asking by rememberSaveable { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    if (book.lentTo != null) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.tertiaryContainer)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Handshake, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(12.dp))
            Text(
                book.lentAt?.let { stringResource(R.string.lent_to_since, book.lentTo, formatDate(it, locale)) }
                    ?: stringResource(R.string.lent_to, book.lentTo),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onReturned) { Text(stringResource(R.string.returned)) }
        }
    } else {
        OutlinedButton(onClick = { asking = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Rounded.Handshake, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.lend_out), style = MaterialTheme.typography.labelLarge)
        }
    }
    if (asking) {
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(R.string.lend_out)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.lend_to_whom)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
            },
            confirmButton = {
                TextButton(onClick = { onLend(name); asking = false }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** The books of the series in the library, and a lookup of the one after this. */
@Composable
fun SeriesSection(
    book: Book,
    books: List<Book>,
    next: NextState,
    onOpen: (Long) -> Unit,
    onFindNext: () -> Unit,
    onAddNext: () -> Unit,
) {
    val series = book.series ?: return
    Spacer(Modifier.height(36.dp))
    Text(stringResource(R.string.series_title, series), style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(10.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        books.forEach { b ->
            val current = b.id == book.id
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (current) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
                    .clickable(enabled = !current) { onOpen(b.id) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BookCover(b.coverModel(), b.title, Modifier.width(36.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        listOfNotNull(b.seriesNumber?.let { "#$it" }, b.title).joinToString("  "),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (current) FontWeight.SemiBold else null,
                    )
                    Text(
                        stringResource(shelfName(b.shelf)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    when (next) {
        is NextState.Found -> {
            val owned = next.next.owned
            Text(
                stringResource(R.string.next_in_series, next.next.title),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(8.dp))
            if (owned != null) {
                OutlinedButton(onClick = { onOpen(owned.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.open_book))
                }
            } else {
                OutlinedButton(onClick = onAddNext, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.add_to_want))
                }
            }
        }
        else -> {
            OutlinedButton(onClick = onFindNext, enabled = next != NextState.Searching, modifier = Modifier.fillMaxWidth()) {
                if (next == NextState.Searching) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Text(stringResource(R.string.find_next_in_series))
            }
            when (next) {
                NextState.Unknown -> NoteText(stringResource(R.string.next_unknown))
                NextState.Offline -> NoteText(stringResource(R.string.refresh_offline))
                else -> Unit
            }
        }
    }
}

@Composable
private fun NoteText(text: String) {
    Spacer(Modifier.height(6.dp))
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun shelfName(shelf: Shelf) = when (shelf) {
    Shelf.Read -> R.string.shelf_read
    Shelf.Reading -> R.string.shelf_reading
    Shelf.WantToRead -> R.string.shelf_want
}

/**
 * A date picker for a reading date. The picker works in UTC days; the date is stored as
 * local noon, so it stays the same day in any time zone close to the user's.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingDateDialog(initial: Long?, onDismiss: () -> Unit, onPick: (Long?) -> Unit) {
    val zone = ZoneId.systemDefault()
    val initialDay = (initial?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: LocalDate.now())
        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDay,
        selectableDates = object : androidx.compose.material3.SelectableDates {
            // No reading dates in the future.
            override fun isSelectableDate(utcTimeMillis: Long) =
                utcTimeMillis <= LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val day = state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                onPick(day?.atTime(LocalTime.NOON)?.atZone(zone)?.toInstant()?.toEpochMilli())
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (initial != null) TextButton(onClick = { onPick(null) }) { Text(stringResource(R.string.clear_date)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    ) { DatePicker(state = state) }
}
