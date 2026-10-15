package app.shelfmark.ui.library

import app.shelfmark.data.Book
import java.text.Normalizer
import java.util.Locale

/**
 * Forgiving library search: every word typed must appear somewhere in the book, in any
 * order. Case, accents and punctuation are ignored, so "britt marie" finds
 * "Britt-Marie" and "langstrump" finds "Långstrump".
 */
object SearchMatcher {

    fun terms(query: String): List<String> = normalize(query).split(' ').filter { it.isNotEmpty() }

    fun matches(book: Book, terms: List<String>): Boolean {
        if (terms.isEmpty()) return true
        val text = normalize(
            listOfNotNull(book.title, book.authors, book.notes, book.isbn, book.publisher).joinToString(" "),
        )
        return terms.all { it in text }
    }

    private val combiningMarks = Regex("\\p{Mn}+")
    private val separators = Regex("[^\\p{L}\\p{N}]+")

    fun normalize(s: String): String {
        // ø and æ don't decompose into a base letter, so spell them out first.
        val folded = s.lowercase(Locale.ROOT)
            .replace("ø", "o")
            .replace("æ", "ae")
            .replace("ß", "ss")
        return Normalizer.normalize(folded, Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .replace(separators, " ")
            .trim()
    }
}
