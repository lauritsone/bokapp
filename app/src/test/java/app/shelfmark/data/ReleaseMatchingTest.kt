package app.shelfmark.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseMatchingTest {

    private fun mods(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test fun readsGenresAndAuthorFromCatalogueRecord() {
        val d = NbCatalog.parseMods(mods("mods-skyggedyr.xml"))
        assertEquals(listOf("Krim", "Romaner"), d.genres)
        assertEquals(true, d.fiction)
        assertEquals("nob", d.language)
        assertEquals("Ulrika Rolfsdotter", d.author)
        assertTrue(d.summary!!.startsWith("Det er midtsommer"))
    }

    @Test fun mapsNorwegianAndEnglishGenresToSharedTags() {
        assertEquals(setOf("crime", "novel", Genres.FICTION), Genres.tags(listOf("Krim", "Romaner"), true))
        assertTrue("crime" in Genres.tags(listOf("Fiction, thrillers, crime"), null))
        assertTrue(Genres.FICTION in Genres.tags(listOf("Kriminalromaner"), null))
        assertEquals(setOf("children", Genres.NONFICTION), Genres.tags(listOf("Barnebøker"), false))
        // Unknown genres are kept as words, so they still match the same word elsewhere.
        assertTrue("sjorovere" in Genres.tags(listOf("Sjørøvere"), null))
        assertEquals(setOf("crime", "novel"), Genres.decode(Genres.encode(setOf("novel", "crime"))))
        // "Roman" means novel, but romance and romantikk are their own genre.
        assertEquals(setOf("romance", Genres.FICTION), Genres.tags(listOf("Romance"), null))
        assertEquals(setOf("romance", "novel", Genres.FICTION), Genres.tags(listOf("Romantikk", "Serieromaner"), null))
        // Open Library's English labels.
        assertEquals(setOf("novel", Genres.FICTION), Genres.tags(listOf("Literary Fiction"), null))
        assertTrue("crime" in Genres.tags(listOf("Fiction, thrillers, crime"), null))
        assertEquals(setOf(Genres.NONFICTION), Genres.tags(listOf("Nonfiction"), null))
    }

    @Test fun cleansCatalogueTitles() {
        assertEquals("Minnesota", ReleaseFinder.displayTitle("Minnesota : roman"))
        assertEquals("Skyggedyr", ReleaseFinder.displayTitle("Skyggedyr : krim"))
        assertEquals("Jeffrey Epstein: døde menn snakker ikke", ReleaseFinder.displayTitle("Jeffrey Epstein : døde menn snakker ikke"))
        assertEquals("The Making of Modern Eating", ReleaseFinder.displayTitle("The  Making of Modern Eating"))
    }

    @Test fun matchesCatalogueAuthorNames() {
        val r = record(creators = listOf("Nesbø, Jo", "Hanso, Riina"))
        assertTrue(ReleaseFinder.wroteBy(r, "Jo Nesbø"))
        assertTrue(ReleaseFinder.wroteBy(r, "Jo Nesbo"))
        assertFalse(ReleaseFinder.wroteBy(r, "Jo Ruud"))
    }

    private val crime = Genres.encode(setOf("crime", "novel", Genres.FICTION))
    private val library = listOf(
        Book(title = "Snømannen", authors = "Jo Nesbø", rating = 5, publisher = "Aschehoug", genres = crime),
        Book(title = "Panserhjerte", authors = "Jo Nesbø", rating = 4, publisher = "Aschehoug", genres = crime),
        Book(title = "Skyggedyr", authors = "Ulrika Rolfsdotter", rating = 4, publisher = "Aschehoug", genres = crime),
        Book(title = "Britt-Marie var her", authors = "Fredrik Backman", rating = 3, publisher = "Cappelen Damm", genres = Genres.encode(setOf("novel", Genres.FICTION))),
        Book(title = "Kokebok", authors = "Hellstrøm", rating = 1, publisher = "Gyldendal", genres = Genres.encode(setOf("cooking", Genres.NONFICTION))),
    )
    private val profile = TasteProfile(library)

    private fun record(title: String = "Ny bok", creators: List<String> = emptyList(), publisher: String? = null) =
        CatalogRecord("id", title, creators, publisher, 2026, null, null, null, 0)

    private fun candidate(author: String, publisher: String?, genres: List<String>, fiction: Boolean?) =
        Candidate(record(publisher = publisher), author, genres, Genres.tags(genres, fiction), null)

    @Test fun favouriteAuthorInFavouriteGenreScoresHighest() {
        val nesbo = profile.score(candidate("Jo Nesbø", "Aschehoug", listOf("Krim", "Romaner"), true))
        val otherCrime = profile.score(candidate("Ny Forfatter", "Aschehoug", listOf("Krim"), true))
        val otherNovel = profile.score(candidate("Ny Forfatter", "Cappelen Damm", listOf("Romaner"), true))
        val cookbook = profile.score(candidate("Kokk", "Gyldendal", listOf("Kokebøker"), false))
        val dislikedAuthor = profile.score(candidate("Hellstrøm", "Gyldendal", listOf("Kokebøker"), false))

        assertTrue("nesbo ${nesbo.percent}", nesbo.percent >= 90)
        assertTrue(nesbo.percent > otherCrime.percent)
        assertTrue(otherCrime.percent > otherNovel.percent)
        assertTrue(otherNovel.percent > cookbook.percent)
        assertTrue(dislikedAuthor.percent <= cookbook.percent)

        assertEquals(MatchReason.Author("Jo Nesbø", 5), nesbo.reasons[0])
        assertTrue(nesbo.reasons.any { it is MatchReason.Genre && it.label == "Krim" && it.books == 3 })
        assertTrue(nesbo.reasons.any { it == MatchReason.Publisher("Aschehoug") })
        assertTrue(cookbook.reasons.isEmpty())
    }

    @Test fun englishEditionsAreNotPenalisedForUnknownPublishers() {
        val tags = Genres.tags(listOf("Krim"), true)
        val norwegian = profile.score(Candidate(record(publisher = "Ukjent Forlag"), "Ny Forfatter", listOf("Krim"), tags, null))
        val english = profile.score(Candidate(record(publisher = "Penguin").copy(language = "eng"), "New Author", listOf("Crime"), tags, null))
        assertTrue("eng ${english.percent} nob ${norwegian.percent}", english.percent > norwegian.percent)
        assertTrue(english.reasons.none { it is MatchReason.Publisher })
    }

    @Test fun readsOpenLibrarySearch() {
        val json = """{"docs":[{"key":"/works/OL42407794W","title":"Mina vänner","author_name":["Fredrik Backman"],
            "first_publish_year":2025,"subject":["Fiction","nyt:combined=2025","genre:Contemporary"],"cover_i":1,
            "editions":{"docs":[{"title":"My Friends","language":["eng"],"isbn":["1982121653","9781982121655"],"cover_i":15094954,"publisher":["Atria Books"]}]}}]}"""
        val r = OlCatalog.parseSearch(json).single()
        assertEquals("My Friends", r.title)
        assertEquals("9781982121655", r.isbn)
        assertEquals("Atria Books", r.publisher)
        assertEquals("https://covers.openlibrary.org/b/id/15094954-L.jpg", r.coverUrl)
        assertEquals(listOf("Fiction", "Contemporary"), r.subjects)
        assertEquals("eng", r.language)
        assertTrue(r.knownFirstEdition)
    }

    @Test fun booksLeftOutDontShapeTheProfile() {
        val cookbookFan = library + List(3) { i ->
            Book(title = "Bakebok $i", authors = "Ingrid Espelid", rating = 5, publisher = "Gyldendal",
                genres = Genres.encode(setOf("cooking", Genres.NONFICTION)), ignoreForSuggestions = true)
        }
        val left = TasteProfile(cookbookFan)
        assertEquals(profile.bookCount, left.bookCount)
        assertFalse("Ingrid Espelid" in left.favouriteAuthors(10))
        assertFalse("cooking" in left.topGenres(5))
        val baking = candidate("Ingrid Espelid", "Gyldendal", listOf("Kokebøker"), false)
        assertEquals(profile.score(baking).percent, left.score(baking).percent)
        assertTrue(TasteProfile(cookbookFan.map { it.copy(ignoreForSuggestions = false) }).score(baking).percent > left.score(baking).percent)
    }

    @Test fun picksAuthorsAndPublishersToSearch() {
        assertEquals("Jo Nesbø", profile.favouriteAuthors(3).first())
        assertFalse("Hellstrøm" in profile.favouriteAuthors(10))
        assertEquals(listOf("Aschehoug", "Cappelen Damm"), profile.favouritePublishers(3))
    }
}
