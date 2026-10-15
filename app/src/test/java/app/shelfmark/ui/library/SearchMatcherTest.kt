package app.shelfmark.ui.library

import app.shelfmark.data.Book
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchMatcherTest {
    private val britt = Book(title = "Britt-Marie var her", authors = "Fredrik Backman, Einar Blomgren", isbn = "9788202485436")
    private val pippi = Book(title = "Pippi Långstrump", authors = "Astrid Lindgren")
    private val sno = Book(title = "Snømannen", authors = "Jo Nesbø", notes = "Mørk og spennende")
    private fun hit(b: Book, q: String) = SearchMatcher.matches(b, SearchMatcher.terms(q))

    @Test fun matches() {
        for (q in listOf("Britt", "britt marie", "Britt-Marie", "marie britt", "backman", "BLOMGREN", "var her", "9788202485436", "978820")) assertTrue(q, hit(britt, q))
        for (q in listOf("langstrump", "Långstrump", "pippi lind")) assertTrue(q, hit(pippi, q))
        for (q in listOf("snomannen", "Snømannen", "nesbo", "Nesbø", "mork", "spennende")) assertTrue(q, hit(sno, q))
        for (q in listOf("brittany", "marie curie", "nesbo backman")) assertFalse(q, hit(britt, q))
        assertTrue(hit(britt, "   "))
    }
}
