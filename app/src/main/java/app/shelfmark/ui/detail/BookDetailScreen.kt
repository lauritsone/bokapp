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
import app.shelfmark.ui.icons.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import app.shelfmark.data.RefreshResult
import app.shelfmark.data.tagList
import app.shelfmark.ui.components.formatLabel
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.clickable
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
import app.shelfmark.ui.components.BarSpinner
import app.shelfmark.ui.components.BookCover
import app.shelfmark.ui.components.ButtonSpinner
import app.shelfmark.ui.components.CoverSize
import app.shelfmark.ui.components.EmptyState
import app.shelfmark.ui.components.InfoCard
import app.shelfmark.ui.components.LoadingState
import app.shelfmark.ui.components.RatingPicker
import app.shelfmark.ui.components.SectionTitle
import app.shelfmark.ui.components.ShelfmarkTopBar
import app.shelfmark.ui.theme.Dimens
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import app.shelfmark.ui.components.FinishButton
import app.shelfmark.ui.components.FinishSheet
import app.shelfmark.ui.components.formatDate
import app.shelfmark.ui.components.rememberRepository
import app.shelfmark.ui.theme.ReadingStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    bookId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenBook: (Long) -> Unit,
) {
    val repo = rememberRepository()
    val vm: BookDetailViewModel = viewModel(factory = viewModelFactory { initializer { BookDetailViewModel(repo, bookId) } })
    val book by vm.book.collectAsStateWithLifecycle()
    val askForReview by vm.askForReview.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val refreshResult by vm.refreshResult.collectAsStateWithLifecycle()
    val offline by vm.offline.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val missing by vm.missing.collectAsStateWithLifecycle()
    val seriesBooks by vm.seriesBooks.collectAsStateWithLifecycle()
    val next by vm.next.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val scroll = rememberScrollState()
    // The title moves into the bar once the cover and title have scrolled away.
    val coverHeight = with(LocalDensity.current) { 300.dp.toPx() }
    val coverGone by remember(coverHeight) { derivedStateOf { scroll.value > coverHeight } }

    LaunchedEffect(refreshResult, offline) {
        val result = refreshResult
        if (result == null && !offline) return@LaunchedEffect
        val res = resources
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
            actionLabel = if (canPick) res.getString(R.string.edit_details) else null,
            withDismissAction = canPick,
            duration = if (canPick) SnackbarDuration.Long else SnackbarDuration.Short,
        )
        // Cleared only now: changing the keys earlier would cancel the snackbar.
        vm.refreshShown()
        if (choice == SnackbarResult.ActionPerformed) onEdit()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            ShelfmarkTopBar(
                title = book?.title?.takeIf { coverGone },
                onBack = onBack,
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = vm::refresh, enabled = book != null && !refreshing) {
                        if (refreshing) {
                            BarSpinner()
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.refresh_details))
                        }
                    }
                    IconButton(onClick = onEdit, enabled = book != null) {
                        Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.edit_details))
                    }
                    // Removing is rare and can't be taken back by a tap, so it sits behind the ⋮ menu.
                    Box {
                        IconButton(onClick = { menuOpen = true }, enabled = book != null) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.remove_from_library)) },
                                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    confirmDelete = true
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        val current = book
        if (current == null) {
            // Briefly while loading; for good if the book was removed meanwhile.
            if (missing) {
                EmptyState(
                    title = null,
                    body = stringResource(R.string.book_not_found),
                    icon = Icons.Rounded.Info,
                    modifier = Modifier.padding(padding).padding(horizontal = Dimens.gutter),
                ) {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.go_back)) }
                }
            } else {
                LoadingState(Modifier.padding(padding))
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
                // Kept between redraws, so the page can skip redrawing when nothing changed.
                extras = remember(seriesBooks, next, onOpenBook) {
                    DetailExtras(
                        seriesBooks = seriesBooks,
                        next = next,
                        onOpenBook = onOpenBook,
                        onFindNext = vm::findNext,
                        onAddNext = { vm.addNext(onOpenBook) },
                        onSetPage = vm::setProgress,
                        onSetDates = vm::setDates,
                        onLend = vm::lend,
                        onReturned = vm::returned,
                        onReadAgain = vm::readAgain,
                    )
                },
                scroll = scroll,
                contentPadding = padding,
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

/** Actions and data for the reading-log sections of the page. */
private class DetailExtras(
    val seriesBooks: List<Book>,
    val next: NextState,
    val onOpenBook: (Long) -> Unit,
    val onFindNext: () -> Unit,
    val onAddNext: () -> Unit,
    val onSetPage: (Int?) -> Unit,
    val onSetDates: (Long?, Long?) -> Unit,
    val onLend: (String) -> Unit,
    val onReturned: () -> Unit,
    val onReadAgain: () -> Unit,
)

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
    extras: DetailExtras,
    scroll: ScrollState,
    contentPadding: PaddingValues,
) {
    var editingDate by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        Modifier
            .fillMaxSize()
            .consumeWindowInsets(contentPadding)
            .imePadding()
            .verticalScroll(scroll)
            // Inside the scroll, so the page scrolls under the top bar and the navigation bar.
            .padding(contentPadding)
            .padding(horizontal = Dimens.gutter)
            .padding(top = Dimens.topGap, bottom = Dimens.bottomGap),
    ) {
        BookCover(
            model = book.coverModel(),
            title = book.title,
            size = CoverSize.Hero,
            modifier = Modifier.align(Alignment.CenterHorizontally),
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
        }

        if (book.series != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                book.seriesNumber?.let { stringResource(R.string.series_with_number, book.series, it) } ?: book.series,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(12.dp))
        ShelfBadge(book.shelf, Modifier.align(Alignment.CenterHorizontally))

        Spacer(Modifier.height(20.dp))
        ShelfSelector(book.shelf, onShelf)

        Spacer(Modifier.height(Dimens.headerGap))
        if (book.shelf == Shelf.Read) {
            InfoCard { RatingPicker(book.rating, onRatingChange = onRate) }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = extras.onReadAgain, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.read_again))
            }
        } else {
            if (book.shelf == Shelf.Reading) {
                ProgressCard(book, extras.onSetPage)
                Spacer(Modifier.height(Dimens.headerGap))
                FinishButton(onFinish, large = true)
                Spacer(Modifier.height(Dimens.headerGap))
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
            Spacer(Modifier.height(Dimens.sectionGap))
            NotesSection(book.notes, onSaveNotes)
        }

        book.description?.let { DescriptionSection(it) }

        SeriesSection(book, extras.seriesBooks, extras.next, extras.onOpenBook, extras.onFindNext, extras.onAddNext)

        val locale = LocalConfiguration.current.locales[0]
        val facts = buildList {
            book.publishedDate?.let { add(R.string.published to it) }
            book.publisher?.let { add(R.string.publisher to it) }
            book.pageCount?.let { add(R.string.pages to it.toString()) }
            book.isbn?.let { add(R.string.isbn to it) }
            add(R.string.format to stringResource(formatLabel(book.format)))
            if (book.rereads > 0) add(R.string.times_read to pluralStringResource(R.plurals.read_times, book.rereads + 1, book.rereads + 1))
            book.tagList().takeIf { it.isNotEmpty() }?.let { add(R.string.tags to it.joinToString(", ")) }
            add(R.string.added to formatDate(book.addedAt, locale))
        }
        // Reading dates can be set or corrected; books added straight to Read have none.
        val dates = buildList {
            if (book.shelf != Shelf.WantToRead) add(Triple("started", R.string.started_on, book.startedAt))
            if (book.shelf == Shelf.Read) add(Triple("finished", R.string.finished_on, book.finishedAt))
        }
        Spacer(Modifier.height(Dimens.sectionGap))
        SectionTitle(stringResource(R.string.details))
        // With large text a label beside its value would wrap badly; it goes above it instead.
        val stacked = LocalDensity.current.fontScale > 1.3f
        Column {
            facts.forEach { (label, value) ->
                FactRow(stringResource(label), stacked) {
                    Text(value, style = MaterialTheme.typography.bodyMedium)
                }
            }
            dates.forEach { (key, label, value) ->
                FactRow(
                    stringResource(label),
                    stacked,
                    modifier = Modifier.clickable(onClickLabel = stringResource(R.string.change_date)) { editingDate = key },
                    trailing = {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                ) {
                    Text(
                        value?.let { formatDate(it, locale) } ?: stringResource(R.string.add_date),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (value == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        editingDate?.let { key ->
            ReadingDateDialog(
                initial = if (key == "started") book.startedAt else book.finishedAt,
                onDismiss = { editingDate = null },
                onPick = { picked ->
                    editingDate = null
                    if (key == "started") extras.onSetDates(picked, book.finishedAt) else extras.onSetDates(book.startedAt, picked)
                },
            )
        }
        // Typed in by hand after a failed scan, say: the catalogues may have the rest.
        val missing = book.authors.isBlank() || book.description.isNullOrBlank() || book.publisher == null ||
            book.publishedDate == null || book.pageCount == null || book.isbn == null ||
            (book.coverUrl == null && book.photoPath == null)
        if (missing) {
            Spacer(Modifier.height(Dimens.headerGap))
            OutlinedButton(
                onClick = onFindDetails,
                enabled = !refreshing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.buttonHeight),
            ) {
                if (refreshing) ButtonSpinner() else Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.find_missing_details))
            }
        }

        Spacer(Modifier.height(Dimens.sectionGap))
        LendingSection(book, extras.onLend, extras.onReturned)

        Spacer(Modifier.height(Dimens.headerGap))
        SuggestionsToggle(use = !book.ignoreForSuggestions, onChange = onUseForSuggestions)
    }
}

/** Lets a one-off read be left out of the taste behind new-release suggestions. */
@Composable
private fun SuggestionsToggle(use: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .toggleable(value = use, role = Role.Switch, onValueChange = onChange)
            .padding(Dimens.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.use_for_suggestions), style = MaterialTheme.typography.titleSmall)
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

    SectionTitle(
        stringResource(R.string.your_notes),
        action = if (!editing && notes.isNotBlank()) {
            { TextButton(onClick = { draft = notes; editing = true }) { Text(stringResource(R.string.edit)) } }
        } else {
            null
        },
    )
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
                .height(Dimens.buttonHeight),
        ) {
            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.write_thoughts))
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
    Spacer(Modifier.height(Dimens.sectionGap))
    SectionTitle(stringResource(R.string.about_book))
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

/** A label and its value in the Details section; [stacked] puts the label above, for large text. */
@Composable
private fun FactRow(
    label: String,
    stacked: Boolean,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    value: @Composable () -> Unit,
) {
    val labelText = @Composable {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Row(
        modifier
            .fillMaxWidth()
            // Tall enough to tap, for the dates that can be changed.
            .heightIn(min = if (trailing != null) 48.dp else 32.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (stacked) {
            Column(Modifier.weight(1f)) {
                labelText()
                value()
            }
        } else {
            Box(Modifier.width(112.dp)) { labelText() }
            Box(Modifier.weight(1f)) { value() }
        }
        trailing?.invoke()
    }
}
