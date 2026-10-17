package app.shelfmark.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant

/** A book found in a catalogue (NB.no or Open Library), as its search returns it. */
data class CatalogRecord(
    val id: String,
    val title: String,
    val creators: List<String>,
    val publisher: String?,
    val year: Int?,
    val isbn: String?,
    val pageCount: Int?,
    val coverUrl: String?,
    /** When the library catalogued it; the best "how new is it" signal the API offers. */
    val indexedAt: Long,
    /** ISO 639-2 code: "nob" for NB.no's Bokmål search, "eng" for English editions. */
    val language: String = "nob",
    /** Subjects that came with the search result (Open Library); NB.no's come with [NbDetails]. */
    val subjects: List<String> = emptyList(),
    /** Whether the catalogue already knows this is the first printing (Open Library's first publish year). */
    val knownFirstEdition: Boolean = false,
)

/** The parts of a full catalogue record (MODS) that say what kind of book it is. */
data class NbDetails(
    /** Genre words as catalogued, e.g. "Krim", "Romaner". */
    val genres: List<String>,
    /** true for fiction, false for non-fiction, null if the record doesn't say. */
    val fiction: Boolean?,
    val language: String?,
    val summary: String?,
    /** Main author from the statement of responsibility, without translators. */
    val author: String?,
    /** Subject words, e.g. "Vennskap", "Sorg", "Maria Wern (fiktiv person)". */
    val topics: List<String> = emptyList(),
    /** "adult" or "juvenile" when the record says. */
    val audience: String? = null,
    /** Dewey number, e.g. "839.8238" (Norwegian fiction) or "641.5" (cooking). */
    val dewey: String? = null,
    /** The book before and after this one in its series, by title, when the record says. */
    val preceding: String? = null,
    val succeeding: String? = null,
) {
    /** The series, named after its recurring character ("Harry Hole"), if the record has exactly one. */
    val series: String?
        get() = topics.filter { it.endsWith("(fiktiv person)") }
            .map { personName(it.removeSuffix("(fiktiv person)").trim()) }.distinct().singleOrNull()
}

/**
 * Client for the parts of api.nb.no that Shelfmark uses. No API key needed. Calls marked
 * `cached` may be answered from [cache], for lookups whose answers hardly change.
 */
class NbCatalog(private val cache: DiskCache? = null) {

    /** Books matching [query] (NB search syntax), optionally limited to one year and Bokmål. */
    suspend fun search(
        query: String,
        year: Int? = null,
        size: Int = 40,
        norwegianOnly: Boolean = true,
        page: Int = 0,
        cached: Boolean = false,
    ): List<CatalogRecord> =
        withContext(Dispatchers.IO) {
            val params = buildList {
                add("q=" + enc(query))
                add("filter=" + enc("mediatype:bøker"))
                year?.let { add("filter=" + enc("year:$it")) }
                if (norwegianOnly) add("filter=" + enc("languages:nob"))
                add("size=$size")
                if (page > 0) add("page=$page")
            }
            fetch("$BASE/items?" + params.joinToString("&"), cached, ::parseSearch)
        }

    suspend fun findByIsbn(isbn: String, cached: Boolean = false): CatalogRecord? =
        search("isbn:$isbn", size = 5, norwegianOnly = false, cached = cached).firstOrNull { it.isbn == isbn }

    suspend fun details(id: String, cached: Boolean = false): NbDetails = withContext(Dispatchers.IO) {
        fetch("$BASE/metadata/$id/mods", cached, ::parseMods)
    }

    /** Old cached answers are deleted. */
    fun pruneCache() {
        cache?.prune()
    }

    private suspend fun <T> fetch(url: String, cached: Boolean, parse: (String) -> T): T {
        val text = if (cached && cache != null) cache.get(url) else httpGet(url)
        return try {
            parse(text)
        } catch (e: Exception) {
            // An odd answer isn't kept, so the next try asks again.
            cache?.forget(url)
            throw e
        }
    }

    companion object {
        private const val BASE = "https://api.nb.no/catalog/v1"

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

        fun parseSearch(json: String): List<CatalogRecord> {
            val items = JSONObject(json).optJSONObject("_embedded")?.optJSONArray("items") ?: return emptyList()
            return (0 until items.length()).mapNotNull { i ->
                val item = items.getJSONObject(i)
                val m = item.optJSONObject("metadata") ?: return@mapNotNull null
                val title = m.optString("title").trim().ifEmpty { null } ?: return@mapNotNull null
                val origin = m.optJSONObject("originInfo")
                val creators = m.optJSONArray("creators")
                    ?.let { a -> (0 until a.length()).map { a.optString(it).trim() }.filter { it.isNotEmpty() } }
                    .orEmpty()
                CatalogRecord(
                    id = item.optString("id"),
                    title = title,
                    creators = creators,
                    publisher = origin?.optString("publisher")?.trim()?.ifEmpty { null },
                    year = origin?.optString("issued")?.take(4)?.toIntOrNull(),
                    isbn = m.optJSONObject("identifiers")?.optJSONArray("isbn13")?.optString(0)?.ifEmpty { null },
                    pageCount = m.optInt("pageCount").takeIf { it > 0 },
                    coverUrl = item.optJSONObject("_links")?.optJSONObject("thumbnail_large")?.optString("href")?.ifEmpty { null },
                    indexedAt = origin?.optString("firstIndexTime")
                        ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L,
                )
            }
        }

        private fun element(name: String) = Regex("<(?:mods:)?$name(\\s[^>]*)?>([^<]*)</(?:mods:)?$name>")

        // Built once: a search for new releases reads a few hundred records.
        private val genreElement = element("genre")
        private val abstractElement = element("abstract")
        private val noteElement = element("note")
        private val topicElement = element("topic")
        private val audienceElement = element("targetAudience")
        private val languagePattern = Regex("<(?:mods:)?language>\\s*<(?:mods:)?languageTerm[^>]*>([^<]+)<")
        private val deweyPattern = Regex("<(?:mods:)?classification[^>]*authority=\"ddc\"[^>]*>\\s*([0-9][0-9.]*)")
        private val relatedPatterns = listOf("preceding", "succeeding").associateWith { type ->
            Regex("<(?:mods:)?relatedItem type=\"$type\">\\s*<(?:mods:)?titleInfo>\\s*<(?:mods:)?title>([^<]+)<")
        }
        private val byPrefix = Regex("^(av|by|tekst:?|skrevet av)\\s+", RegexOption.IGNORE_CASE)
        private val numericEntity = Regex("&#([xX][0-9a-fA-F]+|\\d+);")

        /** Reads the few MODS fields we need with simple patterns, so it also runs in plain JVM tests. */
        fun parseMods(xml: String): NbDetails {
            val genreTags = genreElement.findAll(xml).map { it.groupValues[1] to unescape(it.groupValues[2]).trim() }.toList()
            // Plain <genre> elements hold the subject genres; ones with attributes are format codes.
            val genres = genreTags.filter { (attrs, _) -> attrs.isBlank() }.map { it.second }.filter { it.isNotEmpty() }.distinct()
            val forms = genreTags.filter { (attrs, _) -> "marcgt" in attrs || "literaryform" in attrs }.map { it.second.lowercase() }
            val fiction = when {
                forms.any { it == "fiction" || it == "novel" } -> true
                forms.any { "non" in it } -> false
                else -> null
            }
            // The first <language> block is the book's own; later ones (objectPart) are originals.
            val language = languagePattern.find(xml)?.groupValues?.get(1)?.trim()
            val summary = abstractElement.find(xml)?.groupValues?.get(2)?.let(::unescape)?.trim()?.ifEmpty { null }
            val responsibility = noteElement.findAll(xml)
                .firstOrNull { "statement of responsibility" in it.groupValues[1] }
                ?.groupValues?.get(2)?.let(::unescape)
            val topics = topicElement.findAll(xml).map { unescape(it.groupValues[2]).trim() }.filter { it.isNotEmpty() }.distinct().toList()
            val audience = audienceElement.find(xml)?.groupValues?.get(2)?.let(::audienceOf)
            val dewey = deweyPattern.find(xml)?.groupValues?.get(1)
            fun related(type: String) = relatedPatterns.getValue(type)
                .find(xml)?.groupValues?.get(1)?.let(::unescape)?.trim()?.ifEmpty { null }
            return NbDetails(
                genres, fiction, language, summary, responsibility?.let(::mainAuthor), topics, audience, dewey,
                preceding = related("preceding"),
                succeeding = related("succeeding"),
            )
        }

        /** "adult", "Voksne", "juvenile", "Barn" … as "adult" or "juvenile". */
        private fun audienceOf(value: String): String? = foldText(value).let {
            when {
                "juvenile" in it || "barn" in it || "ungdom" in it -> "juvenile"
                "adult" in it || "voksn" in it -> "adult"
                else -> null
            }
        }

        /** "Ulrika Rolfsdotter ; oversatt av Nina Wessel" → "Ulrika Rolfsdotter". */
        private fun mainAuthor(statement: String): String? =
            statement.split(";").first()
                .replace(byPrefix, "")
                .trim().trimEnd('.', ',').ifEmpty { null }

        private fun unescape(s: String) = s
            .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
            .replace(numericEntity) { m ->
                val code = m.groupValues[1].let { if (it[0] == 'x' || it[0] == 'X') it.drop(1).toIntOrNull(16) else it.toIntOrNull() }
                code?.takeIf(Character::isValidCodePoint)?.let { String(Character.toChars(it)) } ?: m.value
            }
            .replace("&amp;", "&")
    }
}

/** Catalogues write names as "Nesbø, Jo" or "Lindgren, Astrid, 1907-2002"; shows "Jo Nesbø". */
fun personName(name: String): String {
    val parts = name.split(",").map { it.trim() }
    if (parts.size < 2 || parts[1].isEmpty() || parts[1].first().isDigit()) return parts[0]
    return "${parts[1]} ${parts[0]}"
}
