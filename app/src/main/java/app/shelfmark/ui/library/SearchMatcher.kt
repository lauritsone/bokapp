package app.shelfmark.ui.library

import app.shelfmark.data.Book
import app.shelfmark.data.foldText

/**
 * Forgiving library search: every word typed must appear somewhere in the book, in any
 * order. Case, accents and punctuation are ignored, so "britt marie" finds
 * "Britt-Marie" and "langstrump" finds "Långstrump".
 */
object SearchMatcher {

    fun terms(query: String): List<String> = foldText(query).split(' ').filter { it.isNotEmpty() }

    fun matches(book: Book, terms: List<String>): Boolean {
        if (terms.isEmpty()) return true
        val text = foldText(
            listOfNotNull(book.title, book.authors, book.notes, book.isbn, book.publisher).joinToString(" "),
        )
        return terms.all { it in text }
    }
}
