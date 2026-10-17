package app.shelfmark.ui.library

import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import app.shelfmark.ui.icons.AutoAwesome
import app.shelfmark.data.ImportException
import app.shelfmark.ui.icons.FileUpload
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import app.shelfmark.ui.components.icon
import app.shelfmark.ui.components.colors
import app.shelfmark.ui.components.ShelfBadge
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import kotlinx.coroutines.launch
import java.io.IOException
import app.shelfmark.ui.components.label
import app.shelfmark.ui.components.ShelfOrder
import app.shelfmark.ui.components.LanguageMenuItems
import app.shelfmark.data.Shelf
import app.shelfmark.data.LibraryExport
import app.shelfmark.data.ExportFormat
import app.shelfmark.data.BookRepository
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.rounded.MoreVert
import app.shelfmark.ui.icons.FileDownload
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.compose.rememberLauncherForActivityResult
import android.net.Uri
import app.shelfmark.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import app.shelfmark.ui.icons.QrCodeScanner
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
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import app.shelfmark.ui.icons.Handshake
import app.shelfmark.ui.icons.Sell
import androidx.compose.material3.LinearProgressIndicator
import app.shelfmark.data.BookFormat
import app.shelfmark.data.foldText
import app.shelfmark.data.progress
import app.shelfmark.ui.components.formatIcon
import app.shelfmark.ui.components.formatLabel
import androidx.lifecycle.viewmodel.viewModelFactory
import app.shelfmark.data.Book
import app.shelfmark.data.coverModel
import app.shelfmark.ui.components.BookCover
import app.shelfmark.ui.components.FinishButton
import app.shelfmark.ui.components.FinishSheet
import app.shelfmark.ui.components.RatingBar
import app.shelfmark.ui.components.rememberRepository
import app.shelfmark.ui.theme.LocalStarColor

@Composable
fun LibraryScreen(
    onScan: (Shelf) -> Unit,
    onAddByHand: (Shelf) -> Unit,
    onOpen: (Long) -> Unit,
    onReleases: () -> Unit,
) {
    val repo = rememberRepository()
    val vm: LibraryViewModel = viewModel(factory = viewModelFactory { initializer { LibraryViewModel(repo, createSavedStateHandle()) } })
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val export = rememberExporter(repo, snackbar)
    val import = rememberImporter(repo, snackbar)
    val justFinished by vm.justFinished.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val movedToRead = stringResource(R.string.moved_to_read, justFinished?.title.orEmpty())

    justFinished?.let { book ->
        fun done(review: () -> Unit) {
            review()
            scope.launch { snackbar.showSnackbar(movedToRead) }
        }
        FinishSheet(
            book,
            onSave = { rating, notes -> done { vm.review(rating, notes) } },
            onSkip = { done(vm::skipReview) },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (state.totalCount > 0) {
                ExtendedFloatingActionButton(
                    onClick = { onScan(state.shelf) },
                    icon = { Icon(Icons.Rounded.QrCodeScanner, contentDescription = null) },
                    text = { Text(stringResource(R.string.scan_a_book), style = MaterialTheme.typography.labelLarge) },
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
            item(key = "header") {
                LibraryHeader(state, onAddByHand = { onAddByHand(state.shelf) }, onExport = export, onImport = import)
            }
            if (state.totalCount > 0 && !state.searching) {
                item(key = "releases") { ReleasesCard(onReleases, Modifier.padding(top = 20.dp)) }
            }
            if (state.totalCount > 0) {
                item(key = "shelves") {
                    if (state.searching) {
                        Text(
                            stringResource(if (state.filter != null && query.isBlank()) R.string.filtering_all_shelves else R.string.searching_all_shelves),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 20.dp),
                        )
                    } else {
                        ShelfTabs(state, vm::setShelf, Modifier.padding(top = 20.dp))
                    }
                }
                item(key = "search") {
                    SearchField(query, vm::setQuery, Modifier.padding(top = 12.dp))
                }
                item(key = "sort") {
                    SortRow(state.sort, vm::setSort, Modifier.padding(top = 12.dp, bottom = 8.dp))
                }
                if (state.tags.isNotEmpty() || state.lentCount > 0) {
                    item(key = "filters") {
                        FilterRow(state, vm::setFilter, Modifier.padding(bottom = 8.dp))
                    }
                }
            }
            when {
                !state.loaded -> Unit
                state.totalCount == 0 -> item(key = "empty") {
                    EmptyLibrary(
                        onScan = { onScan(Shelf.Read) },
                        onAddByHand = { onAddByHand(Shelf.Read) },
                        onImport = import,
                    )
                }
                state.books.isEmpty() && query.isBlank() && state.filter == null -> item(key = "emptyShelf") { EmptyShelf(state.shelf) }
                state.books.isEmpty() -> item(key = "none") { NoResults(query) }
                else -> items(state.books, key = { it.id }) { book ->
                    Column(Modifier.animateItem()) {
                        BookRow(book, onClick = { onOpen(book.id) }, onFinish = { vm.finish(book) })
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
private fun LibraryHeader(
    state: LibraryState,
    onAddByHand: () -> Unit,
    onExport: (ExportFormat) -> Unit,
    onImport: () -> Unit,
) {
    // With large text the title would break mid-word beside the buttons.
    val titleStyle = if (LocalDensity.current.fontScale > 1.3f) {
        MaterialTheme.typography.headlineMedium
    } else {
        MaterialTheme.typography.displaySmall
    }
    Column {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                stringResource(R.string.your_library),
                style = titleStyle,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            LibraryMenu(canExport = state.totalCount > 0, onExport = onExport, onImport = onImport, modifier = Modifier.padding(top = 2.dp))
            if (state.totalCount > 0) {
                Spacer(Modifier.width(8.dp))
                FilledTonalIconButton(onClick = onAddByHand, modifier = Modifier.padding(top = 2.dp)) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.add_by_hand))
                }
            }
        }
        if (state.totalCount > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                summaryLine(state),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun summaryLine(s: LibraryState): String {
    val read = s.shelfCounts[Shelf.Read] ?: 0
    val books = pluralStringResource(R.plurals.books_read, read, read)
    return s.averageRating?.let { stringResource(R.string.books_read_with_average, books, it) } ?: books
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    TextField(
        value = query,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.search_placeholder)) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.clear_search))
                }
            }
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        // The list filters as you type; the Search key just puts the keyboard away.
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
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
                label = { Text(stringResource(mode.label), style = MaterialTheme.typography.labelMedium) },
            )
        }
    }
}

/** The user's tags and "Lent out", each showing only those books. */
@Composable
private fun FilterRow(state: LibraryState, onChange: (String?) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.lentCount > 0) {
            val selected = state.filter == LENT_FILTER
            FilterChip(
                selected = selected,
                onClick = { onChange(if (selected) null else LENT_FILTER) },
                label = { Text(stringResource(R.string.lent_out_count, state.lentCount), style = MaterialTheme.typography.labelMedium) },
                leadingIcon = { Icon(Icons.Rounded.Handshake, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
        }
        state.tags.forEach { tag ->
            val selected = state.filter != null && foldText(state.filter) == foldText(tag)
            FilterChip(
                selected = selected,
                onClick = { onChange(if (selected) null else tag) },
                label = { Text(tag, style = MaterialTheme.typography.labelMedium) },
                leadingIcon = { Icon(Icons.Rounded.Sell, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
        }
    }
}

@Composable
private fun BookRow(book: Book, onClick: () -> Unit, onFinish: () -> Unit) {
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
            book.series?.let { series ->
                Text(
                    book.seriesNumber?.let { stringResource(R.string.series_with_number, series, it) } ?: series,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShelfBadge(book.shelf)
                Spacer(Modifier.width(10.dp))
                if (book.format != BookFormat.Paper) {
                    Icon(
                        formatIcon(book.format),
                        contentDescription = stringResource(formatLabel(book.format)),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                }
                // Stars belong to finished books; the others get theirs when they are finished.
                if (book.shelf == Shelf.Read && book.rating > 0) {
                    RatingBar(book.rating, starSize = 17.dp, spacing = 1.dp)
                } else if (book.shelf == Shelf.Read) {
                    Text(
                        stringResource(R.string.not_rated),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            if (book.ignoreForSuggestions) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.left_out_of_suggestions),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            book.lentTo?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.lent_to, it),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (book.shelf == Shelf.Reading) {
                book.progress()?.let { p ->
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(progress = { p }, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(10.dp))
                        Text("${(p * 100).toInt()} %", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(10.dp))
                FinishButton(onFinish)
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
private fun EmptyLibrary(onScan: () -> Unit, onAddByHand: () -> Unit, onImport: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StackOfBooks()
        Spacer(Modifier.height(36.dp))
        Text(
            stringResource(R.string.empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.empty_body),
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
            Text(stringResource(R.string.scan_first_book), style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onAddByHand) {
            Text(stringResource(R.string.add_by_hand), style = MaterialTheme.typography.labelLarge)
        }
        TextButton(onClick = onImport) {
            Text(stringResource(R.string.import_from_backup), style = MaterialTheme.typography.labelLarge)
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

/** Import, export and language, behind the ⋮ button. */
@Composable
private fun LibraryMenu(
    canExport: Boolean,
    onExport: (ExportFormat) -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (canExport) {
                Text(
                    stringResource(R.string.export_library),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
                ExportFormat.entries.forEach { format ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    when (format) {
                                        ExportFormat.Csv -> R.string.export_csv
                                        ExportFormat.Json -> R.string.export_json
                                        ExportFormat.Backup -> R.string.export_backup
                                    },
                                ),
                            )
                        },
                        leadingIcon = { Icon(Icons.Rounded.FileDownload, contentDescription = null) },
                        onClick = {
                            open = false
                            onExport(format)
                        },
                    )
                }
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.import_books)) },
                leadingIcon = { Icon(Icons.Rounded.FileUpload, contentDescription = null) },
                onClick = {
                    open = false
                    onImport()
                },
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            LanguageMenuItems(onChosen = { open = false })
        }
    }
}

/**
 * Asks where to save the export (Downloads, Drive, …), writes it, and reports the
 * result in [snackbar]. Returns the function to start an export.
 */
@Composable
private fun rememberExporter(repo: BookRepository, snackbar: SnackbarHostState): (ExportFormat) -> Unit {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()

    fun saveTo(format: ExportFormat, uri: Uri?) {
        if (uri == null) return
        scope.launch {
            val message = try {
                val out = context.contentResolver.openOutputStream(uri) ?: throw IOException("No output stream")
                // Finishes even if the screen goes away (rotation, opening a book) part way.
                val count = withContext(NonCancellable) { repo.export(format, out) }
                resources.getQuantityString(R.plurals.export_done, count, count)
            } catch (e: IOException) {
                resources.getString(R.string.export_failed)
            } catch (e: SecurityException) {
                resources.getString(R.string.export_failed)
            }
            snackbar.showSnackbar(message)
        }
    }

    val csv = rememberLauncherForActivityResult(CreateDocument(ExportFormat.Csv.mimeType)) { saveTo(ExportFormat.Csv, it) }
    val json = rememberLauncherForActivityResult(CreateDocument(ExportFormat.Json.mimeType)) { saveTo(ExportFormat.Json, it) }
    val zip = rememberLauncherForActivityResult(CreateDocument(ExportFormat.Backup.mimeType)) { saveTo(ExportFormat.Backup, it) }
    return { format ->
        val launcher = when (format) {
            ExportFormat.Csv -> csv
            ExportFormat.Json -> json
            ExportFormat.Backup -> zip
        }
        launcher.launch(LibraryExport.fileName(format))
    }
}

/** File types offered in the picker; the file's contents decide how it is read. */
private val ImportMimeTypes = arrayOf(
    "application/zip", "application/json", "text/csv", "text/comma-separated-values", "text/plain",
    "application/vnd.ms-excel", "application/octet-stream",
)

/**
 * Lets the user pick a JSON backup or CSV file, adds its books, and reports the
 * result in [snackbar]. Returns the function to start an import.
 */
@Composable
private fun rememberImporter(repo: BookRepository, snackbar: SnackbarHostState): () -> Unit {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val res = resources
            val message = try {
                val input = context.contentResolver.openInputStream(uri) ?: throw IOException("No input stream")
                val result = withContext(NonCancellable) { repo.import(input) }
                if (result.added == 0 && result.skipped == 0 && result.updated == 0) {
                    res.getString(R.string.import_nothing)
                } else {
                    listOfNotNull(
                        res.getQuantityString(R.plurals.import_done, result.added, result.added),
                        result.updated.takeIf { it > 0 }?.let { res.getQuantityString(R.plurals.import_updated, it, it) },
                        result.skipped.takeIf { it > 0 }?.let { res.getQuantityString(R.plurals.import_skipped, it, it) },
                    ).joinToString(" ")
                }
            } catch (e: ImportException) {
                res.getString(R.string.import_failed)
            } catch (e: IOException) {
                res.getString(R.string.import_failed)
            } catch (e: SecurityException) {
                res.getString(R.string.import_failed)
            }
            snackbar.showSnackbar(message, withDismissAction = true)
        }
    }
    return { launcher.launch(ImportMimeTypes) }
}

/** One tab per shelf, each with its icon, colour and number of books. */
@Composable
private fun ShelfTabs(state: LibraryState, onChange: (Shelf) -> Unit, modifier: Modifier = Modifier) {
    val selectedIndex = ShelfOrder.indexOf(state.shelf)
    val selectedColors = state.shelf.colors()
    TabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        indicator = { positions ->
            TabRowDefaults.SecondaryIndicator(
                Modifier.tabIndicatorOffset(positions[selectedIndex]),
                height = 3.dp,
                color = selectedColors.content,
            )
        },
    ) {
        ShelfOrder.forEach { shelf ->
            val colors = shelf.colors()
            val count = state.shelfCounts[shelf] ?: 0
            // Tab's icon-and-text layout has a fixed height that clips two-line labels at large text sizes.
            Tab(
                selected = shelf == state.shelf,
                onClick = { onChange(shelf) },
                selectedContentColor = colors.content,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Column(
                    Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BadgedBox(
                        badge = {
                            if (count > 0) {
                                Badge(containerColor = colors.container, contentColor = colors.content) { Text("$count") }
                            }
                        },
                    ) { Icon(shelf.icon, contentDescription = null) }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(shelf.label),
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** The way into "New releases for you". */
@Composable
private fun ReleasesCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.releases_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                stringResource(R.string.releases_entry_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun EmptyShelf(shelf: Shelf) {
    Text(
        stringResource(
            when (shelf) {
                Shelf.Read -> R.string.empty_shelf_read
                Shelf.Reading -> R.string.empty_shelf_reading
                Shelf.WantToRead -> R.string.empty_shelf_want
            },
        ),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, start = 12.dp, end = 12.dp),
    )
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
            stringResource(R.string.no_results, query.trim()),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.no_results_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
