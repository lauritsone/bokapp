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

    fun matches(book: Book, terms: List<String>): Boolean =
        terms.isEmpty() || matches(foldText(rawText(book)), terms)

    /** Whether [folded], a book's [rawText] after [foldText], holds every term. */
    fun matches(folded: String, terms: List<String>): Boolean = terms.all { it in folded }

    /** Everything about a book that a search looks through, before folding. */
    fun rawText(book: Book): String =
        listOfNotNull(book.title, book.authors, book.notes, book.isbn, book.publisher, book.series, book.tags, book.lentTo).joinToString(" ")
}
