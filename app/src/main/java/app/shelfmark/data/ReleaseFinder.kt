package app.shelfmark.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** How far along a search for new releases is, for the progress text. */
sealed interface ReleaseProgress {
    data class LearningTaste(val done: Int, val total: Int) : ReleaseProgress
    data object Searching : ReleaseProgress
    data class ReadingDetails(val done: Int, val total: Int) : ReleaseProgress
}

/** [ignored] is how many books the user left out of the taste profile. */
data class ReleaseResult(val matches: List<Match>, val profileSize: Int, val ignored: Int = 0)

/**
 * Finds new books: Norwegian ones in the National Library's catalogue (recent books by
 * the user's favourite authors, newest books from the publishers they read most) and
 * English ones on Open Library (new books by those authors, and the most-read new books
 * in their favourite genres). Each is scored against a [TasteProfile] of their library.
 */
class ReleaseFinder(private val dao: BookDao, private val nb: NbCatalog, private val ol: OlCatalog) {

    private val detailsCache = ConcurrentHashMap<String, NbDetails>()
    private val firstYearsCache = ConcurrentHashMap<String, Map<String, Int>>()

    /** @throws IOException if the catalogue can't be reached at all. */
    suspend fun find(onProgress: (ReleaseProgress) -> Unit): ReleaseResult {
        learnGenres(onProgress)
        val library = dao.getAll()
        val profile = TasteProfile(library)
        val year = LocalDate.now().year

        onProgress(ReleaseProgress.Searching)
        val failures = AtomicInteger()
        val searches = mutableListOf<suspend () -> List<CatalogRecord>>()
        for (author in profile.favouriteAuthors(limit = 8)) {
            for (y in listOf(year, year - 1)) {
                searches += { nb.search("\"$author\"", year = y, size = 20).filter { wroteBy(it, author) } }
            }
        }
        // The newest Norwegian books in the user's favourite genres (feelgood, crime …).
        for (genre in profile.topGenres(limit = 3).mapNotNull { nbSubjects[it] }) {
            searches += { nb.search("subject:\"$genre\"", year = year, size = 60).newest(20) }
        }
        for (publisher in profile.favouritePublishers(limit = 3)) {
            searches += { nb.search("publisher:\"$publisher\"", year = year, size = 60).newest(15) }
        }
        // English: the same authors' new books, and the most-read new books in the user's genres.
        for (author in profile.favouriteAuthors(limit = 6)) {
            searches += { ol.search("author:\"$author\"", fromYear = year - 1, limit = 5).filter { wroteBy(it, author) } }
        }
        val subjects = profile.topGenres(limit = 3).mapNotNull { olSubjects[it] }.ifEmpty { listOf("fiction") }
        for (subject in subjects) {
            searches += { ol.search("subject:\"$subject\"", fromYear = year - 1, limit = 12, popular = true) }
        }
        if (profile.favouriteAuthors(1).isEmpty() && profile.favouritePublishers(1).isEmpty()) {
            // Nothing to go on yet: show this year's newest Norwegian books too.
            searches += { nb.search("*", year = year, size = 60).newest(25) }
        }
        val found = concurrently(searches) { search ->
            try {
                search()
            } catch (e: IOException) {
                failures.incrementAndGet()
                emptyList()
            }
        }.flatten()
        if (failures.get() == searches.size) throw IOException("The library catalogue couldn't be reached")

        val owned = library.mapNotNull { it.isbn }.toSet()
        val ownedTitles = library.map { titleKey(it.title) }.toSet()
        val fresh = found
            .filter { it.isbn !in owned && titleKey(it.title) !in ownedTitles }
            // The same book is often catalogued again a year later; keep the first.
            .sortedWith(compareBy({ it.year ?: Int.MAX_VALUE }, { -it.indexedAt }))
            .distinctBy { titleKey(it.title) + "|" + it.creators.firstOrNull()?.let(::foldText) + "|" + it.language }
            .take(80)

        val done = AtomicInteger()
        onProgress(ReleaseProgress.ReadingDetails(0, fresh.size))
        val candidates = concurrently(fresh) { record ->
            val details = try {
                cachedDetails(record)
            } catch (e: IOException) {
                null
            }
            onProgress(ReleaseProgress.ReadingDetails(done.incrementAndGet(), fresh.size))
            details?.let { toCandidate(record, it) }
        }.filterNotNull()

        // New editions of old books ("Rødstrupe", 2026) aren't new releases; each author's
        // bibliography shows which titles they published before.
        val scored = candidates.map(profile::score).sortedByDescending { it.percent }
        val matches = concurrently(scored) { match -> match.takeUnless { isReissue(it.candidate, year) } }.filterNotNull()
            // Poor matches only clutter the list, but a thin library should still see something.
            .let { all -> all.filter { it.percent >= MIN_PERCENT }.takeIf { it.size >= 10 } ?: all.take(10) }
        return ReleaseResult(matches, profile.bookCount, library.count { it.ignoreForSuggestions })
    }

    /** Looks up genres for library books that don't have them yet, a batch at a time. */
    private suspend fun learnGenres(onProgress: (ReleaseProgress) -> Unit) {
        val pending = dao.getAll().filter { !it.ignoreForSuggestions && it.profiledAt < Genres.RULES_SINCE }.take(40)
        if (pending.isEmpty()) return
        val done = AtomicInteger()
        onProgress(ReleaseProgress.LearningTaste(0, pending.size))
        concurrently(pending) { book ->
            try {
                val tags = genresFor(book)
                dao.setGenres(book.id, tags.takeIf { it.isNotEmpty() }?.let(Genres::encode), System.currentTimeMillis())
            } catch (e: IOException) {
                // Offline or catalogue hiccup: try this book again next time.
            }
            onProgress(ReleaseProgress.LearningTaste(done.incrementAndGet(), pending.size))
        }
    }

    /** Whether the author's bibliography has this title from before last year. */
    private suspend fun isReissue(c: Candidate, year: Int): Boolean {
        if (c.record.knownFirstEdition) return false
        val author = c.record.creators.firstOrNull() ?: return false
        val firstYears = try {
            firstYearsCache[author] ?: firstYears(author).also { firstYearsCache[author] = it }
        } catch (e: IOException) {
            return false
        }
        val first = firstYears[titleKey(c.record.title)] ?: return false
        return first < year - 1
    }

    /** Title → the earliest year the catalogue has it, for one author's books in Norwegian. */
    private suspend fun firstYears(author: String): Map<String, Int> {
        val name = personName(author)
        return nb.search("\"$name\"", size = 100)
            .filter { wroteBy(it, name) && it.year != null }
            .groupBy { titleKey(it.title) }
            .mapValues { (_, records) -> records.minOf { it.year!! } }
    }

    private suspend fun genresFor(book: Book): Set<String> {
        val wanted = titleKey(book.title)
        val author = TasteProfile.authorNames(book.authors).firstOrNull()
        val record = book.isbn?.let { nb.findByIsbn(it) }
            ?: author?.let {
                nb.search("\"${book.title}\" \"$it\"", size = 10)
                    // Same author (not a book about it), and the title may carry a subtitle.
                    .firstOrNull { r ->
                        wroteBy(r, it) && (titleKey(r.title) == wanted || foldText(r.title).startsWith(foldText(book.title)))
                    }
            }
            // Not in the catalogue: the blurb from the book lookup can still tell the mood.
            ?: return Genres.traits(NbDetails(emptyList(), null, null, null, null), book.description)
        val details = cachedDetails(record)
        return Genres.traits(details, details.summary ?: book.description)
    }

    private suspend fun cachedDetails(record: CatalogRecord): NbDetails =
        detailsCache[record.id] ?: fetchDetails(record).also { detailsCache[record.id] = it }

    private suspend fun fetchDetails(record: CatalogRecord): NbDetails =
        if (record.id.startsWith(OlCatalog.ID_PREFIX)) {
            // Open Library's subjects came with the search; only the description needs a request.
            val folded = record.subjects.map(::foldText)
            NbDetails(
                genres = record.subjects,
                fiction = when {
                    folded.any { "nonfiction" in it.replace(" ", "") } -> false
                    folded.any { "fiction" in it } -> true
                    else -> null
                },
                language = record.language,
                summary = ol.description(record),
                author = record.creators.firstOrNull(),
            )
        } else {
            nb.details(record.id)
        }

    fun toCandidate(record: CatalogRecord, details: NbDetails) = Candidate(
        record = record.copy(title = displayTitle(record.title)),
        author = details.author ?: record.creators.firstOrNull()?.let(::personName).orEmpty(),
        genreLabels = details.genres,
        tags = Genres.traits(details),
        summary = details.summary,
        topicLabels = details.topics,
    )

    private fun List<CatalogRecord>.newest(n: Int) = sortedByDescending { it.indexedAt }.take(n)

    private suspend fun <T, R> concurrently(items: List<T>, parallel: Int = 6, block: suspend (T) -> R): List<R> =
        coroutineScope {
            val permits = Semaphore(parallel)
            items.map { async { permits.withPermit { block(it) } } }.awaitAll()
        }

    companion object {
        /** Matches below this are left out, as long as enough better ones remain. */
        const val MIN_PERCENT = 30

        /** NB.no's subject word for each genre tag. */
        private val nbSubjects = mapOf(
            "feelgood" to "feelgood", "crime" to "krim", "fantasy" to "fantasy", "scifi" to "science fiction",
            "horror" to "grøsser", "romance" to "romantikk", "historical" to "historiske romaner",
            "shortstories" to "noveller", "poetry" to "dikt", "biography" to "biografier", "history" to "historie",
            "essays" to "essays", "humor" to "humor", "selfhelp" to "selvhjelp", "cooking" to "kokebøker",
            "travel" to "reiseskildringer", "nature" to "natur", "sport" to "idrett", "comics" to "tegneserier",
            "children" to "barnebøker", "youngadult" to "ungdomsromaner",
        )

        /** Open Library subject to search for each genre tag. */
        private val olSubjects = mapOf(
            "crime" to "crime", "fantasy" to "fantasy", "scifi" to "science fiction", "horror" to "horror",
            "romance" to "romance", "historical" to "historical fiction", "shortstories" to "short stories",
            "poetry" to "poetry", "children" to "juvenile fiction", "youngadult" to "young adult fiction",
            "biography" to "biography", "history" to "history", "essays" to "essays", "humor" to "humor",
            "selfhelp" to "self-help", "cooking" to "cooking", "travel" to "travel", "nature" to "nature",
            "sport" to "sports", "comics" to "graphic novels", "novel" to "fiction",
            "feelgood" to "humorous fiction",
        )

        /** Subtitles that only name the form, as in "Minnesota : roman". */
        private val formSubtitles = setOf("roman", "krim", "krimroman", "kriminalroman", "noveller", "dikt", "thriller", "spenningsroman", "novel")

        /** "Minnesota : roman" → "Minnesota"; "Epstein : døde menn snakker ikke" → "Epstein: døde menn snakker ikke". */
        fun displayTitle(title: String): String {
            val clean = title.replace(Regex("\\s+"), " ").trim()
            val main = clean.substringBefore(" : ")
            val sub = clean.substringAfter(" : ", "")
            return when {
                sub.isEmpty() -> main
                foldText(sub) in formSubtitles -> main
                else -> "$main: $sub"
            }
        }

        /** The main title, folded, for telling whether two records are the same book. */
        fun titleKey(title: String) = foldText(displayTitle(title).substringBefore(": "))

        /** Whether the record lists [author] among its creators ("Nesbø, Jo" matches "Jo Nesbø"). */
        fun wroteBy(record: CatalogRecord, author: String): Boolean {
            val wanted = foldText(author).split(' ').toSet()
            return record.creators.any { foldText(it).split(' ').toSet().containsAll(wanted) }
        }
    }
}
