package app.shelfmark.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailRefreshTest {

    private fun record(title: String, author: String, isbn: String? = null, id: String = title + author + isbn) =
        CatalogRecord(id, title, listOf(author), null, 2024, isbn, null, null, 0)

    @Test fun picksTheEditionWithAnIsbn() {
        val found = listOf(
            record("Tusenfryd", "Ohlsson, Kristina"),
            record("Tusenfryd : roman", "Ohlsson, Kristina", isbn = "9788203365089"),
            record("Tusenfryd og andre blomster", "Ohlsson, Kristina", isbn = "9788200000000"),
        )
        val pick = DetailRefresh.pick("Tusenfryd", found, byAuthor = true)
        assertEquals("9788203365089", (pick as DetailRefresh.PickResult.One).record.isbn)
    }

    @Test fun aLongerTitleIsAnotherBook() {
        val found = listOf(record("Tusenfryd og andre blomster", "Ohlsson, Kristina"))
        assertEquals(DetailRefresh.PickResult.None, DetailRefresh.pick("Tusenfryd", found, byAuthor = true))
    }

    @Test fun withoutAnAuthorTheTitleMustBelongToOne() {
        val one = listOf(record("Tusenfryd", "Ohlsson, Kristina"), record("Tusenfryd", "Ohlsson, Kristina", isbn = "1"))
        assertTrue(DetailRefresh.pick("Tusenfryd", one, byAuthor = false) is DetailRefresh.PickResult.One)
        val two = one + record("Tusenfryd", "Hansen, Per")
        assertEquals(DetailRefresh.PickResult.Several, DetailRefresh.pick("Tusenfryd", two, byAuthor = false))
    }

    @Test fun aMisspeltAuthorStillNarrowsByName() {
        val found = listOf(record("Tusenfryd", "Ohlsson, Kristina"), record("Tusenfryd", "Hansen, Per"))
        val pick = DetailRefresh.pick("Tusenfryd", found, byAuthor = false, author = "Kristine Ohlsson")
        assertEquals("Ohlsson, Kristina", (pick as DetailRefresh.PickResult.One).record.creators.first())
        assertEquals(DetailRefresh.PickResult.None, DetailRefresh.pick("Tusenfryd", found, byAuthor = false, author = "Jo Nesbø"))
    }

    @Test fun fillsOnlyWhatIsMissing() {
        val book = Book(id = 3, title = "Tusenfryd", authors = "", publisher = "Mitt forlag", coverUrl = "https://covers.openlibrary.org/b/isbn/1-L.jpg")
        val info = BookInfo("9788203365089", "Tusenfryd", "Kristina Ohlsson", "Aschehoug", "2024", 380, "En krimroman.", "https://nb.no/cover.jpg")
        val filled = DetailRefresh.fillGaps(book, info, isbnTaken = false)
        assertEquals("Tusenfryd", filled.title)
        assertEquals("Kristina Ohlsson", filled.authors)
        assertEquals("Mitt forlag", filled.publisher)
        assertEquals("9788203365089", filled.isbn)
        assertEquals("https://nb.no/cover.jpg", filled.coverUrl)
        assertEquals(6, DetailRefresh.filled(book, filled))
        assertEquals(null, DetailRefresh.fillGaps(book, info, isbnTaken = true).isbn)
        assertEquals(0, DetailRefresh.filled(filled, DetailRefresh.fillGaps(filled, info, isbnTaken = false)))
    }

    @Test fun anIsbnForAnotherTitleIsNotTrusted() {
        assertTrue(DetailRefresh.sameTitle("Tusenfryd", "Tusenfryd : roman"))
        assertTrue(!DetailRefresh.sameTitle("Tusenfryd", "Sommerlys"))
        assertTrue(!DetailRefresh.sameTitle("", "Sommerlys"))
    }
}
