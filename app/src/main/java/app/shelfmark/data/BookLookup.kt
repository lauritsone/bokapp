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
        for (source in sources) {
            try {
                val info = source(isbn)
                reachedASource = true
                if (info != null) {
                    // Only keep the guessed Open Library cover if it really exists; otherwise
                    // the cover repair in BookRepository looks elsewhere later.
                    return@withContext if (info.coverUrl == null) {
                        info.copy(coverUrl = openLibraryCover(isbn).takeIf { runCatching { imageExists(it) }.getOrDefault(false) })
                    } else {
                        info
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
        val root = JSONObject(httpGet("https://www.googleapis.com/books/v1/volumes?q=isbn:$isbn"))
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
            httpGet("https://openlibrary.org/api/books?bibkeys=ISBN:$isbn&format=json&jscmd=data"),
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
        val root = JSONObject(httpGet("https://api.nb.no/catalog/v1/items?q=isbn:$isbn&size=5"))
        val items = root.optJSONObject("_embedded")?.optJSONArray("items") ?: return null
        val item = (0 until items.length()).map { items.getJSONObject(it) }.firstOrNull { item ->
            val isbns = item.optJSONObject("metadata")?.optJSONObject("identifiers")?.optJSONArray("isbn13")
            isbns != null && (0 until isbns.length()).any { isbns.optString(it) == isbn }
        } ?: return null
        val data = item.getJSONObject("metadata")
        val title = data.str("title") ?: return null
        val origin = data.optJSONObject("originInfo")
        // The full record names the main author apart from translators, and has the blurb.
        val details = runCatching { NbCatalog.parseMods(httpGet("https://api.nb.no/catalog/v1/metadata/${item.optString("id")}/mods")) }.getOrNull()
        return BookInfo(
            isbn = isbn,
            title = ReleaseFinder.displayTitle(title),
            authors = details?.author ?: data.optJSONArray("creators").names().firstOrNull()?.let(::personName).orEmpty(),
            publisher = origin?.str("publisher"),
            publishedDate = origin?.str("issued")?.take(4),
            pageCount = data.optInt("pageCount").takeIf { it > 0 },
            description = details?.summary,
            coverUrl = item.optJSONObject("_links")?.optJSONObject("thumbnail_large")?.str("href"),
        )
    }

    private fun fromLibris(isbn: String): BookInfo? {
        val root = JSONObject(httpGet("https://libris.kb.se/xsearch?query=isbn:$isbn&format=json"))
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
