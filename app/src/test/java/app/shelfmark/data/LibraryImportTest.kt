package app.shelfmark.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class LibraryImportTest {

    private val books = listOf(
        Book(
            title = "Snømannen", authors = "Jo Nesbø", rating = 4, shelf = Shelf.Read,
            notes = "Spennende, \"mørk\"\nAndre linje", isbn = "9788203362446", publisher = "Aschehoug",
            publishedDate = "2017", pageCount = 445, addedAt = 1_700_000_000_000, finishedAt = 1_700_500_000_000,
            coverUrl = "https://www.nb.no/services/image/resolver/URN:NBN:no-nb_digibok_2023071907012_C1/full/0,200/0/native.jpg",
        ),
        Book(title = "Pippi Långstrump", authors = "Astrid Lindgren", shelf = Shelf.WantToRead),
        Book(title = "Pride and Prejudice", authors = "Jane Austen", shelf = Shelf.Reading, publisher = "Penguin, UK"),
    )

    private fun exported(format: ExportFormat): String {
        val out = ByteArrayOutputStream()
        LibraryExport.write(format, books, out)
        return out.toString(Charsets.UTF_8.name())
    }

    private fun assertSameBooks(imported: List<Book>, keepsDescriptionFields: Boolean) {
        assertEquals(3, imported.size)
        val sno = imported[0]
        assertEquals("Snømannen", sno.title)
        assertEquals("Jo Nesbø", sno.authors)
        assertEquals(4, sno.rating)
        assertEquals(Shelf.Read, sno.shelf)
        assertEquals("Spennende, \"mørk\"\nAndre linje", sno.notes)
        assertEquals("9788203362446", sno.isbn)
        assertEquals("Aschehoug", sno.publisher)
        assertEquals("2017", sno.publishedDate)
        assertEquals(445, sno.pageCount)
        assertEquals(books[0].coverUrl, sno.coverUrl)
        assertEquals(Shelf.WantToRead, imported[1].shelf)
        assertEquals(Shelf.Reading, imported[2].shelf)
        assertEquals("Penguin, UK", imported[2].publisher)
        assertNull(imported[2].finishedAt)
        if (keepsDescriptionFields) {
            assertEquals(1_700_000_000_000, sno.addedAt)
            assertEquals(1_700_500_000_000, sno.finishedAt)
        } else {
            // CSV keeps the day, not the time.
            assertEquals(localDay(1_700_500_000_000), localDay(sno.finishedAt!!))
        }
    }

    private fun localDay(millis: Long) = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()

    @Test fun jsonBackupRoundTrips() = assertSameBooks(LibraryImport.parse(exported(ExportFormat.Json)), true)

    @Test fun csvExportRoundTrips() = assertSameBooks(LibraryImport.parse(exported(ExportFormat.Csv)), false)

    @Test fun readsSemicolonCsvWithNorwegianHeaders() {
        val csv = "Tittel;Forfatter;Hylle;Karakter\r\nSult;Knut Hamsun;Vil lese;\r\nMarkens grøde;Knut Hamsun;Lest;5\r\n"
        val books = LibraryImport.parse(csv)
        assertEquals(listOf("Sult", "Markens grøde"), books.map { it.title })
        assertEquals(listOf(Shelf.WantToRead, Shelf.Read), books.map { it.shelf })
        assertEquals(5, books[1].rating)
    }

    @Test fun readsGoodreadsExport() {
        // Goodreads wraps ISBNs as ="..." inside a quoted field.
        val csv = listOf(
            "Book Id,Title,Author,Additional Authors,ISBN,ISBN13,My Rating,Publisher,Number of Pages,Year Published,Date Added,Date Read,Exclusive Shelf,My Review,Private Notes",
            "1,The Hobbit,J.R.R. Tolkien,,\"=\"\"0261102214\"\"\",\"=\"\"9780261102217\"\"\",5,HarperCollins,310,1999,2023/05/01,2023/06/10,read,Loved it,",
            "2,Dune,Frank Herbert,,\"=\"\"\"\"\",\"=\"\"\"\"\",0,Ace,,1990,2024/01/02,,to-read,,",
            "3,Sofies verden,Jostein Gaarder,,,,0,,,1991,2024/02/03,,currently-reading,,re-read",
        ).joinToString("\n")
        val books = LibraryImport.parse(csv)
        assertEquals(3, books.size)
        assertEquals("9780261102217", books[0].isbn)
        assertEquals(5, books[0].rating)
        assertEquals(310, books[0].pageCount)
        assertEquals("Loved it", books[0].notes)
        // No cover is guessed at import; BookRepository.repairCovers looks one up afterwards.
        assertNull(books[0].coverUrl)
        assertEquals(java.time.LocalDate.of(2023, 6, 10), localDay(books[0].finishedAt!!))
        assertNull(books[1].finishedAt)
        assertEquals(Shelf.WantToRead, books[1].shelf)
        assertNull(books[1].isbn)
        assertEquals(Shelf.Reading, books[2].shelf)
        assertEquals("re-read", books[2].notes)
    }

    @Test(expected = ImportException::class)
    fun rejectsCsvWithoutTitleColumn() {
        LibraryImport.parse("name,score\nfoo,1\n")
    }

    @Test(expected = ImportException::class)
    fun rejectsBrokenJson() {
        LibraryImport.parse("{\"books\": [")
    }

    @Test fun emptyFileHasNoBooks() = assertEquals(0, LibraryImport.parse("﻿  \n").size)
}
