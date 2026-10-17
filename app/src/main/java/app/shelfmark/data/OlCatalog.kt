package app.shelfmark.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Open Library (openlibrary.org) for new English books. Searches return works with
 * their English edition, subjects and first publication year, so reprints are easy
 * to tell apart. No API key needed.
 */
class OlCatalog {

    /**
     * English books first published in [fromYear] or later that match [query]
     * (Open Library search syntax). [popular] sorts by how many readers logged them.
     */
    suspend fun search(query: String, fromYear: Int, limit: Int, popular: Boolean = false): List<CatalogRecord> =
        withContext(Dispatchers.IO) {
            val q = "$query first_publish_year:[$fromYear TO *] language:eng"
            val url = "$BASE/search.json?q=" + enc(q) +
                "&fields=" + enc(FIELDS) + "&limit=$limit" + (if (popular) "&sort=readinglog" else "")
            parseSearch(httpGet(url, timeoutMs = 15_000))
        }

    /** The work's description, if Open Library has one. */
    suspend fun description(record: CatalogRecord): String? = withContext(Dispatchers.IO) {
        val key = record.id.removePrefix(ID_PREFIX)
        val json = JSONObject(httpGet("$BASE$key.json"))
        val d = json.opt("description")
        val text = when (d) {
            is JSONObject -> d.optString("value")
            is String -> d
            else -> null
        }
        text?.substringBefore("\n----")?.substringBefore("([source]")
            ?.replace("**", "")?.replace(Regex("(?<!\\w)\\*|\\*(?!\\w)"), "")
            ?.trim()
            // Some descriptions are only in the original language's script (e.g. Japanese).
            ?.takeIf { t -> t.count { it.isLetter() && it.code < 0x250 } >= t.count { it.isLetter() } * 0.8 }
            ?.ifEmpty { null }
    }

    companion object {
        private const val BASE = "https://openlibrary.org"
        const val ID_PREFIX = "ol:"
        private const val FIELDS = "key,title,author_name,first_publish_year,subject,cover_i,publisher," +
            "editions,editions.title,editions.language,editions.isbn,editions.cover_i,editions.publisher,editions.number_of_pages_median"

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

        fun parseSearch(json: String): List<CatalogRecord> {
            val docs = JSONObject(json).optJSONArray("docs") ?: return emptyList()
            return (0 until docs.length()).mapNotNull { i ->
                val w = docs.getJSONObject(i)
                // The search asks for language:eng, so the matching edition is the English one.
                val e = w.optJSONObject("editions")?.optJSONArray("docs")?.optJSONObject(0)
                val title = (e?.optString("title")?.trim()?.ifEmpty { null } ?: w.optString("title").trim()).ifEmpty { null } ?: return@mapNotNull null
                val isbns = e?.optJSONArray("isbn").strings()
                val coverId = e?.optInt("cover_i")?.takeIf { it > 0 } ?: w.optInt("cover_i").takeIf { it > 0 }
                CatalogRecord(
                    id = ID_PREFIX + w.optString("key"),
                    title = title,
                    creators = w.optJSONArray("author_name").strings(),
                    publisher = (e?.optJSONArray("publisher") ?: w.optJSONArray("publisher")).strings().firstOrNull(),
                    year = w.optInt("first_publish_year").takeIf { it > 0 },
                    isbn = isbns.firstOrNull { it.length == 13 } ?: isbns.firstOrNull()?.let { Isbn.normalize(it) },
                    pageCount = null,
                    coverUrl = coverId?.let { "https://covers.openlibrary.org/b/id/$it-L.jpg" },
                    indexedAt = 0,
                    language = "eng",
                    subjects = cleanSubjects(w.optJSONArray("subject").strings()),
                    knownFirstEdition = true,
                )
            }
        }

        /** Drops Open Library's catalogue codes ("nyt:…", "serie:…") and keeps the genre words. */
        fun cleanSubjects(subjects: List<String>): List<String> =
            subjects.mapNotNull { s ->
                val t = s.removePrefix("genre:").trim()
                t.takeUnless { ':' in it || '=' in it || it.isEmpty() }
            }.distinct().take(12)

        private fun JSONArray?.strings(): List<String> =
            if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).trim().ifEmpty { null } }
    }
}
