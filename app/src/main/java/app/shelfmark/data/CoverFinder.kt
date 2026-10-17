package app.shelfmark.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import org.json.JSONException
import java.net.URLEncoder


/**
 * Finds a cover for a book that has none, or only an unverified guess: the National
 * Library (best for Norwegian books), then Open Library and Google Books, by ISBN or,
 * without one, by title and author. Only the picture is taken: a title match may be
 * another edition, so its ISBN would be wrong for the user's copy.
 */
class CoverFinder(private val nb: NbCatalog) {

    /**
     * @throws IOException if a source couldn't be reached and none had a cover, so the book
     * is tried again later instead of being marked as having none.
     */
    suspend fun find(book: Book): String? = withContext(Dispatchers.IO) {
        var failed = false
        suspend fun attempt(block: suspend () -> String?): String? = try {
            block()
        } catch (e: IOException) {
            failed = true
            null
        } catch (e: JSONException) {
            null
        }

        val isbn = book.isbn?.let { Isbn.normalize(it) }
        val author = TasteProfile.authorNames(book.authors).firstOrNull()
        val found = if (isbn != null) {
            attempt { nb.findByIsbn(isbn)?.coverUrl }
                ?: attempt { openLibraryByIsbn(isbn) }
                ?: attempt { googleByIsbn(isbn) }
        } else {
            null
        } ?: if (book.title.isNotBlank() && author != null) {
            attempt { nbByTitle(book.title, author) }
                ?: attempt { openLibraryByTitle(book.title, author) }
        } else {
            null
        }
        if (found == null && failed) throw IOException("A cover source couldn't be reached")
        found
    }

    private fun openLibraryByIsbn(isbn: String): String? =
        "https://covers.openlibrary.org/b/isbn/$isbn-L.jpg?default=false".takeIf(::imageExists)

    private fun googleByIsbn(isbn: String): String? {
        val items = JSONObject(httpGet("https://www.googleapis.com/books/v1/volumes?q=isbn:$isbn")).optJSONArray("items")
        val images = items?.optJSONObject(0)?.optJSONObject("volumeInfo")?.optJSONObject("imageLinks") ?: return null
        return (images.optString("thumbnail").ifEmpty { images.optString("smallThumbnail") }).ifEmpty { null }
            ?.replace("http://", "https://")?.replace("&edge=curl", "")
    }

    private suspend fun nbByTitle(title: String, author: String): String? {
        val wanted = foldText(title)
        return nb.search("\"$title\" \"$author\"", size = 10, norwegianOnly = false)
            .filter { ReleaseFinder.wroteBy(it, author) && foldText(it.title).startsWith(wanted) }
            .firstNotNullOfOrNull { it.coverUrl }
    }

    private fun openLibraryByTitle(title: String, author: String): String? {
        val url = "https://openlibrary.org/search.json?title=" + enc(title) + "&author=" + enc(author) +
            "&fields=title,cover_i&limit=5"
        val docs = JSONObject(httpGet(url, timeoutMs = 15_000)).optJSONArray("docs") ?: return null
        val wanted = foldText(title)
        for (i in 0 until docs.length()) {
            val d = docs.getJSONObject(i)
            if (!foldText(d.optString("title")).startsWith(wanted)) continue
            val cover = d.optInt("cover_i").takeIf { it > 0 } ?: continue
            return "https://covers.openlibrary.org/b/id/$cover-L.jpg"
        }
        return null
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    companion object {
        /** Cover links that were only guessed from the ISBN and may not exist. */
        fun isGuess(url: String?) = url != null && "covers.openlibrary.org/b/isbn/" in url
    }
}
