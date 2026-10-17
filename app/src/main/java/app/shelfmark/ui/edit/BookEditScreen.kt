package app.shelfmark.ui.edit

import app.shelfmark.R
import app.shelfmark.data.BookFormat
import app.shelfmark.data.Shelf
import app.shelfmark.ui.components.formatIcon
import app.shelfmark.ui.components.formatLabel
import androidx.compose.material3.FilterChip
import app.shelfmark.ui.components.ShelfSelector
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.shelfmark.data.CatalogRecord
import app.shelfmark.data.ReleaseFinder
import app.shelfmark.data.coverModel
import app.shelfmark.data.personName
import app.shelfmark.ui.components.BookCover
import app.shelfmark.ui.components.CoverShape
import app.shelfmark.ui.components.RatingBar
import app.shelfmark.ui.components.ratingLabel
import app.shelfmark.ui.components.rememberRepository
import app.shelfmark.ui.theme.ReadingStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookEditScreen(
    bookId: Long,
    isbn: String?,
    photoPath: String?,
    shelf: Shelf,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    onOpenExisting: (Long) -> Unit,
) {
    val repo = rememberRepository()
    val vm: BookEditViewModel = viewModel(
        factory = viewModelFactory { initializer { BookEditViewModel(repo, bookId, isbn, photoPath, shelf, createSavedStateHandle()) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val book = state.book
    val isNew = bookId == 0L

    LaunchedEffect(state.savedId) { state.savedId?.let(onSaved) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isNew) R.string.new_book else R.string.edit_details), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 32.dp),
        ) {
            val duplicate = state.duplicateOf
            if (duplicate != null) {
                // Something typed (or a book being edited) goes back to the form, to fix the ISBN.
                val hasForm = !isNew || book.title.isNotBlank()
                AlreadyInLibrary(
                    onOpen = { onOpenExisting(duplicate) },
                    onBack = if (hasForm) vm::dismissDuplicate else onBack,
                    backLabel = if (hasForm) R.string.back_to_form else R.string.go_back,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(108.dp)) {
                        BookCover(book.coverModel(), book.title, Modifier.fillMaxWidth(), elevation = 8.dp)
                        if (state.loading) {
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .clip(CoverShape)
                                    .background(Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                    Spacer(Modifier.width(20.dp))
                    Column(Modifier.weight(1f)) {
                        if (book.shelf == Shelf.Read) {
                            Text(stringResource(R.string.your_rating), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                ratingLabel(book.rating),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                stringResource(R.string.rate_when_finished),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (book.shelf == Shelf.Read) {
                    // Full width, so each star is big enough to hit.
                    Spacer(Modifier.height(12.dp))
                    RatingBar(
                        rating = book.rating,
                        starSize = 40.dp,
                        spacing = 6.dp,
                        onRatingChange = { r -> vm.update { it.copy(rating = r) } },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
                Spacer(Modifier.height(20.dp))
                ShelfSelector(book.shelf, onChange = { s -> vm.update { it.copy(shelf = s) } })

                if (state.loading) {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        stringResource(R.string.finding_isbn, book.isbn.orEmpty()),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.message?.let {
                    Spacer(Modifier.height(20.dp))
                    Notice(
                        when (it) {
                            EditMessage.InvalidIsbn -> stringResource(R.string.msg_invalid_isbn)
                            EditMessage.Offline -> stringResource(R.string.msg_offline)
                            is EditMessage.NotFound -> stringResource(R.string.msg_not_found, it.isbn)
                            is EditMessage.IsbnMismatch -> if (it.found.authors.isBlank()) {
                                stringResource(R.string.msg_isbn_mismatch_no_author, it.isbn, it.found.title)
                            } else {
                                stringResource(R.string.msg_isbn_mismatch, it.isbn, it.found.title, it.found.authors)
                            }
                            EditMessage.NoTitleMatches -> stringResource(R.string.msg_no_title_matches)
                        },
                        action = (it as? EditMessage.IsbnMismatch)?.let { stringResource(R.string.use_this_book) to vm::useMismatch },
                    )
                }

                Spacer(Modifier.height(28.dp))
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(
                        value = book.title,
                        onValueChange = { v -> vm.update { it.copy(title = v) } },
                        label = { Text(stringResource(R.string.title)) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyLarge,
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next,
                        ),
                    )
                    OutlinedTextField(
                        value = book.authors,
                        onValueChange = { v -> vm.update { it.copy(authors = v) } },
                        label = { Text(stringResource(R.string.author)) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyLarge,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next,
                        ),
                    )
                    OutlinedButton(
                        onClick = vm::searchTitle,
                        enabled = book.title.isNotBlank() && !state.searching && !state.loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.searching) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        } else {
                            Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.find_in_catalogue))
                    }
                    state.results?.takeIf { it.isNotEmpty() }?.let { results ->
                        SearchResults(results, onPick = vm::pick, onClose = vm::closeResults)
                    }
                    OutlinedTextField(
                        value = book.isbn.orEmpty(),
                        onValueChange = { v -> vm.update { it.copy(isbn = v.take(17).ifBlank { null }) } },
                        label = { Text(stringResource(R.string.isbn)) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyLarge,
                        singleLine = true,
                        supportingText = { Text(stringResource(R.string.isbn_supporting)) },
                        trailingIcon = {
                            IconButton(
                                onClick = vm::lookupIsbn,
                                enabled = !state.loading && !book.isbn.isNullOrBlank(),
                            ) { Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.look_up_isbn)) }
                        },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { vm.lookupIsbn() }),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = book.publishedDate.orEmpty(),
                            onValueChange = { v -> vm.update { it.copy(publishedDate = v.ifBlank { null }) } },
                            label = { Text(stringResource(R.string.published)) },
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.bodyLarge,
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = book.pageCount?.toString().orEmpty(),
                            onValueChange = { v ->
                                vm.update { it.copy(pageCount = v.filter(Char::isDigit).take(5).toIntOrNull()) }
                            },
                            label = { Text(stringResource(R.string.pages)) },
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.bodyLarge,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = book.series.orEmpty(),
                            onValueChange = { v -> vm.update { it.copy(series = v.ifEmpty { null }) } },
                            label = { Text(stringResource(R.string.series)) },
                            modifier = Modifier.weight(2f),
                            textStyle = MaterialTheme.typography.bodyLarge,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        )
                        OutlinedTextField(
                            value = book.seriesNumber.orEmpty(),
                            onValueChange = { v ->
                                vm.update { it.copy(seriesNumber = v.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(5).ifEmpty { null }) }
                            },
                            label = { Text(stringResource(R.string.series_number)) },
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.bodyLarge,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                    FormatSelector(book.format, onChange = { f -> vm.update { it.copy(format = f) } })
                    OutlinedTextField(
                        value = book.tags.orEmpty(),
                        onValueChange = { v -> vm.update { it.copy(tags = v.ifEmpty { null }) } },
                        label = { Text(stringResource(R.string.tags)) },
                        supportingText = { Text(stringResource(R.string.tags_supporting)) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyLarge,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                    if (book.shelf == Shelf.Read || book.notes.isNotBlank()) OutlinedTextField(
                        value = book.notes,
                        onValueChange = { v -> vm.update { it.copy(notes = v) } },
                        label = { Text(stringResource(R.string.your_notes)) },
                        placeholder = { Text(stringResource(R.string.notes_placeholder), style = ReadingStyle) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = ReadingStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                        minLines = 6,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                }

                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = vm::save,
                    enabled = book.title.isNotBlank() && !state.saving && !state.loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Text(stringResource(if (isNew) R.string.save_to_library else R.string.save_changes), style = MaterialTheme.typography.labelLarge)
                }
                if (book.title.isBlank() && !state.loading) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.add_title_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }
        }
    }
}

/** Paper, ebook or audiobook. */
@Composable
private fun FormatSelector(format: BookFormat, onChange: (BookFormat) -> Unit) {
    Column {
        Text(
            stringResource(R.string.format),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BookFormat.entries.forEach { f ->
                FilterChip(
                    selected = f == format,
                    onClick = { onChange(f) },
                    label = { Text(stringResource(formatLabel(f))) },
                    leadingIcon = { Icon(formatIcon(f), contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
    }
}

@Composable
private fun Notice(text: String, action: Pair<String, () -> Unit>? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Rounded.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            action?.let { (label, onClick) ->
                TextButton(onClick = onClick) { Text(label) }
            }
        }
    }
}

/** Editions found by title; tapping one fills in the form. */
@Composable
private fun SearchResults(results: List<CatalogRecord>, onPick: (CatalogRecord) -> Unit, onClose: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(vertical = 8.dp),
    ) {
        Text(
            stringResource(R.string.pick_edition),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        results.forEach { r ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(r) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BookCover(r.coverUrl, r.title, Modifier.width(40.dp), elevation = 2.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(ReleaseFinder.displayTitle(r.title), style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                    r.creators.firstOrNull()?.let {
                        Text(personName(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    listOfNotNull(r.publisher, r.year?.toString(), r.isbn).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        TextButton(onClick = onClose, modifier = Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.close)) }
    }
}

@Composable
private fun AlreadyInLibrary(onOpen: () -> Unit, onBack: () -> Unit, backLabel: Int) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.already_logged_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.already_logged_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpen, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(stringResource(R.string.open_book), style = MaterialTheme.typography.labelLarge)
        }
        TextButton(onClick = onBack) {
            Text(stringResource(backLabel), style = MaterialTheme.typography.labelLarge)
        }
    }
}
