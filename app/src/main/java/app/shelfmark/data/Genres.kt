package app.shelfmark.data

/**
 * Maps catalogue genre words, Norwegian or English, onto shared tags, so "Krim",
 * "Kriminalromaner" and "Fiction, thrillers, crime" all count as crime.
 */
object Genres {
    const val FICTION = "fiction"
    const val NONFICTION = "nonfiction"

    /** Tag to the word stems (folded) that signal it. Checked in order; a genre may hit several. */
    private val stems = linkedMapOf(
        "crime" to listOf("krim", "crime", "detective", "detektiv", "mystery", "mysteries", "thriller", "spenning", "politiroman"),
        "fantasy" to listOf("fantasy"),
        "scifi" to listOf("science fiction", "sci fi", "dystop", "framtidsroman", "fremtidsroman"),
        "horror" to listOf("grosser", "horror", "skrekk"),
        "romance" to listOf("romance", "romantikk", "kjaerlighetsroman", "love stories"),
        "historical" to listOf("historiske romaner", "historical fiction", "historisk roman"),
        "novel" to listOf("roman", "novel"),
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

    /** Shared tags for a book's genre words, plus [FICTION] or [NONFICTION] when known. */
    fun tags(genres: Collection<String>, fiction: Boolean?): Set<String> {
        val tags = mutableSetOf<String>()
        for (genre in genres) {
            val folded = foldText(genre)
            if (folded.isEmpty()) continue
            val hits = stems.filterValues { list -> list.any { it in folded } }.keys
            // Unknown genres still match the same word on other books.
            tags += hits.ifEmpty { setOf(folded) }
        }
        when (fiction) {
            true -> tags += FICTION
            false -> tags += NONFICTION
            null -> if (tags.any { it in fictionTags }) tags += FICTION
        }
        return tags
    }

    private val fictionTags = setOf("crime", "fantasy", "scifi", "horror", "romance", "historical", "novel", "shortstories")

    fun encode(tags: Set<String>): String = tags.sorted().joinToString("|")

    fun decode(value: String?): Set<String> = value?.split("|")?.filter { it.isNotEmpty() }?.toSet().orEmpty()
}
