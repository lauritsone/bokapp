package app.shelfmark.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.shelfmark.data.Book
import app.shelfmark.data.BookRepository
import app.shelfmark.data.RefreshResult
import app.shelfmark.data.Shelf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
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

    fun delete(onDone: () -> Unit) {
        val current = book.value ?: return
        viewModelScope.launch {
            repo.delete(current)
            onDone()
        }
    }
}
