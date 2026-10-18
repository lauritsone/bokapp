package app.shelfmark.ui.library

import app.shelfmark.R
import java.text.Collator
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.shelfmark.data.Book
import app.shelfmark.data.BookRepository
import app.shelfmark.data.Removed
import app.shelfmark.data.Shelf
import app.shelfmark.data.foldText
import app.shelfmark.data.seriesOrder
import app.shelfmark.data.tagList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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
    private val saved: SavedStateHandle,
) : ViewModel() {
    val query: StateFlow<String> = saved.getStateFlow("query", "")
    private val sort = saved.getStateFlow("sort", SortMode.Recent.name)
    private val shelf = saved.getStateFlow("shelf", Shelf.Read.code)
    private val filter = saved.getStateFlow<String?>("filter", null)

    /**
     * Each book's search text, folded, by the text before folding. Folding (accents, case) is
     * the slow part of a search, and between keystrokes the books rarely change.
     */
    private var foldedText: Map<String, String> = emptyMap()

    private fun foldedTexts(all: List<Book>): List<String> {
        val previous = foldedText
        val next = HashMap<String, String>(all.size * 2)
        val texts = all.map { book ->
            val raw = SearchMatcher.rawText(book)
            next[raw] ?: (previous[raw] ?: foldText(raw)).also { next[raw] = it }
        }
        foldedText = next
        return texts
    }

    /**
     * What the screen needs from the books themselves (tags, counts, sort keys, search text),
     * worked out once each time they change rather than on every keystroke.
     */
    private inner class LibraryIndex(val all: List<Book>) {
        /** Each book's tags, folded, by position in [all]. */
        val tagKeys: List<Set<String>>

        /** The user's tags, most used first, as first spelled. */
        val tags: List<String>
        val allTagKeys: Set<String>

        init {
            val counts = LinkedHashMap<String, Int>()
            val spelling = HashMap<String, String>()
            tagKeys = all.map { book ->
                book.tagList().mapTo(HashSet()) { tag ->
                    foldText(tag).also { key ->
                        counts[key] = (counts[key] ?: 0) + 1
                        spelling.putIfAbsent(key, tag)
                    }
                }
            }
            tags = counts.entries.sortedByDescending { it.value }.map { spelling.getValue(it.key) }
            allTagKeys = counts.keys
        }

        val shelfCounts = all.groupingBy { it.shelf }.eachCount()
        val lentCount = all.count { it.lentTo != null }
        val averageRating = all.filter { it.shelf == Shelf.Read && it.rating > 0 }.map { it.rating }
            .takeIf { it.isNotEmpty() }?.average()

        // Only worked out when a search or sort needs them.
        val searchTexts by lazy { foldedTexts(all) }

        // The phone's alphabet: in Norwegian Æ, Ø and Å come last, and É sorts with E.
        private val collator = Collator.getInstance()
        val titleKeys by lazy { all.map { collator.getCollationKey(it.title) } }
        val authorKeys by lazy { all.map { collator.getCollationKey(it.authors) } }
        val seriesKeys by lazy { all.map { collator.getCollationKey(it.series.orEmpty()) } }
    }

    private val index = repo.books.conflate().map { LibraryIndex(it) }

    val state: StateFlow<LibraryState> = combine(index, query, sort, shelf, filter) { ix, q, sortName, shelfCode, f ->
        val all = ix.all
        val s = SortMode.entries.firstOrNull { it.name == sortName } ?: SortMode.Recent
        val sh = Shelf.fromCode(shelfCode)
        // A search or filter looks through every shelf; otherwise show the selected shelf.
        val terms = SearchMatcher.terms(q)
        // A filter nothing matches any more (the last lent book came back, a tag was removed)
        // is dropped, so the shelves come back instead of an empty list with no way out.
        val tagKey = f?.takeIf { it != LENT_FILTER }?.let(::foldText)
        val active = when {
            f == LENT_FILTER -> f.takeIf { ix.lentCount > 0 }
            tagKey != null -> f.takeIf { tagKey in ix.allTagKeys }
            else -> null
        }
        val texts = if (terms.isEmpty()) null else ix.searchTexts
        val picked = all.indices.filter { i ->
            val book = all[i]
            when {
                active == LENT_FILTER -> book.lentTo != null
                active != null -> tagKey in ix.tagKeys[i]
                terms.isEmpty() -> book.shelf == sh
                else -> true
            } && (texts == null || SearchMatcher.matches(texts[i], terms))
        }
        val sorted = when (s) {
            SortMode.Recent -> picked.sortedByDescending { all[it].addedAt }
            SortMode.Rating -> picked.sortedWith(
                compareByDescending<Int> { all[it].rating }.thenByDescending { all[it].addedAt },
            )
            SortMode.Title -> picked.sortedBy { ix.titleKeys[it] }
            SortMode.Author -> picked.sortedBy { ix.authorKeys[it] }
            // Each series together and in order; books outside a series after them.
            SortMode.Series -> picked.sortedWith(
                compareBy<Int> { all[it].series == null }
                    .thenBy { ix.seriesKeys[it] }
                    .thenBy { all[it].seriesOrder() }
                    .thenBy { ix.titleKeys[it] },
            )
        }
        LibraryState(
            books = sorted.map { all[it] },
            totalCount = all.size,
            shelfCounts = ix.shelfCounts,
            averageRating = ix.averageRating,
            shelf = sh,
            searching = terms.isNotEmpty() || active != null,
            sort = s,
            loaded = true,
            tags = ix.tags,
            lentCount = ix.lentCount,
            filter = active,
        )
    }
        // Sorting and searching a large library is kept off the thread that draws the screen.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState())

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

    /** Puts a book finished by mistake back as it was. */
    fun undoFinish(before: Book) {
        viewModelScope.launch { repo.undo(before) }
    }

    /** A book just removed on its page, for the library to offer back. */
    val removed: StateFlow<Removed?> = repo.lastRemoved

    fun removedShown(shown: Removed) {
        repo.lastRemoved.compareAndSet(shown, null)
    }

    fun restore(book: Book) {
        viewModelScope.launch { repo.restore(book) }
    }
}
