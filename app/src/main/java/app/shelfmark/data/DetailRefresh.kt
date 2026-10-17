package app.shelfmark.data

/** What looking up a book's missing details came to. */
sealed interface RefreshResult {
    /** [filled] details were added. */
    data class Updated(val filled: Int) : RefreshResult
    /** Found in the catalogue, but it had nothing the book was missing. */
    data object NothingNew : RefreshResult
    data object NotFound : RefreshResult
    /** No author to go by, and several authors have written a book with this title. */
    data object Ambiguous : RefreshResult
}

/** The rules for refreshing a book's details, kept apart from the network so they can be tested. */
object DetailRefresh {

    /** Whether a looked-up title is the book's own (a subtitle on either side is fine). */
    fun sameTitle(own: String, found: String): Boolean {
        val a = ReleaseFinder.titleKey(own)
        val b = ReleaseFinder.titleKey(found)
        return a.isNotEmpty() && (a == b || a.startsWith(b) || b.startsWith(a))
    }

    /**
     * The catalogue edition that is this book: same title, and when [byAuthor] is false (the
     * search wasn't narrowed to the author) only if one author has a book by that title, one
     * who shares a name with [author] when that was typed (a misspelt first name, say).
     * Editions with an ISBN win, since they bring a cover and the most details.
     */
    fun pick(title: String, records: List<CatalogRecord>, byAuthor: Boolean, author: String = ""): PickResult {
        val wanted = ReleaseFinder.titleKey(title)
        val typedNames = nameParts(author)
        val same = records.filter { r ->
            ReleaseFinder.titleKey(r.title) == wanted &&
                (byAuthor || typedNames.isEmpty() || r.creators.any { c -> nameParts(personName(c)).any(typedNames::contains) })
        }
        if (same.isEmpty()) return PickResult.None
        if (!byAuthor) {
            val authors = same.mapNotNull { r -> r.creators.firstOrNull()?.let { foldText(personName(it)) } }.distinct()
            if (authors.size > 1) return PickResult.Several
        }
        return PickResult.One(same.firstOrNull { it.isbn != null } ?: same.first())
    }

    private fun nameParts(name: String) = foldText(name).split(' ').filter { it.length >= 3 }.toSet()

    sealed interface PickResult {
        data class One(val record: CatalogRecord) : PickResult
        data object None : PickResult
        data object Several : PickResult
    }

    /**
     * [book] with its gaps filled from [info]. What the user typed or picked stays as it is;
     * only a guessed cover link is replaced. [isbnTaken] keeps an ISBN another book in the
     * library already has from being given to this one.
     */
    fun fillGaps(book: Book, info: BookInfo, isbnTaken: Boolean): Book = book.copy(
        isbn = book.isbn ?: info.isbn.ifBlank { null }?.takeUnless { isbnTaken },
        authors = book.authors.ifBlank { info.authors },
        publisher = book.publisher?.ifBlank { null } ?: info.publisher,
        publishedDate = book.publishedDate?.ifBlank { null } ?: info.publishedDate,
        pageCount = book.pageCount ?: info.pageCount,
        description = book.description?.ifBlank { null } ?: info.description,
        coverUrl = if (book.coverUrl == null || CoverFinder.isGuess(book.coverUrl)) info.coverUrl ?: book.coverUrl else book.coverUrl,
    )

    /** How many details [after] has that [before] lacked. */
    fun filled(before: Book, after: Book): Int = listOf(
        before.isbn != after.isbn,
        before.authors != after.authors,
        before.publisher != after.publisher,
        before.publishedDate != after.publishedDate,
        before.pageCount != after.pageCount,
        before.description != after.description,
        before.coverUrl != after.coverUrl,
    ).count { it }
}
