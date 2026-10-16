package app.shelfmark.data

/** A new book to suggest, with what its catalogue record says about it. */
data class Candidate(
    val record: NbRecord,
    val author: String,
    /** Genre words as catalogued, for display. */
    val genreLabels: List<String>,
    val tags: Set<String>,
    val summary: String?,
)

/** Why a book got its score; shown under the match percentage. */
sealed interface MatchReason {
    data class Author(val name: String, val rating: Int?) : MatchReason
    data class Genre(val label: String, val books: Int) : MatchReason
    data class Publisher(val name: String) : MatchReason
}

data class Match(val candidate: Candidate, val percent: Int, val reasons: List<MatchReason>)

/**
 * What the user likes, learned from their library: authors, genres and publishers,
 * each weighted by how the user rated those books. Scores new books against it.
 */
class TasteProfile(books: List<Book>) {

    private class AuthorStat(val name: String, var weight: Double = 0.0, var ratingSum: Int = 0, var rated: Int = 0)

    private val authors = mutableMapOf<String, AuthorStat>()
    private val genreWeights = mutableMapOf<String, Double>()
    private val genreCounts = mutableMapOf<String, Int>()
    private val publisherWeights = mutableMapOf<String, Double>()
    private val publisherNames = mutableMapOf<String, String>()
    private var fictionWeight = 0.0
    private var nonfictionWeight = 0.0

    /** Books that taught the profile something; few means the scores are rough. */
    val bookCount: Int

    init {
        var counted = 0
        for (book in books) {
            val w = weight(book)
            if (w == 0.0) continue
            counted++
            for (name in authorNames(book.authors)) {
                val stat = authors.getOrPut(foldText(name)) { AuthorStat(name) }
                stat.weight += w
                if (book.rating > 0) {
                    stat.ratingSum += book.rating
                    stat.rated++
                }
            }
            val tags = Genres.decode(book.genres)
            for (tag in tags - setOf(Genres.FICTION, Genres.NONFICTION)) {
                genreWeights[tag] = (genreWeights[tag] ?: 0.0) + w
                genreCounts[tag] = (genreCounts[tag] ?: 0) + 1
            }
            if (w > 0) {
                if (Genres.FICTION in tags) fictionWeight += w
                if (Genres.NONFICTION in tags) nonfictionWeight += w
            }
            book.publisher?.takeIf { publisherKey(it).isNotEmpty() }?.let { name ->
                val key = publisherKey(name)
                publisherWeights[key] = (publisherWeights[key] ?: 0.0) + w
                publisherNames.getOrPut(key) { name.trim() }
            }
        }
        bookCount = counted
    }

    /** Authors to look up new books from, best liked first. */
    fun favouriteAuthors(limit: Int): List<String> =
        authors.values.filter { it.weight > 0.3 }.sortedByDescending { it.weight }.take(limit).map { it.name }

    /** Publishers to look up new books from, most read first, as the user's books spell them. */
    fun favouritePublishers(limit: Int): List<String> =
        publisherWeights.filterValues { it > 0.3 }.entries.sortedByDescending { it.value }.take(limit)
            .map { publisherNames.getValue(it.key) }

    fun score(c: Candidate): Match {
        val reasons = mutableListOf<MatchReason>()

        // Author: one 5-star book by them is full marks; a disliked author pulls the score down.
        val author = authorNames(c.author).mapNotNull { authors[foldText(it)] }.maxByOrNull { it.weight }
        val authorScore = (author?.weight ?: 0.0).coerceIn(-1.0, 1.0)
        if (author != null && authorScore > 0.2) {
            reasons += MatchReason.Author(author.name, if (author.rated > 0) Math.round(author.ratingSum.toDouble() / author.rated).toInt() else null)
        }

        // Genre: how much the user reads the candidate's best-matching genres, relative to their top genre.
        val maxGenre = genreWeights.values.maxOrNull()?.takeIf { it > 0 }
        val genreHits = (c.tags - setOf(Genres.FICTION, Genres.NONFICTION))
            .map { tag -> tag to (if (maxGenre == null) 0.0 else ((genreWeights[tag] ?: 0.0) / maxGenre).coerceIn(0.0, 1.0)) }
            .sortedByDescending { it.second }
        // Nearly everything is a "novel", so it counts for less; a specific genre the
        // user reads (crime, fantasy …) adds a bonus on top.
        val genreScore = when {
            maxGenre == null || genreHits.isEmpty() -> 0.25
            else -> {
                val best = genreHits.maxOf { (tag, a) -> if (tag == "novel") a * 0.7 else a }
                val bestSpecific = genreHits.filter { it.first != "novel" }.maxOfOrNull { it.second } ?: 0.0
                (best + 0.3 * bestSpecific).coerceAtMost(1.0)
            }
        }
        // "Novel" says little; name a more telling genre when one matches too.
        val telling = genreHits.filter { it.second >= 0.3 }
        (telling.firstOrNull { it.first != "novel" } ?: telling.firstOrNull())?.let { (tag, _) ->
            reasons += MatchReason.Genre(labelFor(tag, c), genreCounts[tag] ?: 0)
        }

        // Fiction or not, in the proportion the user reads it.
        val known = fictionWeight + nonfictionWeight
        val fictionShare = if (known > 0) fictionWeight / known else 0.5
        val formScore = when {
            Genres.FICTION in c.tags -> fictionShare
            Genres.NONFICTION in c.tags -> 1 - fictionShare
            else -> 0.5
        }

        val maxPublisher = publisherWeights.values.maxOrNull()?.takeIf { it > 0 }
        val publisherScore = c.record.publisher?.let { publisherKey(it) }
            ?.let { key -> if (maxPublisher == null) 0.0 else ((publisherWeights[key] ?: 0.0) / maxPublisher).coerceIn(0.0, 1.0) }
            ?: 0.0
        if (publisherScore >= 0.3) reasons += MatchReason.Publisher(c.record.publisher!!)

        var score = 0.40 * authorScore.coerceAtLeast(0.0) + 0.30 * genreScore + 0.15 * formScore + 0.15 * publisherScore
        if (authorScore < 0) score *= 0.4
        return Match(c, (score * 100).toInt().coerceIn(1, 99), reasons)
    }

    /** The candidate's own genre word for a tag, e.g. "Krim" rather than "crime". */
    private fun labelFor(tag: String, c: Candidate): String =
        c.genreLabels.firstOrNull { Genres.tags(listOf(it), null).contains(tag) } ?: tag

    companion object {
        /** How much a book says about the user's taste. */
        fun weight(book: Book): Double = when {
            book.rating > 0 -> when (book.rating) {
                1 -> -0.6
                2 -> -0.1
                3 -> 0.4
                4 -> 0.8
                else -> 1.0
            }
            book.shelf == Shelf.Reading -> 0.6
            else -> 0.5
        }

        fun authorNames(authors: String): List<String> =
            authors.split(",", ";", "&", " og ", " and ").map { it.trim() }.filter { it.length > 1 }

        /** "Cappelen Damm AS" and "Cappelen Damm" are the same publisher. */
        fun publisherKey(name: String): String =
            foldText(name).removeSuffix(" as").removeSuffix(" forlag").removeSuffix(" asa").trim()
    }
}
