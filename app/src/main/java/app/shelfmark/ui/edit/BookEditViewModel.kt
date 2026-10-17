package app.shelfmark.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.shelfmark.data.Book
import app.shelfmark.data.BookInfo
import app.shelfmark.data.BookRepository
import app.shelfmark.data.CatalogRecord
import app.shelfmark.data.ReleaseFinder
import app.shelfmark.data.Isbn
import app.shelfmark.data.Shelf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import org.json.JSONException

/** Notices shown above the form; turned into text by the screen. */
sealed interface EditMessage {
    data object InvalidIsbn : EditMessage
    data object Offline : EditMessage
    data class NotFound(val isbn: String) : EditMessage
    /** The ISBN belongs to another book than the title typed; [found] is what it points at. */
    data class IsbnMismatch(val isbn: String, val found: BookInfo) : EditMessage
    data object NoTitleMatches : EditMessage
}

data class EditState(
    val book: Book = Book(),
    val loading: Boolean = false,
    val message: EditMessage? = null,
    val duplicateOf: Long? = null,
    val saving: Boolean = false,
    val savedId: Long? = null,
    /** Editions found by title; null when no search has been made. */
    val results: List<CatalogRecord>? = null,
    val searching: Boolean = false,
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
            when {
                info == null -> s.copy(loading = false, message = EditMessage.NotFound(isbn))
                // A mistyped digit can still be a valid ISBN, for some other book.
                !sameBook(s.book.title, info.title) -> s.copy(loading = false, message = EditMessage.IsbnMismatch(isbn, info))
                else -> s.copy(loading = false, book = s.book.filledFrom(info))
            }
        }
    }

    /** Fills in the book an ISBN pointed at, though its title differs from the one typed. */
    fun useMismatch() {
        val m = _state.value.message as? EditMessage.IsbnMismatch ?: return
        _state.update { it.copy(message = null, book = it.book.filledFrom(m.found)) }
    }

    /** Looks for the typed title (and author) in the catalogues, for a book whose barcode won't scan. */
    fun searchTitle() {
        val book = _state.value.book
        if (book.title.isBlank() || _state.value.searching) return
        _state.update { it.copy(searching = true, message = null, results = null) }
        viewModelScope.launch {
            val found = try {
                repo.searchCatalogue(book.title, book.authors)
                    // A typed author with a spelling the catalogue doesn't share: try the title alone.
                    .ifEmpty { if (book.authors.isNotBlank()) repo.searchCatalogue(book.title, "") else emptyList() }
            } catch (e: IOException) {
                _state.update { it.copy(searching = false, message = EditMessage.Offline) }
                return@launch
            }
            _state.update {
                it.copy(searching = false, results = found, message = if (found.isEmpty()) EditMessage.NoTitleMatches else null)
            }
        }
    }

    fun closeResults() = _state.update { it.copy(results = null) }

    /** Fills the form from an edition picked among the search results. */
    fun pick(record: CatalogRecord) {
        _state.update { it.copy(results = null, loading = true, message = null) }
        viewModelScope.launch {
            val existing = record.isbn?.let { repo.findByIsbn(it) }
            if (existing != null && existing.id != _state.value.book.id) {
                _state.update { it.copy(loading = false, duplicateOf = existing.id) }
                return@launch
            }
            val info = try {
                repo.bookInfo(record)
            } catch (e: JSONException) {
                null
            }
            _state.update { s ->
                if (info == null) {
                    s.copy(loading = false)
                } else {
                    s.copy(loading = false, book = s.book.filledFrom(info).copy(isbn = record.isbn ?: s.book.isbn))
                }
            }
        }
    }

    private fun Book.filledFrom(info: BookInfo) = copy(
        title = info.title,
        authors = info.authors.ifBlank { authors },
        publisher = info.publisher ?: publisher,
        publishedDate = info.publishedDate ?: publishedDate,
        pageCount = info.pageCount ?: pageCount,
        description = info.description ?: description,
        coverUrl = info.coverUrl ?: coverUrl,
    )

    /** Whether a looked-up title is the book already typed (blank counts as a match). */
    private fun sameBook(typed: String, found: String): Boolean {
        if (typed.isBlank()) return true
        val a = ReleaseFinder.titleKey(typed)
        val b = ReleaseFinder.titleKey(found)
        return a == b || a.startsWith(b) || b.startsWith(a)
    }

    fun save() {
        val book = _state.value.book
        if (book.title.isBlank() || _state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val isbn = book.isbn?.let { Isbn.normalize(it) ?: it.trim().ifBlank { null } }
            // A typed ISBN that's already in the library, without a lookup first.
            val existing = isbn?.let { repo.findByIsbn(it) }
            if (existing != null && existing.id != book.id) {
                _state.update { it.copy(saving = false, duplicateOf = existing.id) }
                return@launch
            }
            val id = repo.save(
                book.copy(
                    title = book.title.trim(),
                    authors = book.authors.trim(),
                    notes = book.notes.trim(),
                    isbn = isbn,
                ),
            )
            savedPhoto = true
            _state.update { it.copy(saving = false, savedId = id) }
        }
    }

    // A cover photo taken for a new book that is never saved would stay in storage.
    private val newPhoto = photoPath.takeIf { bookId == 0L }
    private var savedPhoto = false

    override fun onCleared() {
        val photo = newPhoto ?: return
        if (!savedPhoto && _state.value.book.photoPath == photo) java.io.File(photo).delete()
    }
}
