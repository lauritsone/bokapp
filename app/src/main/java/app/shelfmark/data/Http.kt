package app.shelfmark.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

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
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 8_000
    conn.readTimeout = 8_000
    conn.instanceFollowRedirects = true
    conn.setRequestProperty("User-Agent", USER_AGENT)
    try {
        val code = conn.responseCode
        return code in 200..299 && conn.contentType.orEmpty().startsWith("image/") && conn.contentLength != 0
    } finally {
        conn.disconnect()
    }
}
