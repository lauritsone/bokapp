package app.shelfmark.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.shelfmark.data.Book
import app.shelfmark.data.BookRepository
import app.shelfmark.data.Shelf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BookDetailViewModel(
    private val repo: BookRepository,
    private val id: Long,
) : ViewModel() {

    val book: StateFlow<Book?> = repo.observe(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setRating(rating: Int) {
        viewModelScope.launch { repo.setRating(id, rating) }
    }

    fun setShelf(shelf: Shelf) {
        viewModelScope.launch { repo.setShelf(id, shelf) }
    }

    fun saveNotes(notes: String) {
        viewModelScope.launch { repo.setNotes(id, notes) }
    }

    fun delete(onDone: () -> Unit) {
        val current = book.value ?: return
        viewModelScope.launch {
            repo.delete(current)
            onDone()
        }
    }
}
