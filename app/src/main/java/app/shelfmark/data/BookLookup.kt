package app.shelfmark.data

import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

data class BookInfo(
    val isbn: String,
    val title: String,
    val authors: String,
    val publisher: String?,
    val publishedDate: String?,
    val pageCount: Int?,
    val description: String?,
    val coverUrl: String?,
    /** The series ("Harry Hole") and the next book in it, when the catalogue knows. */
    val series: String? = null,
    val nextInSeries: String? = null,
)

/**
 * Looks up a book by ISBN. Tries Google Books first (best coverage + descriptions),
 * then Open Library, then the Norwegian (NB.no) and Swedish (Libris) national library
 * catalogues, which cover Nordic books the others often miss. None needs an API key.
 */
class BookLookup {

    /**
     * @return the book, or null if no source knows this ISBN.
     * @throws IOException if no source could be reached at all (offline, etc).
     */
    suspend fun lookup(isbn: String): BookInfo? = withContext(Dispatchers.IO) {
        var reachedASource = false
        val sources: List<(String) -> BookInfo?> = listOf(::fromGoogleBooks, ::fromOpenLibrary, ::fromNationalLibraryNorway, ::fromLibris)
        // On a poor connection each source can hang until its timeout; past this, stop trying.
        val giveUpAt = System.currentTimeMillis() + LOOKUP_BUDGET_MS
        for (source in sources) {
            if (System.currentTimeMillis() > giveUpAt) break
            try {
                val info = source(isbn)
                reachedASource = true
                if (info != null) {
                    // The National Library knows the series (and often has a Norwegian blurb)
                    // when another source answered first.
                    val nb = if (source != sources[2] && System.currentTimeMillis() < giveUpAt) {
                        runCatching { fromNationalLibraryNorway(isbn) }.getOrNull()
                    } else {
                        null
                    }
                    val withSeries = info.copy(
                        series = info.series ?: nb?.series,
                        nextInSeries = info.nextInSeries ?: nb?.nextInSeries,
                        description = info.description ?: nb?.description,
                        coverUrl = info.coverUrl ?: nb?.coverUrl,
                    )
                    // Only keep the guessed Open Library cover if it really exists; otherwise
                    // the cover repair in BookRepository looks elsewhere later.
                    return@withContext if (withSeries.coverUrl == null) {
                        withSeries.copy(coverUrl = openLibraryCover(isbn).takeIf { runCatching { imageExists(it) }.getOrDefault(false) })
                    } else {
                        withSeries
                    }
                }
            } catch (e: JSONException) {
                reachedASource = true
            } catch (e: IOException) {
                // try the next source
            }
        }
        if (!reachedASource) throw IOException("No book database could be reached")
        null
    }

    private fun fromGoogleBooks(isbn: String): BookInfo? {
        val root = JSONObject(httpGet("https://www.googleapis.com/books/v1/volumes?q=isbn:$isbn", TIMEOUT_MS))
        val items = root.optJSONArray("items") ?: return null
        if (items.length() == 0) return null
        val info = items.getJSONObject(0).getJSONObject("volumeInfo")
        val title = info.str("title") ?: return null
        val images = info.optJSONObject("imageLinks")
        val cover = (images?.str("thumbnail") ?: images?.str("smallThumbnail"))
            ?.replace("http://", "https://")
            ?.replace("&edge=curl", "")
        return BookInfo(
            isbn = isbn,
            title = title,
            authors = info.optJSONArray("authors").joinValues(),
            publisher = info.str("publisher"),
            publishedDate = info.str("publishedDate"),
            pageCount = info.optInt("pageCount").takeIf { it > 0 },
            description = info.str("description")?.let { stripHtml(it) },
            coverUrl = cover,
        )
    }

    private fun fromOpenLibrary(isbn: String): BookInfo? {
        val root = JSONObject(
            httpGet("https://openlibrary.org/api/books?bibkeys=ISBN:$isbn&format=json&jscmd=data", TIMEOUT_MS),
        )
        val data = root.optJSONObject("ISBN:$isbn") ?: return null
        val title = data.str("title") ?: return null
        val cover = data.optJSONObject("cover")?.let { it.str("large") ?: it.str("medium") }
        return BookInfo(
            isbn = isbn,
            title = title,
            authors = data.optJSONArray("authors").joinValues("name"),
            publisher = data.optJSONArray("publishers")?.optJSONObject(0)?.str("name"),
            publishedDate = data.str("publish_date"),
            pageCount = data.optInt("number_of_pages").takeIf { it > 0 },
            description = null,
            coverUrl = cover,
        )
    }

    private fun fromNationalLibraryNorway(isbn: String): BookInfo? {
        val root = JSONObject(httpGet("https://api.nb.no/catalog/v1/items?q=isbn:$isbn&size=5", TIMEOUT_MS))
        val items = root.optJSONObject("_embedded")?.optJSONArray("items") ?: return null
        val item = (0 until items.length()).map { items.getJSONObject(it) }.firstOrNull { item ->
            val isbns = item.optJSONObject("metadata")?.optJSONObject("identifiers")?.optJSONArray("isbn13")
            isbns != null && (0 until isbns.length()).any { isbns.optString(it) == isbn }
        } ?: return null
        val data = item.getJSONObject("metadata")
        val title = data.str("title") ?: return null
        val origin = data.optJSONObject("originInfo")
        // The full record names the main author apart from translators, and has the blurb.
        val details = runCatching { NbCatalog.parseMods(httpGet("https://api.nb.no/catalog/v1/metadata/${item.optString("id")}/mods", TIMEOUT_MS)) }.getOrNull()
        return BookInfo(
            isbn = isbn,
            title = ReleaseFinder.displayTitle(title),
            authors = details?.author ?: data.optJSONArray("creators").names().firstOrNull()?.let(::personName).orEmpty(),
            publisher = origin?.str("publisher"),
            publishedDate = origin?.str("issued")?.take(4),
            pageCount = data.optInt("pageCount").takeIf { it > 0 },
            description = details?.summary,
            coverUrl = item.optJSONObject("_links")?.optJSONObject("thumbnail_large")?.str("href"),
            series = details?.series,
            nextInSeries = details?.succeeding,
        )
    }

    private fun fromLibris(isbn: String): BookInfo? {
        val root = JSONObject(httpGet("https://libris.kb.se/xsearch?query=isbn:$isbn&format=json", TIMEOUT_MS))
        val list = root.optJSONObject("xsearch")?.optJSONArray("list") ?: return null
        val data = (0 until list.length()).map { list.getJSONObject(it) }.firstOrNull { record ->
            record.stringList("isbn").any { Isbn.normalize(it) == isbn }
        } ?: return null
        val title = data.str("title") ?: return null
        return BookInfo(
            isbn = isbn,
            title = title,
            authors = data.stringList("creator").joinToString(", ") { personName(it) },
            // Entries look like "Stockholm : Rabén & Sjögren"; keep the publisher's name.
            publisher = data.stringList("publisher")
                .map { it.substringAfter(" : ").trim(' ', ';', ':') }
                .firstOrNull { it.isNotBlank() },
            publishedDate = data.str("date"),
            pageCount = null,
            description = null,
            coverUrl = null,
        )
    }

    private companion object {
        /** Connect timeout per request (reads get 5 s more); the scan screen is waiting. */
        const val TIMEOUT_MS = 6_000

        /** No new source is tried after this long. */
        const val LOOKUP_BUDGET_MS = 20_000L
    }

    private fun openLibraryCover(isbn: String) =
        "https://covers.openlibrary.org/b/isbn/$isbn-L.jpg?default=false"

    private fun stripHtml(s: String): String =
        Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString().trim()
}

private fun JSONObject.str(key: String): String? =
    if (isNull(key)) null else optString(key).trim().ifBlank { null }

/** A field that may hold a single string or an array of strings. */
private fun JSONObject.stringList(key: String): List<String> =
    optJSONArray(key)?.names() ?: listOfNotNull(str(key))

private fun JSONArray?.names(): List<String> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).trim().ifBlank { null } }

/** Joins a JSON array of strings, or of objects (using [key]), into "A, B". */
private fun JSONArray?.joinValues(key: String? = null): String {
    if (this == null) return ""
    return (0 until length()).mapNotNull { i ->
        if (key == null) optString(i).trim().ifBlank { null } else optJSONObject(i)?.str(key)
    }.joinToString(", ")
}
