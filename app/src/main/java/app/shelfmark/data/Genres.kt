package app.shelfmark.data

/**
 * Maps catalogue genre words, Norwegian or English, onto shared tags, so "Krim",
 * "Kriminalromaner" and "Fiction, thrillers, crime" all count as crime.
 */
object Genres {
    const val FICTION = "fiction"
    const val NONFICTION = "nonfiction"

    /** When the tagging rules last changed; books tagged before this are tagged again. */
    const val RULES_SINCE = 1_792_238_400_000L // 2026-10-17 12:00 UTC

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

    // ---------- Traits beyond genre ----------
    // Stored with the genre tags, told apart by a prefix.

    /** A subject word from the catalogue: "topic:vennskap". */
    const val TOPIC = "topic:"

    /** A recurring character, i.e. a series: "series:maria wern". */
    const val SERIES = "series:"

    /** Mood: light (feelgood, humour, romance), dark (crime, violence) or serious (grief, prize-winning literary). */
    const val TONE = "tone:"
    const val LIGHT = "tone:light"
    const val DARK = "tone:dark"
    const val SERIOUS = "tone:serious"

    const val JUVENILE = "audience:juvenile"
    const val ADULT = "audience:adult"

    /** Which literature a novel comes from, by its Dewey number: "lit:norwegian". */
    const val LITERATURE = "lit:"

    /** Non-fiction subject by Dewey class: "dewey:641" is food and cooking. */
    const val DEWEY = "dewey:"

    /** Whether [tag] is a genre (crime, novel, a catalogue word, a Dewey class) rather than another trait. */
    fun isGenre(tag: String) = tag != FICTION && tag != NONFICTION &&
        listOf(TOPIC, SERIES, TONE, "audience:", LITERATURE).none { tag.startsWith(it) }

    /**
     * Everything a catalogue record says about what kind of book it is: genre tags, subjects,
     * series, mood, audience and origin. [summary] is the blurb, used to judge the mood.
     */
    fun traits(details: NbDetails, summary: String? = details.summary): Set<String> {
        val tags = tags(details.genres, details.fiction).toMutableSet()
        val topics = details.topics.map(::foldText).filter { it.isNotEmpty() }
        for (topic in topics) {
            tags += if (topic.endsWith(" fiktiv person")) SERIES + topic.removeSuffix(" fiktiv person") else TOPIC + topic
        }
        when {
            details.audience == "juvenile" || "children" in tags || "youngadult" in tags -> tags += JUVENILE
            details.audience == "adult" -> tags += ADULT
        }
        details.dewey?.let { dewey ->
            if (dewey.startsWith("8")) literature(dewey)?.let { tags += LITERATURE + it }
            else if (dewey.length >= 3 && FICTION !in tags) tags += DEWEY + dewey.take(3)
        }
        tags += tones(tags, topics, summary?.let(::foldText).orEmpty())
        return tags
    }

    /** Dewey 800s are literature by language: 823 English, 839.82 Norwegian, 839.7 Swedish … */
    fun literature(dewey: String): String? = when {
        dewey.startsWith("839.82") || dewey.startsWith("839.83") -> "norwegian"
        dewey.startsWith("839.81") -> "danish"
        dewey.startsWith("839.7") -> "swedish"
        dewey.startsWith("839.69") -> "icelandic"
        dewey.startsWith("839.3") -> "dutch"
        dewey.startsWith("81") -> "american"
        dewey.startsWith("82") -> "english"
        dewey.startsWith("83") -> "german"
        dewey.startsWith("84") -> "french"
        dewey.startsWith("85") -> "italian"
        dewey.startsWith("86") -> "spanish"
        dewey.startsWith("891.7") -> "russian"
        dewey.startsWith("895.6") -> "japanese"
        dewey.length >= 3 -> dewey.take(3)
        else -> null
    }

    // Folded word stems; a stem matches at the start of a word.
    private val lightWords = listOf(
        "feelgood", "feel good", "hjertevarm", "humoristisk", "morsom", "sjarmerende", "koselig", "livsbejaende",
        "romantisk komedie", "lun humor", "heartwarming", "uplifting", "romantic comed", "charming", "cozy", "cosy",
        "humorous", "hilarious", "love stories", "chick lit",
    )
    private val darkWords = listOf(
        "drap", "drept", "mord", "morder", "seriemord", "etterforsk", "voldtekt", "overgrep", "psykopat", "kidnapp",
        "thriller", "murder", "killer", "detective", "serial kill", "psychological",
    )
    private val seriousWords = listOf(
        "sorg", "doden", "selvmord", "traume", "abort", "eksistensiell", "nobelpris", "prisbelont", "prisvinnende",
        "litteraturpris", "kritikerpris", "brageprisen", "kritikerrost", "grief", "booker", "pulitzer",
    )
    private val seriousTopics = setOf("doden", "sorg", "selvmord", "abort", "overgrep", "traumer", "incest", "psykiske lidelser")

    private fun tones(tags: Set<String>, topics: List<String>, summary: String): Set<String> {
        val text = " $summary "
        fun mentions(words: List<String>) = words.any { " $it" in text }
        val tones = mutableSetOf<String>()
        if (tags.any { it == "feelgood" || it == "romance" || it == "humor" } || mentions(lightWords)) tones += LIGHT
        if (tags.any { it == "crime" || it == "horror" } || mentions(darkWords)) tones += DARK
        // A feelgood novel may well deal with grief; its genre says more than the topic.
        if (LIGHT !in tones && (topics.any { it in seriousTopics } || mentions(seriousWords))) tones += SERIOUS
        return tones
    }

    fun encode(tags: Set<String>): String = tags.sorted().joinToString("|")

    fun decode(value: String?): Set<String> = value?.split("|")?.filter { it.isNotEmpty() }?.toSet().orEmpty()
}
