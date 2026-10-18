package app.shelfmark.data

import android.text.Html
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
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
 * Looks up a book by ISBN. Google Books (best coverage and descriptions), Open Library and
 * the Norwegian national library ([nb]) are asked at once; the first of them to know the
 * book wins, in that order, and NB.no adds the series and a Norwegian blurb. The Swedish
 * national library (Libris) is asked only when none of them knows it. None needs an API key.
 */
class BookLookup(private val nb: NbCatalog) {

    /**
     * @return the book, or null if no source knows this ISBN.
     * @throws IOException if no source could be reached at all (offline, etc).
     */
    suspend fun lookup(isbn: String): BookInfo? = withContext(Dispatchers.IO) {
        val reached = AtomicBoolean(false)
        // On a poor connection a source can hang until its timeout; past this, it is given up.
        val giveUpAt = System.currentTimeMillis() + LOOKUP_BUDGET_MS
        suspend fun attempt(source: suspend (String) -> BookInfo?): BookInfo? = try {
            withTimeout((giveUpAt - System.currentTimeMillis()).coerceAtLeast(1)) { source(isbn) }
                .also { reached.set(true) }
        } catch (e: TimeoutCancellationException) {
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: JSONException) {
            reached.set(true)
            null
        } catch (e: IOException) {
            null
        }

        val info = coroutineScope {
            val google = async { attempt(::fromGoogleBooks) }
            val openLibrary = async { attempt(::fromOpenLibrary) }
            val norway = async { attempt(::fromNationalLibraryNorway) }
            val first = google.await() ?: openLibrary.await()
            // Google answered: Open Library's copy isn't needed.
            openLibrary.cancel()
            // With a book in hand, NB.no only adds the series and a blurb: not worth a long wait.
            val fromNb = if (first == null) norway.await() else withTimeoutOrNull(NB_EXTRA_WAIT_MS) { norway.await() }
            norway.cancel()
            when {
                first != null -> first.copy(
                    series = first.series ?: fromNb?.series,
                    nextInSeries = first.nextInSeries ?: fromNb?.nextInSeries,
                    description = first.description ?: fromNb?.description,
                    coverUrl = first.coverUrl ?: fromNb?.coverUrl,
                )
                fromNb != null -> fromNb
                else -> attempt(::fromLibris)
            }
        }
        if (info == null) {
            if (!reached.get()) throw IOException("No book database could be reached")
            return@withContext null
        }
        // Only keep the guessed Open Library cover if it really exists; otherwise
        // the cover repair in BookRepository looks elsewhere later.
        if (info.coverUrl == null) {
            info.copy(coverUrl = openLibraryCover(isbn).takeIf { runCatching { imageExists(it) }.getOrDefault(false) })
        } else {
            info
        }
    }

    private suspend fun fromGoogleBooks(isbn: String): BookInfo? {
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

    private suspend fun fromOpenLibrary(isbn: String): BookInfo? {
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

    private suspend fun fromNationalLibraryNorway(isbn: String): BookInfo? {
        // Asked fresh: a book new to NB.no shows up there within days.
        val record = nb.findByIsbn(isbn) ?: return null
        // The full record names the main author apart from translators, and has the blurb.
        val details = try {
            nb.details(record.id, cached = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        return BookInfo(
            isbn = isbn,
            title = ReleaseFinder.displayTitle(record.title),
            authors = details?.author ?: record.creators.firstOrNull()?.let(::personName).orEmpty(),
            publisher = record.publisher,
            publishedDate = record.year?.toString(),
            pageCount = record.pageCount,
            description = details?.summary,
            coverUrl = record.coverUrl,
            series = details?.series,
            nextInSeries = details?.succeeding,
        )
    }

    private suspend fun fromLibris(isbn: String): BookInfo? {
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

        /** How long NB.no is waited for once another source has found the book. */
        const val NB_EXTRA_WAIT_MS = 3_000L
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
