package app.shelfmark.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.shelfmark.data.Book
import app.shelfmark.data.coverModel
import app.shelfmark.ui.components.BookCover
import app.shelfmark.ui.components.RatingBar
import app.shelfmark.ui.components.rememberRepository
import app.shelfmark.ui.theme.LocalStarColor

@Composable
fun LibraryScreen(
    onScan: () -> Unit,
    onAddByHand: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    val repo = rememberRepository()
    val vm: LibraryViewModel = viewModel(factory = viewModelFactory { initializer { LibraryViewModel(repo) } })
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (state.totalCount > 0) {
                ExtendedFloatingActionButton(
                    onClick = onScan,
                    icon = { Icon(Icons.Rounded.QrCodeScanner, contentDescription = null) },
                    text = { Text("Scan a book", style = MaterialTheme.typography.labelLarge) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding() + 20.dp,
                bottom = padding.calculateBottomPadding() + 112.dp,
            ),
        ) {
            item(key = "header") { LibraryHeader(state, onAddByHand) }
            if (state.totalCount > 0) {
                item(key = "search") {
                    SearchField(query, vm::setQuery, Modifier.padding(top = 20.dp))
                }
                item(key = "sort") {
                    SortRow(state.sort, vm::setSort, Modifier.padding(top = 12.dp, bottom = 8.dp))
                }
            }
            when {
                !state.loaded -> Unit
                state.totalCount == 0 -> item(key = "empty") { EmptyLibrary(onScan, onAddByHand) }
                state.books.isEmpty() -> item(key = "none") { NoResults(query) }
                else -> items(state.books, key = { it.id }) { book ->
                    Column(Modifier.animateItem()) {
                        BookRow(book, onClick = { onOpen(book.id) })
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 88.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryHeader(state: LibraryState, onAddByHand: () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(
                "Your library",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (state.totalCount > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    summaryLine(state),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.totalCount > 0) {
            FilledTonalIconButton(onClick = onAddByHand, modifier = Modifier.padding(top = 2.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = "Add a book by hand")
            }
        }
    }
}

private fun summaryLine(s: LibraryState): String {
    val books = if (s.totalCount == 1) "1 book" else "${s.totalCount} books"
    val avg = s.averageRating?.let { ", averaging %.1f stars".format(it) } ?: ""
    return "$books read$avg"
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    TextField(
        value = query,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Search titles, authors or notes") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = container,
            unfocusedContainerColor = container,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun SortRow(sort: SortMode, onChange: (SortMode) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SortMode.entries.forEach { mode ->
            FilterChip(
                selected = mode == sort,
                onClick = { onChange(mode) },
                label = { Text(mode.label, style = MaterialTheme.typography.labelMedium) },
            )
        }
    }
}

@Composable
private fun BookRow(book: Book, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        BookCover(book.coverModel(), book.title, Modifier.width(68.dp), elevation = 3.dp)
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(
                book.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (book.authors.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    book.authors,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            if (book.rating > 0) {
                RatingBar(book.rating, starSize = 17.dp, spacing = 1.dp)
            } else {
                Text(
                    "Not rated yet",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            if (book.notes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    book.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EmptyLibrary(onScan: () -> Unit, onAddByHand: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StackOfBooks()
        Spacer(Modifier.height(36.dp))
        Text(
            "Start your reading log",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Scan the barcode on the back of a book you've read, or photograph its cover. " +
                "Rate it, write down what you thought, and it's here whenever you need to remember.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 360.dp),
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onScan,
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp),
        ) {
            Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("Scan your first book", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onAddByHand) {
            Text("Add a book by hand", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Three fanned book spines with a star bookmark: the empty-state illustration. */
@Composable
private fun StackOfBooks() {
    val tones = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondaryContainer,
    )
    val angles = listOf(-10f, 0f, 9f)
    val offsets = listOf((-44).dp, 0.dp, 44.dp)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.height(140.dp).width(200.dp)) {
        for (i in 0..2) {
            Box(
                Modifier
                    .offset(x = offsets[i], y = if (i == 1) (-6).dp else 4.dp)
                    .rotate(angles[i])
                    .size(width = 72.dp, height = 108.dp)
                    .clip(RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp, topEnd = 9.dp, bottomEnd = 9.dp))
                    .background(tones[i]),
            )
        }
        Box(
            Modifier
                .offset(x = 18.dp, y = (-36).dp)
                .size(width = 14.dp, height = 38.dp)
                .background(LocalStarColor.current, RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp)),
        )
    }
}

@Composable
private fun NoResults(query: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Nothing matches \u201c${query.trim()}\u201d",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Try part of a title, an author's name or a word from your notes.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
