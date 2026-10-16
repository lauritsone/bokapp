package app.shelfmark.data

/**
 * Maps catalogue genre words, Norwegian or English, onto shared tags, so "Krim",
 * "Kriminalromaner" and "Fiction, thrillers, crime" all count as crime.
 */
object Genres {
    const val FICTION = "fiction"
    const val NONFICTION = "nonfiction"

    /** When the tagging rules last changed; books tagged before this are tagged again. */
    const val RULES_SINCE = 1_792_143_000_000L // 2026-10-16 09:30 UTC

    /** Tag to the word stems (folded) that signal it. A genre may hit several. */
    private val stems = linkedMapOf(
        "crime" to listOf("krim", "crime", "detective", "detektiv", "mystery", "mysteries", "thriller", "spenning", "politiroman"),
        "fantasy" to listOf("fantasy"),
        "scifi" to listOf("science fiction", "sci fi", "dystop", "framtidsroman", "fremtidsroman"),
        "horror" to listOf("grosser", "horror", "skrekk"),
        "feelgood" to listOf("feelgood", "feel good", "chick lit"),
        "romance" to listOf("romance", "romantikk", "kjaerlighetsroman", "love stories"),
        "historical" to listOf("historiske romaner", "historical fiction", "historisk roman"),
        // Not plain "roman": that would also catch "romance" and "romantikk".
        "novel" to listOf(),
        "shortstories" to listOf("novelle", "short stor"),
        "poetry" to listOf("dikt", "poetry", "poems", "lyrikk"),
        "children" to listOf("barnebok", "barneboker", "bildebok", "bildeboker", "children", "juvenile", "picture book"),
        "youngadult" to listOf("ungdomsbok", "ungdomsboker", "ungdomsroman", "young adult"),
        "biography" to listOf("biografi", "biography", "memoar", "memoir", "erindring"),
        "history" to listOf("historie", "history"),
        "essays" to listOf("essay"),
        "humor" to listOf("humor", "humour"),
        "selfhelp" to listOf("selvhjelp", "self help"),
        "cooking" to listOf("kokebok", "kokeboker", "cooking", "oppskrift"),
        "travel" to listOf("reise", "travel"),
        "nature" to listOf("natur", "nature"),
        "sport" to listOf("sport", "fotball", "football"),
        "comics" to listOf("tegneserie", "comic", "graphic novel"),
    )

    /** "Romaner", "Kriminalroman", "Novel", "Fiction", "Literary fiction", but not "Romance" or "Nonfiction". */
    private val novelPattern = Regex("roman(er|en|ene)?\\b|\\bnovels?\\b|^(literary |general |contemporary )?fiction$|^fiction ")
    private val nonfictionPattern = Regex("\\bnon ?fiction\\b|\\bsakprosa\\b|\\bfaglitteratur\\b")

    /** Shared tags for a book's genre words, plus [FICTION] or [NONFICTION] when known. */
    fun tags(genres: Collection<String>, fiction: Boolean?): Set<String> {
        val tags = mutableSetOf<String>()
        var saysNonfiction = false
        for (genre in genres) {
            val folded = foldText(genre)
            if (folded.isEmpty()) continue
            if (nonfictionPattern.containsMatchIn(folded)) {
                saysNonfiction = true
                continue
            }
            val hits = stems.filterValues { list -> list.any { it in folded } }.keys.toMutableSet()
            if (novelPattern.containsMatchIn(folded)) hits += "novel"
            // Unknown genres still match the same word on other books.
            tags += hits.ifEmpty { setOf(folded) }
        }
        if (fiction == null && saysNonfiction) {
            tags += NONFICTION
            return tags
        }
        when (fiction) {
            true -> tags += FICTION
            false -> tags += NONFICTION
            null -> if (tags.any { it in fictionTags }) tags += FICTION
        }
        return tags
    }

    /**
     * Whether [tag] is a genre we know (crime, fantasy …) rather than the catch-all "novel"
     * or a word copied from the catalogue as is.
     */
    fun isSpecific(tag: String) = tag != "novel" && tag in stems

    /** Genres close enough that liking one says something about the other. */
    val related: Map<String, Set<String>> = mapOf(
        "feelgood" to setOf("romance"),
        "romance" to setOf("feelgood"),
        "fantasy" to setOf("scifi"),
        "scifi" to setOf("fantasy"),
    )

    private val fictionTags = setOf("feelgood", "crime", "fantasy", "scifi", "horror", "romance", "historical", "novel", "shortstories")

    fun encode(tags: Set<String>): String = tags.sorted().joinToString("|")

    fun decode(value: String?): Set<String> = value?.split("|")?.filter { it.isNotEmpty() }?.toSet().orEmpty()
}
