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
        assertEquals(listOf("Annie Ljung (fiktiv person)", "Ådalen"), d.topics.distinct())
        assertEquals("adult", d.audience)
        assertEquals("839.738", d.dewey)
        val traits = Genres.traits(d)
        assertTrue("series:annie ljung" in traits)
        assertTrue("topic:adalen" in traits)
        assertTrue(Genres.ADULT in traits)
        assertTrue("lit:swedish" in traits)
        assertTrue(Genres.DARK in traits)
    }

    @Test fun readsMoodAudienceAndDeweyFromRecords() {
        fun traits(genres: List<String>, summary: String? = null, topics: List<String> = emptyList(), audience: String? = null, dewey: String? = null) =
            Genres.traits(NbDetails(genres, null, "nob", summary, null, topics, audience, dewey))
        assertTrue(Genres.LIGHT in traits(listOf("Feelgood", "Romaner")))
        assertTrue(Genres.LIGHT in traits(listOf("Romaner"), "En hjertevarm og morsom roman om et lite bakeri."))
        assertTrue(Genres.DARK in traits(listOf("Romaner"), "Et brutalt drap ryster bygda."))
        assertTrue(Genres.SERIOUS in traits(listOf("Romaner"), topics = listOf("Døden", "Sorg")))
        // A feelgood novel may deal with grief; the genre decides the mood.
        assertFalse(Genres.SERIOUS in traits(listOf("Feelgood"), topics = listOf("Sorg")))
        assertTrue(Genres.JUVENILE in traits(listOf("Fantasy", "Romaner"), audience = "juvenile"))
        assertTrue(Genres.JUVENILE in traits(listOf("Bildebøker")))
        assertTrue("lit:norwegian" in traits(listOf("Romaner"), dewey = "839.8238"))
        assertTrue("lit:english" in traits(listOf("Romaner"), dewey = "823.92"))
        assertTrue("dewey:641" in traits(emptyList(), dewey = "641.5"))
        assertFalse(Genres.isGenre("topic:vennskap"))
        assertTrue(Genres.isGenre("dewey:641"))
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

    private fun candidate(details: NbDetails) =
        Candidate(record(publisher = "Ukjent"), details.author ?: "Ny Forfatter", details.genres, Genres.traits(details), details.summary, details.topics)

    private val feelgoodReader = TasteProfile(listOf(
        Book(title = "Det lille bakeriet", authors = "Jenny Colgan", rating = 5, publisher = "Cappelen Damm",
            genres = Genres.encode(Genres.traits(NbDetails(listOf("Feelgood", "Romaner"), true, "nob", null, null, listOf("Bakerier", "Vennskap"), "adult", "823.92")))),
        Book(title = "Ved første blikk", authors = "Jill Mansell", rating = 4, publisher = "Cappelen Damm",
            genres = Genres.encode(Genres.traits(NbDetails(listOf("Feelgood", "Romantikk", "Romaner"), true, "nob", null, null, emptyList(), "adult", "823.92")))),
    ))

    private fun novel(genres: List<String>, summary: String? = null, topics: List<String> = emptyList(), audience: String? = "adult") =
        candidate(NbDetails(genres, true, "nob", summary, null, topics, audience, "839.8238"))

    @Test fun feelgoodReaderGetsFeelgoodBeforeOtherNovels() {
        val cosy = feelgoodReader.score(novel(listOf("Feelgood", "Romaner"), topics = listOf("Vennskap")))
        val plain = feelgoodReader.score(novel(listOf("Romaner")))
        val literary = feelgoodReader.score(novel(listOf("Romaner"), "Prisbelønt roman om sorg.", listOf("Døden", "Sorg")))
        val thriller = feelgoodReader.score(novel(listOf("Romaner", "Psykologiske thrillere"), "Han er på jakt etter det perfekte offeret."))
        val youngAdult = feelgoodReader.score(novel(listOf("Feelgood", "Romaner"), audience = "juvenile"))

        assertTrue("cosy ${cosy.percent}", cosy.percent >= 75)
        assertTrue("${cosy.percent} > ${plain.percent}", cosy.percent > plain.percent)
        assertTrue("${plain.percent} > ${literary.percent}", plain.percent > literary.percent)
        assertTrue("${literary.percent} > ${thriller.percent}", literary.percent >= thriller.percent)
        assertTrue("thriller ${thriller.percent}", thriller.percent < ReleaseFinder.MIN_PERCENT)
        assertTrue("young adult ${youngAdult.percent}", youngAdult.percent < ReleaseFinder.MIN_PERCENT)
        assertTrue(cosy.reasons.any { it is MatchReason.Theme && it.label == "Vennskap" && it.books == 1 })
    }

    @Test fun sameSeriesCountsLikeAFavourite() {
        val reader = TasteProfile(listOf(Book(title = "Skyggedyr", authors = "Ulrika Rolfsdotter", rating = 5,
            genres = Genres.encode(Genres.traits(NbCatalog.parseMods(mods("mods-skyggedyr.xml")))))))
        val next = reader.score(candidate(NbDetails(listOf("Krim", "Romaner"), true, "nob", null, "Ny Forfatter", listOf("Annie Ljung (fiktiv person)"), "adult", "839.738")))
        assertTrue(next.reasons.contains(MatchReason.Series("Annie Ljung")))
    }

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
        assertTrue(cookbook.reasons.isEmpty())
    }

    @Test fun publisherPlaysNoPart() {
        val tags = Genres.tags(listOf("Krim"), true)
        val usual = profile.score(Candidate(record(publisher = "Aschehoug"), "Ny Forfatter", listOf("Krim"), tags, null))
        val unknown = profile.score(Candidate(record(publisher = "Ukjent Forlag"), "Ny Forfatter", listOf("Krim"), tags, null))
        val english = profile.score(Candidate(record(publisher = "Penguin").copy(language = "eng"), "Ny Forfatter", listOf("Crime"), tags, null))
        assertEquals(usual.percent, unknown.percent)
        assertEquals(usual.percent, english.percent)
    }

    @Test fun onlySubgenresTheUserReadsFit() {
        fun fits(vararg genres: String, author: String = "Ny Forfatter") =
            feelgoodReader.fitsGenres(Candidate(record(), author, genres.toList(), Genres.tags(genres.toList(), true), null))
        assertTrue(fits("Feelgood", "Romaner"))
        assertTrue("romance is close to feelgood", fits("Kjærlighetsromaner"))
        assertFalse(fits("Krim", "Romaner"))
        assertFalse("a plain novel's subgenre is unknown", fits("Romaner"))
        assertFalse(fits("Kokebøker"))
        assertTrue("a favourite author's new book always fits", fits("Krim", "Romaner", author = "Jenny Colgan"))

        val plainReader = TasteProfile(listOf(Book(title = "Roman", authors = "A", rating = 5, genres = Genres.encode(setOf("novel", Genres.FICTION)))))
        assertTrue(plainReader.fitsGenres(Candidate(record(), "B", listOf("Romaner"), Genres.tags(listOf("Romaner"), true), null)))
        assertTrue("no subgenres yet: the score decides", TasteProfile(emptyList()).fitsGenres(candidate("B", null, listOf("Krim"), true)))
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

    @Test fun genresTheUserDoesntReadCountAgainstABook() {
        val feelgood = TasteProfile(listOf(
            Book(title = "Det lille bakeriet", authors = "Jenny Colgan", rating = 5, genres = Genres.encode(setOf("feelgood", "novel", Genres.FICTION))),
        ))
        fun score(vararg genres: String) = feelgood.score(candidate("Ny Forfatter", null, genres.toList(), true)).percent
        val cosy = score("Feelgood", "Romaner")
        val plain = score("Romaner")
        val romance = score("Kjærlighetsromaner")
        val crime = score("Krim", "Romaner")
        assertTrue("$cosy > $plain", cosy > plain)
        assertTrue("$romance > $crime (romance is close to feelgood)", romance > crime)
        assertTrue("$plain > $crime", plain > crime)
    }

    @Test fun picksAuthorsAndGenresToSearch() {
        assertEquals("Jo Nesbø", profile.favouriteAuthors(3).first())
        assertFalse("Hellstrøm" in profile.favouriteAuthors(10))
        assertEquals("crime", profile.topGenres(3).first())
    }
}
