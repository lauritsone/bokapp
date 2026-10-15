package app.shelfmark.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.shelfmark.data.Book
import app.shelfmark.data.BookRepository
import app.shelfmark.data.Isbn
import app.shelfmark.data.Shelf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/** Notices shown above the form; turned into text by the screen. */
sealed interface EditMessage {
    data object InvalidIsbn : EditMessage
    data object Offline : EditMessage
    data class NotFound(val isbn: String) : EditMessage
}

data class EditState(
    val book: Book = Book(),
    val loading: Boolean = false,
    val message: EditMessage? = null,
    val duplicateOf: Long? = null,
    val saving: Boolean = false,
    val savedId: Long? = null,
)

class BookEditViewModel(
    private val repo: BookRepository,
    bookId: Long,
    isbn: String?,
    photoPath: String?,
    shelf: Shelf,
) : ViewModel() {

    private val _state = MutableStateFlow(EditState(book = Book(isbn = isbn, photoPath = photoPath, shelf = shelf)))
    val state: StateFlow<EditState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            when {
                bookId != 0L -> repo.get(bookId)?.let { existing -> _state.update { it.copy(book = existing) } }
                isbn != null -> fetch(isbn)
            }
        }
    }

    fun update(transform: (Book) -> Book) = _state.update { it.copy(book = transform(it.book)) }

    /** Looks up whatever is in the ISBN field and fills in the details. */
    fun lookupIsbn() {
        val raw = _state.value.book.isbn.orEmpty()
        val isbn = Isbn.normalize(raw)
        if (isbn == null) {
            _state.update { it.copy(message = EditMessage.InvalidIsbn) }
            return
        }
        viewModelScope.launch { fetch(isbn) }
    }

    private suspend fun fetch(isbn: String) {
        _state.update { it.copy(loading = true, message = null, book = it.book.copy(isbn = isbn)) }

        val existing = repo.findByIsbn(isbn)
        if (existing != null && existing.id != _state.value.book.id) {
            _state.update { it.copy(loading = false, duplicateOf = existing.id) }
            return
        }

        val info = try {
            repo.lookup(isbn)
        } catch (e: IOException) {
            _state.update {
                it.copy(
                    loading = false,
                    message = EditMessage.Offline,
                )
            }
            return
        }

        _state.update { s ->
            if (info == null) {
                s.copy(
                    loading = false,
                    message = EditMessage.NotFound(isbn),
                )
            } else {
                s.copy(
                    loading = false,
                    book = s.book.copy(
                        title = info.title,
                        authors = info.authors,
                        publisher = info.publisher,
                        publishedDate = info.publishedDate,
                        pageCount = info.pageCount,
                        description = info.description,
                        coverUrl = info.coverUrl ?: s.book.coverUrl,
                    ),
                )
            }
        }
    }

    fun save() {
        val book = _state.value.book
        if (book.title.isBlank() || _state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val id = repo.save(
                book.copy(
                    title = book.title.trim(),
                    authors = book.authors.trim(),
                    notes = book.notes.trim(),
                    isbn = book.isbn?.let { Isbn.normalize(it) ?: it.trim().ifBlank { null } },
                ),
            )
            _state.update { it.copy(saving = false, savedId = id) }
        }
    }
}
