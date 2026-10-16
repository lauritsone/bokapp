package app.shelfmark.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class ExportFormat(val mimeType: String, val extension: String) {
    /** Opens in Excel, Google Sheets or Numbers. */
    Csv("text/csv", "csv"),

    /** Every field, for backups or other apps. */
    Json("application/json", "json"),
}

/** Turns the library into a file the user can keep or open elsewhere. */
object LibraryExport {

    fun fileName(format: ExportFormat, today: LocalDate = LocalDate.now()) =
        "shelfmark-$today.${format.extension}"

    fun write(format: ExportFormat, books: List<Book>, out: OutputStream) = when (format) {
        ExportFormat.Csv -> writeCsv(books, out)
        ExportFormat.Json -> writeJson(books, out)
    }

    private fun writeCsv(books: List<Book>, out: OutputStream) {
        val header = listOf(
            "title", "authors", "shelf", "rating", "notes", "isbn",
            "publisher", "published", "pages", "added", "updated", "cover_url",
        )
        val writer = out.bufferedWriter(Charsets.UTF_8)
        // The byte-order mark makes Excel read æ, ø and å correctly.
        writer.write(0xFEFF)
        writer.write(header.joinToString(",") + "\r\n")
        for (b in books) {
            val row = listOf(
                b.title, b.authors, b.shelf.key, b.rating.takeIf { it > 0 }?.toString(), b.notes, b.isbn,
                b.publisher, b.publishedDate, b.pageCount?.toString(), localDate(b.addedAt), localDate(b.updatedAt), b.coverUrl,
            )
            writer.write(row.joinToString(",") { csvField(it.orEmpty()) } + "\r\n")
        }
        writer.flush()
    }

    private fun writeJson(books: List<Book>, out: OutputStream) {
        val array = JSONArray()
        for (b in books) {
            array.put(
                JSONObject()
                    .put("title", b.title)
                    .put("authors", b.authors)
                    .put("shelf", b.shelf.key)
                    .put("rating", b.rating)
                    .put("notes", b.notes)
                    .putOpt("isbn", b.isbn)
                    .putOpt("publisher", b.publisher)
                    .putOpt("publishedDate", b.publishedDate)
                    .putOpt("pageCount", b.pageCount)
                    .putOpt("description", b.description)
                    .putOpt("coverUrl", b.coverUrl)
                    .put("addedAt", Instant.ofEpochMilli(b.addedAt).toString())
                    .put("updatedAt", Instant.ofEpochMilli(b.updatedAt).toString()),
            )
        }
        val root = JSONObject()
            .put("app", "Shelfmark")
            .put("formatVersion", 1)
            .put("exportedAt", Instant.now().toString())
            .put("books", array)
        val writer = out.bufferedWriter(Charsets.UTF_8)
        writer.write(root.toString(2))
        writer.flush()
    }

    private fun localDate(epochMillis: Long) =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    /** Quotes a field when it holds a comma, quote or line break (RFC 4180). */
    private fun csvField(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}
