package app.shelfmark.ui.library

import app.shelfmark.R
import java.text.Collator
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.shelfmark.data.Book
import app.shelfmark.data.BookRepository
import app.shelfmark.data.Shelf
import app.shelfmark.data.foldText
import app.shelfmark.data.seriesOrder
import app.shelfmark.data.tagList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SortMode(@StringRes val label: Int) {
    Recent(R.string.sort_recent),
    Rating(R.string.sort_rating),
    Title(R.string.sort_title),
    Author(R.string.sort_author),
    Series(R.string.sort_series),
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
    /** The user's tags, most used first, for the filter chips. */
    val tags: List<String> = emptyList(),
    /** Books lent out right now. */
    val lentCount: Int = 0,
    /** A tag, or [LENT_FILTER], to show only those books (from every shelf). */
    val filter: String? = null,
)

/** The filter value for books that are lent out. */
const val LENT_FILTER = "\u0001lent"

class LibraryViewModel(
    private val repo: BookRepository,
    // Kept so the shelf, search, sort and filter survive Android closing the app in the background.
    private val saved: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    val query: StateFlow<String> = saved.getStateFlow("query", "")
    private val sort = saved.getStateFlow("sort", SortMode.Recent.name)
    private val shelf = saved.getStateFlow("shelf", Shelf.Read.code)
    private val filter = saved.getStateFlow<String?>("filter", null)

    val state: StateFlow<LibraryState> = combine(repo.books, query, sort, shelf, filter) { all, q, sortName, shelfCode, f ->
        val s = SortMode.entries.firstOrNull { it.name == sortName } ?: SortMode.Recent
        val sh = Shelf.fromCode(shelfCode)
        // A search or filter looks through every shelf; otherwise show the selected shelf.
        val terms = SearchMatcher.terms(q)
        val tags = all.flatMap { it.tagList() }.groupBy(::foldText)
            .entries.sortedByDescending { it.value.size }.map { it.value.first() }
        // A filter nothing matches any more (the last lent book came back, a tag was removed)
        // is dropped, so the shelves come back instead of an empty list with no way out.
        val tagKey = f?.takeIf { it != LENT_FILTER }?.let(::foldText)
        val active = when {
            f == LENT_FILTER -> f.takeIf { all.any { it.lentTo != null } }
            tagKey != null -> f.takeIf { tags.any { foldText(it) == tagKey } }
            else -> null
        }
        val filtered = all.filter { book ->
            when {
                active == LENT_FILTER -> book.lentTo != null
                active != null -> book.tagList().any { foldText(it) == tagKey }
                tagKey != null -> book.tagList().any { foldText(it) == tagKey }
                terms.isEmpty() -> book.shelf == sh
                else -> true
            } && SearchMatcher.matches(book, terms)
        }
        val sorted = when (s) {
            SortMode.Recent -> filtered.sortedByDescending { it.addedAt }
            SortMode.Rating -> filtered.sortedWith(
                compareByDescending<Book> { it.rating }.thenByDescending { it.addedAt },
            )
            // The phone's alphabet: in Norwegian Æ, Ø and Å come last, and É sorts with E.
            SortMode.Title -> filtered.sortedWith(compareBy(Collator.getInstance()) { it.title })
            SortMode.Author -> filtered.sortedWith(compareBy(Collator.getInstance()) { it.authors })
            // Each series together and in order; books outside a series after them.
            SortMode.Series -> filtered.sortedWith(
                compareBy<Book> { it.series == null }
                    .thenBy(Collator.getInstance()) { it.series.orEmpty() }
                    .thenBy { it.seriesOrder() }
                    .thenBy(Collator.getInstance()) { it.title },
            )
        }
        val rated = all.filter { it.shelf == Shelf.Read && it.rating > 0 }
        LibraryState(
            books = sorted,
            totalCount = all.size,
            shelfCounts = all.groupingBy { it.shelf }.eachCount(),
            averageRating = if (rated.isEmpty()) null else rated.map { it.rating }.average(),
            shelf = sh,
            searching = terms.isNotEmpty() || active != null,
            sort = s,
            loaded = true,
            tags = tags,
            lentCount = all.count { it.lentTo != null },
            filter = active,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState())

    fun setQuery(q: String) {
        saved["query"] = q
    }

    fun setShelf(s: Shelf) {
        saved["shelf"] = s.code
    }

    fun setSort(s: SortMode) {
        saved["sort"] = s.name
    }

    /** Shows only books with this tag (or lent out); null shows the shelves again. */
    fun setFilter(f: String?) {
        saved["filter"] = f
    }

    private val _justFinished = MutableStateFlow<Book?>(null)

    /** A book that was just finished from the list, waiting for a rating and review. */
    val justFinished: StateFlow<Book?> = _justFinished.asStateFlow()

    fun finish(book: Book) {
        _justFinished.value = book
        viewModelScope.launch { repo.finish(book.id) }
    }

    fun review(rating: Int, notes: String) {
        val book = _justFinished.value ?: return
        _justFinished.value = null
        viewModelScope.launch { repo.review(book.id, rating, notes) }
    }

    fun skipReview() {
        _justFinished.value = null
    }
}
