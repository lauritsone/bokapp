package app.shelfmark.data

import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

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
 * then falls back to Open Library. Neither needs an API key.
 */
class BookLookup {

    /**
     * @return the book, or null if no source knows this ISBN.
     * @throws IOException if no source could be reached at all (offline, etc).
     */
    suspend fun lookup(isbn: String): BookInfo? = withContext(Dispatchers.IO) {
        var reachedASource = false
        val sources: List<(String) -> BookInfo?> = listOf(::fromGoogleBooks, ::fromOpenLibrary)
        for (source in sources) {
            try {
                val info = source(isbn)
                reachedASource = true
                if (info != null) {
                    return@withContext if (info.coverUrl == null) {
                        info.copy(coverUrl = openLibraryCover(isbn))
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

    private fun openLibraryCover(isbn: String) =
        "https://covers.openlibrary.org/b/isbn/$isbn-L.jpg?default=false"

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        conn.setRequestProperty("User-Agent", "Shelfmark/1.0 (Android reading log)")
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code from $url")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun stripHtml(s: String): String =
        Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString().trim()
}

private fun JSONObject.str(key: String): String? =
    if (isNull(key)) null else optString(key).trim().ifBlank { null }

/** Joins a JSON array of strings, or of objects (using [key]), into "A, B". */
private fun JSONArray?.joinValues(key: String? = null): String {
    if (this == null) return ""
    return (0 until length()).mapNotNull { i ->
        if (key == null) optString(i).trim().ifBlank { null } else optJSONObject(i)?.str(key)
    }.joinToString(", ")
}
