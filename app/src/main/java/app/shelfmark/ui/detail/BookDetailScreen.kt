package app.shelfmark.ui.detail

import androidx.compose.ui.platform.LocalConfiguration
import app.shelfmark.R
import app.shelfmark.data.Shelf
import app.shelfmark.ui.components.ShelfBadge
import app.shelfmark.ui.components.ShelfSelector
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.shelfmark.data.RefreshResult
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import app.shelfmark.ui.components.FinishButton
import app.shelfmark.ui.components.FinishSheet
import app.shelfmark.ui.components.RatingBar
import app.shelfmark.ui.components.formatDate
import app.shelfmark.ui.components.ratingLabel
import app.shelfmark.ui.components.rememberRepository
import app.shelfmark.ui.theme.ReadingStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    bookId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val repo = rememberRepository()
    val vm: BookDetailViewModel = viewModel(factory = viewModelFactory { initializer { BookDetailViewModel(repo, bookId) } })
    val book by vm.book.collectAsStateWithLifecycle()
    val askForReview by vm.askForReview.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val refreshResult by vm.refreshResult.collectAsStateWithLifecycle()
    val offline by vm.offline.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val missing by vm.missing.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(refreshResult, offline) {
        val result = refreshResult
        if (result == null && !offline) return@LaunchedEffect
        val res = context.resources
        val message = when (result) {
            is RefreshResult.Updated -> res.getQuantityString(R.plurals.refresh_updated, result.filled, result.filled)
            RefreshResult.NothingNew -> res.getString(R.string.refresh_nothing_new)
            RefreshResult.NotFound -> res.getString(R.string.refresh_not_found)
            RefreshResult.Ambiguous -> res.getString(R.string.refresh_ambiguous)
            null -> res.getString(R.string.refresh_offline)
        }
        // Not found by itself: the edit screen can search by title and let the user pick.
        val canPick = result == RefreshResult.NotFound || result == RefreshResult.Ambiguous
        val choice = snackbar.showSnackbar(
            message,
            actionLabel = if (canPick) res.getString(R.string.edit) else null,
            withDismissAction = canPick,
            duration = if (canPick) SnackbarDuration.Long else SnackbarDuration.Short,
        )
        // Cleared only now: changing the keys earlier would cancel the snackbar.
        vm.refreshShown()
        if (choice == SnackbarResult.ActionPerformed) onEdit()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = vm::refresh, enabled = book != null && !refreshing) {
                        if (refreshing) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.refresh_details))
                        }
                    }
                    IconButton(onClick = onEdit, enabled = book != null) {
                        Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.edit_details))
                    }
                    IconButton(onClick = { confirmDelete = true }, enabled = book != null) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.remove_from_library))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val current = book
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                // Briefly while loading; for good if the book was removed meanwhile.
                if (missing) {
                    Text(stringResource(R.string.book_not_found), style = MaterialTheme.typography.bodyLarge)
                } else {
                    CircularProgressIndicator()
                }
            }
        } else {
            DetailContent(
                book = current,
                onRate = vm::setRating,
                onShelf = vm::setShelf,
                onFinish = vm::finish,
                onSaveNotes = vm::saveNotes,
                onUseForSuggestions = vm::setUseForSuggestions,
                onFindDetails = vm::refresh,
                refreshing = refreshing,
                modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            )
        }
    }

    val current = book
    if (askForReview && current != null) {
        FinishSheet(current, onSave = vm::review, onSkip = vm::skipReview)
    }
    if (confirmDelete && current != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.remove_title), style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(
                    stringResource(R.string.remove_body, current.title),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(onDone = onBack)
                }) { Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.keep_it)) }
            },
        )
    }
}

@Composable
private fun DetailContent(
    book: Book,
    onRate: (Int) -> Unit,
    onShelf: (Shelf) -> Unit,
    onFinish: () -> Unit,
    onSaveNotes: (String) -> Unit,
    onUseForSuggestions: (Boolean) -> Unit,
    onFindDetails: () -> Unit,
    refreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 4.dp, bottom = 48.dp),
    ) {
        BookCover(
            model = book.coverModel(),
            title = book.title,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(164.dp),
            elevation = 14.dp,
        )
        Spacer(Modifier.height(28.dp))
        Text(
            book.title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (book.authors.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                book.authors,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            // Typed in by hand after a failed scan: look the rest up in the catalogues.
            TextButton(onClick = onFindDetails, enabled = !refreshing, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.add_missing_details), textAlign = TextAlign.Center)
            }
        }

        Spacer(Modifier.height(12.dp))
        ShelfBadge(book.shelf, Modifier.align(Alignment.CenterHorizontally))

        Spacer(Modifier.height(20.dp))
        ShelfSelector(book.shelf, onShelf)

        Spacer(Modifier.height(16.dp))
        if (book.shelf == Shelf.Read) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RatingBar(rating = book.rating, starSize = 44.dp, spacing = 4.dp, onRatingChange = onRate)
                Spacer(Modifier.height(6.dp))
                Text(
                    ratingLabel(book.rating),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            if (book.shelf == Shelf.Reading) {
                FinishButton(onFinish, large = true)
                Spacer(Modifier.height(10.dp))
            }
            Text(
                stringResource(R.string.rate_when_finished),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Notes written before the book went back to another shelf stay visible.
        if (book.shelf == Shelf.Read || book.notes.isNotBlank()) {
            Spacer(Modifier.height(36.dp))
            NotesSection(book.notes, onSaveNotes)
        }

        book.description?.let { DescriptionSection(it) }

        val locale = LocalConfiguration.current.locales[0]
        val facts = buildList {
            book.publishedDate?.let { add(R.string.published to it) }
            book.publisher?.let { add(R.string.publisher to it) }
            book.pageCount?.let { add(R.string.pages to it.toString()) }
            book.isbn?.let { add(R.string.isbn to it) }
            add(R.string.added to formatDate(book.addedAt, locale))
            book.finishedAt?.takeIf { book.shelf == Shelf.Read }?.let { add(R.string.finished_on to formatDate(it, locale)) }
        }
        Spacer(Modifier.height(36.dp))
        Text(stringResource(R.string.details), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            facts.forEach { (label, value) ->
                Row {
                    Text(
                        stringResource(label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(104.dp),
                    )
                    Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
        }
        val missing = book.description.isNullOrBlank() || book.publisher == null || book.publishedDate == null ||
            book.pageCount == null || book.isbn == null || (book.coverUrl == null && book.photoPath == null)
        if (missing) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onFindDetails,
                enabled = !refreshing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.find_missing_details), style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(Modifier.height(28.dp))
        SuggestionsToggle(use = !book.ignoreForSuggestions, onChange = onUseForSuggestions)
    }
}

/** Lets a one-off read be left out of the taste behind new-release suggestions. */
@Composable
private fun SuggestionsToggle(use: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .toggleable(value = use, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.use_for_suggestions), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.use_for_suggestions_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = use, onCheckedChange = null)
    }
}

@Composable
private fun NotesSection(notes: String, onSave: (String) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable(notes) { mutableStateOf(notes) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.your_notes), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (!editing && notes.isNotBlank()) {
            TextButton(onClick = { draft = notes; editing = true }) { Text(stringResource(R.string.edit)) }
        }
    }
    Spacer(Modifier.height(10.dp))
    when {
        editing -> {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 6,
                textStyle = ReadingStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                placeholder = { Text(stringResource(R.string.notes_placeholder), style = ReadingStyle) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { draft = notes; editing = false }) { Text(stringResource(R.string.cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onSave(draft.trim()); editing = false }) { Text(stringResource(R.string.save_notes)) }
            }
        }
        notes.isBlank() -> OutlinedButton(
            onClick = { editing = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.write_thoughts), style = MaterialTheme.typography.labelLarge)
        }
        else -> SelectionContainer {
            Text(notes, style = ReadingStyle, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun DescriptionSection(description: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val long = description.length > 320
    Spacer(Modifier.height(36.dp))
    Text(stringResource(R.string.about_book), style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(10.dp))
    Text(
        description,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = if (expanded || !long) Int.MAX_VALUE else 6,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.animateContentSize(),
    )
    if (long) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.padding(start = 0.dp)) {
            Text(stringResource(if (expanded) R.string.show_less else R.string.show_more))
        }
    }
}
