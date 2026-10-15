package app.shelfmark.ui.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import app.shelfmark.ui.components.NotesPlaceholder
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
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onEdit, enabled = book != null) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit details")
                    }
                    IconButton(onClick = { confirmDelete = true }, enabled = book != null) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove from library")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val current = book
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding))
        } else {
            DetailContent(
                book = current,
                onRate = vm::setRating,
                onSaveNotes = vm::saveNotes,
                modifier = Modifier.padding(padding),
            )
        }
    }

    val current = book
    if (confirmDelete && current != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove this book?", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(
                    "\u201c${current.title}\u201d and your rating and notes will be deleted from your library.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(onDone = onBack)
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Keep it") }
            },
        )
    }
}

@Composable
private fun DetailContent(
    book: Book,
    onRate: (Int) -> Unit,
    onSaveNotes: (String) -> Unit,
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
        }

        Spacer(Modifier.height(24.dp))
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

        Spacer(Modifier.height(36.dp))
        NotesSection(book.notes, onSaveNotes)

        book.description?.let { DescriptionSection(it) }

        val facts = buildList {
            book.publishedDate?.let { add("Published" to it) }
            book.publisher?.let { add("Publisher" to it) }
            book.pageCount?.let { add("Pages" to it.toString()) }
            book.isbn?.let { add("ISBN" to it) }
            add("Added" to formatDate(book.addedAt))
        }
        Spacer(Modifier.height(36.dp))
        Text("Details", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            facts.forEach { (label, value) ->
                Row {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(104.dp),
                    )
                    Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun NotesSection(notes: String, onSave: (String) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable(notes) { mutableStateOf(notes) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Your notes", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (!editing && notes.isNotBlank()) {
            TextButton(onClick = { draft = notes; editing = true }) { Text("Edit") }
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
                placeholder = { Text(NotesPlaceholder, style = ReadingStyle) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { draft = notes; editing = false }) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onSave(draft.trim()); editing = false }) { Text("Save notes") }
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
            Text("Write down your thoughts", style = MaterialTheme.typography.labelLarge)
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
    Text("About this book", style = MaterialTheme.typography.titleLarge)
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
            Text(if (expanded) "Show less" else "Show more")
        }
    }
}
