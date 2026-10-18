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
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import app.shelfmark.ui.components.BarSpinner
import app.shelfmark.ui.components.BookCover
import app.shelfmark.ui.components.ButtonSpinner
import app.shelfmark.ui.components.CompactBookRow
import app.shelfmark.ui.components.CoverSize
import app.shelfmark.ui.components.EmptyState
import app.shelfmark.ui.components.InfoCard
import app.shelfmark.ui.components.RatingPicker
import app.shelfmark.ui.components.ShelfmarkTopBar
import app.shelfmark.ui.icons.TaskAlt
import app.shelfmark.ui.scan.isbnInput
import app.shelfmark.ui.scan.isbnLooksWrong
import app.shelfmark.ui.theme.Dimens
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import app.shelfmark.ui.components.CoverShape
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
    val focusManager = LocalFocusManager.current

    LaunchedEffect(state.savedId) { state.savedId?.let(onSaved) }
    // Leaving with unsaved work asks first, from the back arrow and the system Back alike.
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val leave = { if (state.unsaved) confirmDiscard = true else onBack() }
    BackHandler(enabled = state.unsaved) { confirmDiscard = true }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.discard_title)) },
            text = { Text(stringResource(R.string.discard_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onBack()
                }) { Text(stringResource(R.string.discard), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.keep_editing)) }
            },
        )
    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val fullWidth = Modifier.fillMaxWidth()
    val nextField = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ShelfmarkTopBar(
                title = stringResource(if (isNew) R.string.new_book else R.string.edit_details),
                onBack = leave,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                // Inside the scroll, so the form scrolls under the top bar and the navigation bar.
                .padding(padding)
                .padding(horizontal = Dimens.gutter)
                .padding(top = Dimens.topGap, bottom = Dimens.bottomGap),
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
                // The same order as the book's page: cover, shelf, rating.
                Box(Modifier.align(Alignment.CenterHorizontally)) {
                    BookCover(book.coverModel(), book.title, CoverSize.Medium)
                    if (state.loading) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .clip(CoverShape)
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            BarSpinner(color = Color.White)
                        }
                    }
                }
                if (state.loading) {
                    Spacer(Modifier.height(Dimens.headerGap))
                    Text(
                        stringResource(R.string.finding_isbn, book.isbn.orEmpty()),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(20.dp))
                ShelfSelector(book.shelf, onChange = { s -> vm.update { it.copy(shelf = s) } })
                Spacer(Modifier.height(Dimens.headerGap))
                if (book.shelf == Shelf.Read) {
                    InfoCard { RatingPicker(book.rating, onRatingChange = { r -> vm.update { it.copy(rating = r) } }) }
                } else {
                    Text(
                        stringResource(R.string.rate_when_finished),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                state.message?.let {
                    Spacer(Modifier.height(20.dp))
                    Notice(
                        when (it) {
                            EditMessage.InvalidIsbn -> stringResource(R.string.msg_invalid_isbn)
                            is EditMessage.Offline -> stringResource(R.string.msg_offline)
                            is EditMessage.NotFound -> stringResource(R.string.msg_not_found, it.isbn)
                            is EditMessage.IsbnMismatch -> if (it.found.authors.isBlank()) {
                                stringResource(R.string.msg_isbn_mismatch_no_author, it.isbn, it.found.title)
                            } else {
                                stringResource(R.string.msg_isbn_mismatch, it.isbn, it.found.title, it.found.authors)
                            }
                            EditMessage.NoTitleMatches -> stringResource(R.string.msg_no_title_matches)
                        },
                        action = when (it) {
                            is EditMessage.IsbnMismatch -> stringResource(R.string.use_this_book) to vm::useMismatch
                            is EditMessage.Offline -> stringResource(R.string.try_again) to vm::retry
                            else -> null
                        },
                    )
                }

                Spacer(Modifier.height(28.dp))
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(
                        value = book.title,
                        onValueChange = { v -> vm.update { it.copy(title = v) } },
                        label = { Text(stringResource(R.string.title)) },
                        modifier = fullWidth,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next,
                        ),
                        keyboardActions = nextField,
                    )
                    OutlinedTextField(
                        value = book.authors,
                        onValueChange = { v -> vm.update { it.copy(authors = v) } },
                        label = { Text(stringResource(R.string.author)) },
                        modifier = fullWidth,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next,
                        ),
                        keyboardActions = nextField,
                    )
                    OutlinedButton(
                        onClick = vm::searchTitle,
                        enabled = book.title.isNotBlank() && !state.searching && !state.loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Dimens.buttonHeight),
                    ) {
                        if (state.searching) {
                            ButtonSpinner()
                        } else {
                            Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.find_in_catalogue))
                    }
                    state.results?.takeIf { it.isNotEmpty() }?.let { results ->
                        SearchResults(results, onPick = vm::pick, onClose = vm::closeResults)
                    }
                    // Checked as it is typed, like in the scanner's ISBN dialog.
                    val isbnWrong = isbnLooksWrong(book.isbn.orEmpty())
                    OutlinedTextField(
                        value = book.isbn.orEmpty(),
                        onValueChange = { v -> vm.update { it.copy(isbn = isbnInput(v).ifBlank { null }) } },
                        label = { Text(stringResource(R.string.isbn)) },
                        modifier = fullWidth,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        singleLine = true,
                        isError = isbnWrong,
                        supportingText = { Text(stringResource(if (isbnWrong) R.string.isbn_invalid else R.string.isbn_supporting)) },
                        trailingIcon = {
                            IconButton(
                                onClick = vm::lookupIsbn,
                                enabled = !state.loading && !book.isbn.isNullOrBlank(),
                            ) { Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.look_up_isbn)) }
                        },
                        // Search looks the book up, which fills in most of the fields below.
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
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = nextField,
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
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            keyboardActions = nextField,
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
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                            keyboardActions = nextField,
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
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                            keyboardActions = nextField,
                        )
                    }
                    FormatSelector(book.format, onChange = { f -> vm.update { it.copy(format = f) } })
                    val notesShown = book.shelf == Shelf.Read || book.notes.isNotBlank()
                    OutlinedTextField(
                        value = book.tags.orEmpty(),
                        onValueChange = { v -> vm.update { it.copy(tags = v.ifEmpty { null }) } },
                        label = { Text(stringResource(R.string.tags)) },
                        supportingText = { Text(stringResource(R.string.tags_supporting)) },
                        modifier = fullWidth,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        singleLine = true,
                        // Tags are often lower case ("book club"); the last field closes the keyboard.
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            imeAction = if (notesShown) ImeAction.Next else ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) },
                            onDone = { focusManager.clearFocus() },
                        ),
                    )
                    if (notesShown) OutlinedTextField(
                        value = book.notes,
                        onValueChange = { v -> vm.update { it.copy(notes = v) } },
                        label = { Text(stringResource(R.string.your_notes)) },
                        placeholder = { Text(stringResource(R.string.notes_placeholder), style = ReadingStyle) },
                        modifier = fullWidth,
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
                        .height(Dimens.buttonHeight),
                ) {
                    Text(stringResource(if (isNew) R.string.save_to_library else R.string.save_changes))
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
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(Dimens.cardPadding),
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
    InfoCard(padding = false) {
        Text(
            stringResource(R.string.pick_edition),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = Dimens.cardPadding, end = Dimens.cardPadding, top = Dimens.cardPadding, bottom = 4.dp),
        )
        Column(Modifier.padding(horizontal = 8.dp)) {
            results.forEach { r ->
                CompactBookRow(
                    cover = r.coverUrl,
                    title = ReleaseFinder.displayTitle(r.title),
                    subtitle = r.creators.firstOrNull()?.let(::personName),
                    detail = listOfNotNull(r.publisher, r.year?.toString(), r.isbn).joinToString(" · ").ifEmpty { null },
                    onClick = { onPick(r) },
                )
            }
        }
        TextButton(onClick = onClose, modifier = Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.close)) }
    }
}

@Composable
private fun AlreadyInLibrary(onOpen: () -> Unit, onBack: () -> Unit, backLabel: Int) {
    EmptyState(
        title = stringResource(R.string.already_logged_title),
        body = stringResource(R.string.already_logged_body),
        icon = Icons.Rounded.TaskAlt,
    ) {
        Button(onClick = onOpen) { Text(stringResource(R.string.open_book)) }
        TextButton(onClick = onBack) { Text(stringResource(backLabel)) }
    }
}
