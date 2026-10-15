package app.shelfmark.ui.library

import app.shelfmark.R
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.shelfmark.data.Book
import app.shelfmark.data.BookRepository
import app.shelfmark.data.Shelf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class SortMode(@StringRes val label: Int) {
    Recent(R.string.sort_recent),
    Rating(R.string.sort_rating),
    Title(R.string.sort_title),
    Author(R.string.sort_author),
}

data class LibraryState(
    /** Books on the selected shelf that match the search. */
    val books: List<Book> = emptyList(),
    /** Books on every shelf; zero means the library is empty. */
    val totalCount: Int = 0,
    val shelfCounts: Map<Shelf, Int> = emptyMap(),
    /** Average over finished books only. */
    val averageRating: Double? = null,
    val shelf: Shelf = Shelf.Read,
    /** True while a search is shown; results then come from every shelf. */
    val searching: Boolean = false,
    val sort: SortMode = SortMode.Recent,
    val loaded: Boolean = false,
)

class LibraryViewModel(repo: BookRepository) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val sort = MutableStateFlow(SortMode.Recent)
    private val shelf = MutableStateFlow(Shelf.Read)

    val state: StateFlow<LibraryState> = combine(repo.books, _query, sort, shelf) { all, q, s, sh ->
        // A search looks through every shelf; otherwise show the selected shelf.
        val terms = SearchMatcher.terms(q)
        val filtered = if (terms.isEmpty()) {
            all.filter { it.shelf == sh }
        } else {
            all.filter { SearchMatcher.matches(it, terms) }
        }
        val sorted = when (s) {
            SortMode.Recent -> filtered.sortedByDescending { it.addedAt }
            SortMode.Rating -> filtered.sortedWith(
                compareByDescending<Book> { it.rating }.thenByDescending { it.addedAt },
            )
            SortMode.Title -> filtered.sortedBy { it.title.lowercase() }
            SortMode.Author -> filtered.sortedBy { it.authors.lowercase() }
        }
        val rated = all.filter { it.shelf == Shelf.Read && it.rating > 0 }
        LibraryState(
            books = sorted,
            totalCount = all.size,
            shelfCounts = all.groupingBy { it.shelf }.eachCount(),
            averageRating = if (rated.isEmpty()) null else rated.map { it.rating }.average(),
            shelf = sh,
            searching = terms.isNotEmpty(),
            sort = s,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState())

    fun setQuery(q: String) {
        _query.value = q
    }

    fun setShelf(s: Shelf) {
        shelf.value = s
    }

    fun setSort(s: SortMode) {
        sort.value = s
    }
}
