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
)

/** Client for the parts of api.nb.no that Shelfmark uses. No API key needed. */
class NbCatalog {

    /** Books matching [query] (NB search syntax), optionally limited to one year and Bokmål. */
    suspend fun search(query: String, year: Int? = null, size: Int = 40, norwegianOnly: Boolean = true, page: Int = 0): List<CatalogRecord> =
        withContext(Dispatchers.IO) {
            val params = buildList {
                add("q=" + enc(query))
                add("filter=" + enc("mediatype:bøker"))
                year?.let { add("filter=" + enc("year:$it")) }
                if (norwegianOnly) add("filter=" + enc("languages:nob"))
                add("size=$size")
                if (page > 0) add("page=$page")
            }
            parseSearch(httpGet("$BASE/items?" + params.joinToString("&")))
        }

    suspend fun findByIsbn(isbn: String): CatalogRecord? =
        search("isbn:$isbn", size = 5, norwegianOnly = false).firstOrNull { it.isbn == isbn }

    suspend fun details(id: String): NbDetails = withContext(Dispatchers.IO) {
        parseMods(httpGet("$BASE/metadata/$id/mods"))
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

        /** Reads the few MODS fields we need with simple patterns, so it also runs in plain JVM tests. */
        fun parseMods(xml: String): NbDetails {
            val genreTags = element("genre").findAll(xml).map { it.groupValues[1] to unescape(it.groupValues[2]).trim() }.toList()
            // Plain <genre> elements hold the subject genres; ones with attributes are format codes.
            val genres = genreTags.filter { (attrs, _) -> attrs.isBlank() }.map { it.second }.filter { it.isNotEmpty() }.distinct()
            val forms = genreTags.filter { (attrs, _) -> "marcgt" in attrs || "literaryform" in attrs }.map { it.second.lowercase() }
            val fiction = when {
                forms.any { it == "fiction" || it == "novel" } -> true
                forms.any { "non" in it } -> false
                else -> null
            }
            // The first <language> block is the book's own; later ones (objectPart) are originals.
            val language = Regex("<(?:mods:)?language>\\s*<(?:mods:)?languageTerm[^>]*>([^<]+)<").find(xml)?.groupValues?.get(1)?.trim()
            val summary = element("abstract").find(xml)?.groupValues?.get(2)?.let(::unescape)?.trim()?.ifEmpty { null }
            val responsibility = element("note").findAll(xml)
                .firstOrNull { "statement of responsibility" in it.groupValues[1] }
                ?.groupValues?.get(2)?.let(::unescape)
            val topics = element("topic").findAll(xml).map { unescape(it.groupValues[2]).trim() }.filter { it.isNotEmpty() }.distinct().toList()
            val audience = element("targetAudience").find(xml)?.groupValues?.get(2)?.let(::audienceOf)
            val dewey = Regex("<(?:mods:)?classification[^>]*authority=\"ddc\"[^>]*>\\s*([0-9][0-9.]*)").find(xml)?.groupValues?.get(1)
            return NbDetails(genres, fiction, language, summary, responsibility?.let(::mainAuthor), topics, audience, dewey)
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
                .replace(Regex("^(av|by|tekst:?|skrevet av)\\s+", RegexOption.IGNORE_CASE), "")
                .trim().trimEnd('.', ',').ifEmpty { null }

        private fun unescape(s: String) = s
            .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
            .replace(Regex("&#(\\d+);")) { it.groupValues[1].toInt().toChar().toString() }
            .replace("&amp;", "&")
    }
}

/** Catalogues write names as "Nesbø, Jo" or "Lindgren, Astrid, 1907-2002"; shows "Jo Nesbø". */
fun personName(name: String): String {
    val parts = name.split(",").map { it.trim() }
    if (parts.size < 2 || parts[1].isEmpty() || parts[1].first().isDigit()) return parts[0]
    return "${parts[1]} ${parts[0]}"
}
