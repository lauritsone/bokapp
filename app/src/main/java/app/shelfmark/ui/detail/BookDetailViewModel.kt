package app.shelfmark.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.shelfmark.data.Book
import app.shelfmark.data.BookRepository
import app.shelfmark.data.NextInSeries
import app.shelfmark.data.RefreshResult
import app.shelfmark.data.foldText
import app.shelfmark.data.seriesOrder
import kotlinx.coroutines.flow.combine
import app.shelfmark.data.Shelf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

class BookDetailViewModel(
    private val repo: BookRepository,
    private val id: Long,
) : ViewModel() {

    val book: StateFlow<Book?> = repo.observe(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** True once it's known there is no such book (removed elsewhere), as opposed to still loading. */
    val missing: StateFlow<Boolean> = repo.observe(id).map { it == null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setRating(rating: Int) {
        viewModelScope.launch { repo.setRating(id, rating) }
    }

    private val _askForReview = MutableStateFlow(false)

    /** True right after the book was finished, to ask for a rating and review. */
    val askForReview: StateFlow<Boolean> = _askForReview.asStateFlow()

    fun setShelf(shelf: Shelf) {
        if (shelf == Shelf.Read && book.value?.shelf != Shelf.Read) _askForReview.value = true
        viewModelScope.launch { repo.setShelf(id, shelf) }
    }

    fun finish() = setShelf(Shelf.Read)

    fun review(rating: Int, notes: String) {
        _askForReview.value = false
        viewModelScope.launch { repo.review(id, rating, notes) }
    }

    fun skipReview() {
        _askForReview.value = false
    }

    fun setUseForSuggestions(use: Boolean) {
        viewModelScope.launch { repo.setIgnoreForSuggestions(id, !use) }
    }

    fun saveNotes(notes: String) {
        viewModelScope.launch { repo.setNotes(id, notes) }
    }

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _refreshResult = MutableStateFlow<RefreshResult?>(null)

    /** The outcome of the last refresh, until the screen has shown it. */
    val refreshResult: StateFlow<RefreshResult?> = _refreshResult.asStateFlow()

    private val _offline = MutableStateFlow(false)

    /** True when the last refresh couldn't reach any catalogue. */
    val offline: StateFlow<Boolean> = _offline.asStateFlow()

    /** Looks the book up in the catalogues again and fills in the details it lacks. */
    fun refresh() {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            try {
                _refreshResult.value = repo.refreshDetails(id)
            } catch (e: IOException) {
                _offline.value = true
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun refreshShown() {
        _refreshResult.value = null
        _offline.value = false
    }

    /** The other books of this book's series in the library, in series order (this one included). */
    val seriesBooks: StateFlow<List<Book>> = combine(book, repo.books) { current, all ->
        val series = current?.series?.let(::foldText) ?: return@combine emptyList()
        all.filter { it.series?.let(::foldText) == series }
            .sortedWith(compareBy({ it.seriesOrder() }, { it.publishedDate ?: "" }, { it.addedAt }))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setProgress(page: Int?) {
        viewModelScope.launch { repo.setProgress(id, page) }
    }

    fun setDates(startedAt: Long?, finishedAt: Long?) {
        viewModelScope.launch { repo.setDates(id, startedAt, finishedAt) }
    }

    fun lend(to: String) {
        viewModelScope.launch { repo.lend(id, to) }
    }

    fun returned() {
        viewModelScope.launch { repo.returned(id) }
    }

    fun readAgain() {
        viewModelScope.launch { repo.readAgain(id) }
    }

    private val _next = MutableStateFlow<NextState>(NextState.Idle)

    /** Looking up the next book in the series, and what was found. */
    val next: StateFlow<NextState> = _next.asStateFlow()

    fun findNext() {
        val current = book.value ?: return
        if (_next.value == NextState.Searching) return
        _next.value = NextState.Searching
        viewModelScope.launch {
            _next.value = try {
                repo.nextInSeries(current)?.let { NextState.Found(it) } ?: NextState.Unknown
            } catch (e: IOException) {
                NextState.Offline
            }
        }
    }

    /** Puts the next book on Want to read, then opens it. */
    fun addNext(onAdded: (Long) -> Unit) {
        val found = (_next.value as? NextState.Found)?.next ?: return
        val current = book.value ?: return
        _next.value = NextState.Searching
        viewModelScope.launch {
            try {
                val newId = repo.addNextInSeries(current, found)
                _next.value = NextState.Idle
                onAdded(newId)
            } catch (e: IOException) {
                _next.value = NextState.Offline
            }
        }
    }

    fun delete(onDone: () -> Unit) {
        val current = book.value ?: return
        viewModelScope.launch {
            repo.delete(current)
            onDone()
        }
    }
}

sealed interface NextState {
    data object Idle : NextState
    data object Searching : NextState
    data class Found(val next: NextInSeries) : NextState
    /** The catalogue doesn't say which book comes next. */
    data object Unknown : NextState
    data object Offline : NextState
}
