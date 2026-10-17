package app.shelfmark.data

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

private const val USER_AGENT = "Shelfmark/1.0 (Android reading log)"

/** GETs [url] as text. @throws IOException on network errors or a non-2xx answer. */
internal fun httpGet(url: String, timeoutMs: Int = 10_000): String {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = timeoutMs
    conn.readTimeout = timeoutMs + 5_000
    conn.setRequestProperty("User-Agent", USER_AGENT)
    try {
        val code = conn.responseCode
        if (code !in 200..299) throw IOException("HTTP $code from $url")
        return conn.inputStream.bufferedReader().use { it.readText() }
    } finally {
        conn.disconnect()
    }
}

/**
 * Whether [url] serves an image. Cover services answer 404 (or an error page) when they
 * have no cover, so a cover link is only kept after this check.
 * @throws IOException if the server can't be reached at all.
 */
internal fun imageExists(url: String): Boolean {
    // Only the headers are needed; the image itself is fetched once, when it is shown.
    val head = imageCheck(url, "HEAD")
    // A few servers don't answer HEAD; ask for the image itself then.
    return head ?: (imageCheck(url, "GET") == true)
}

/** Whether [url] answers [method] with an image; null if the server doesn't accept the method. */
private fun imageCheck(url: String, method: String): Boolean? {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.requestMethod = method
    conn.connectTimeout = 8_000
    conn.readTimeout = 8_000
    conn.instanceFollowRedirects = true
    conn.setRequestProperty("User-Agent", USER_AGENT)
    try {
        val code = conn.responseCode
        if (method == "HEAD" && (code == 405 || code == 501)) return null
        // A HEAD answer often has no length (-1); only an empty image (0) says there is none.
        return code in 200..299 && conn.contentType.orEmpty().startsWith("image/") && conn.contentLength != 0
    } finally {
        conn.disconnect()
    }
}

/**
 * Catalogue answers kept on disk for [maxAgeMs], so a search repeated after the app restarts
 * needn't ask again. Android may clear [dir] when space runs low; that only costs a request.
 */
class DiskCache internal constructor(private val dir: File, private val maxAgeMs: Long) {

    /** The cached answer for [url], or [httpGet]'s, which is then kept. */
    fun get(url: String, timeoutMs: Int = 10_000): String {
        val file = File(dir, sha1(url))
        if (file.isFile && System.currentTimeMillis() - file.lastModified() < maxAgeMs) {
            try {
                return file.readText()
            } catch (e: IOException) {
                // Unreadable: ask the catalogue instead.
            }
        }
        val text = httpGet(url, timeoutMs)
        try {
            dir.mkdirs()
            // Written to a file of its own and renamed, so a half-written answer is never read
            // back, even when two searches fetch the same address at once.
            val partial = File.createTempFile(file.name, ".part", dir).apply { writeText(text) }
            if (!partial.renameTo(file)) partial.delete()
        } catch (e: IOException) {
            // A full disk just means no cache.
        }
        return text
    }

    fun forget(url: String) {
        File(dir, sha1(url)).delete()
    }

    /** Deletes answers too old to be used. */
    fun prune() {
        val now = System.currentTimeMillis()
        dir.listFiles()?.filter { now - it.lastModified() >= maxAgeMs }?.forEach { it.delete() }
    }

    private fun sha1(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
