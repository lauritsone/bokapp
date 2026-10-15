package app.shelfmark.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.shelfmark.data.Book
import app.shelfmark.data.BookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class SortMode(val label: String) {
    Recent("Recently added"),
    Rating("Highest rated"),
    Title("Title"),
    Author("Author"),
}

data class LibraryState(
    val books: List<Book> = emptyList(),
    val totalCount: Int = 0,
    val averageRating: Double? = null,
    val sort: SortMode = SortMode.Recent,
    val loaded: Boolean = false,
)

class LibraryViewModel(repo: BookRepository) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val sort = MutableStateFlow(SortMode.Recent)

    val state: StateFlow<LibraryState> = combine(repo.books, _query, sort) { all, q, s ->
        val needle = q.trim()
        val filtered = if (needle.isEmpty()) all else all.filter {
            it.title.contains(needle, ignoreCase = true) ||
                it.authors.contains(needle, ignoreCase = true) ||
                it.notes.contains(needle, ignoreCase = true) ||
                it.isbn?.contains(needle) == true
        }
        val sorted = when (s) {
            SortMode.Recent -> filtered.sortedByDescending { it.addedAt }
            SortMode.Rating -> filtered.sortedWith(
                compareByDescending<Book> { it.rating }.thenByDescending { it.addedAt },
            )
            SortMode.Title -> filtered.sortedBy { it.title.lowercase() }
            SortMode.Author -> filtered.sortedBy { it.authors.lowercase() }
        }
        val rated = all.filter { it.rating > 0 }
        LibraryState(
            books = sorted,
            totalCount = all.size,
            averageRating = if (rated.isEmpty()) null else rated.map { it.rating }.average(),
            sort = s,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState())

    fun setQuery(q: String) {
        _query.value = q
    }

    fun setSort(s: SortMode) {
        sort.value = s
    }
}
