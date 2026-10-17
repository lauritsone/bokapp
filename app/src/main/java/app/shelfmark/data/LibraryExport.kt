package app.shelfmark.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

enum class ExportFormat(val mimeType: String, val extension: String) {
    /** Opens in Excel, Google Sheets or Numbers. */
    Csv("text/csv", "csv"),

    /** Every field, for other apps. */
    Json("application/json", "json"),

    /** Every field plus the user's own cover photos, to restore the library on another phone. */
    Backup("application/zip", "zip"),
}

/** Turns the library into a file the user can keep or open elsewhere. */
object LibraryExport {

    fun fileName(format: ExportFormat, today: LocalDate = LocalDate.now()) =
        "shelfmark-$today.${format.extension}"

    /** Writes [books] to [out]; a [ExportFormat.Backup] also packs their cover photos. */
    fun write(format: ExportFormat, books: List<Book>, out: OutputStream) = when (format) {
        ExportFormat.Csv -> writeCsv(books, out)
        ExportFormat.Json -> writeJson(books, out)
        ExportFormat.Backup -> writeBackup(books, out)
    }

    /** books.json, with each photo at the path its book's "photo" names, e.g. photos/12.jpg. */
    private fun writeBackup(books: List<Book>, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            val photos = mutableMapOf<Long, String>()
            for (b in books) {
                val file = b.photoPath?.let(::File)?.takeIf { it.isFile } ?: continue
                val name = "${LibraryBackup.PHOTOS}${b.id}.jpg"
                zip.putNextEntry(ZipEntry(name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                photos[b.id] = name
            }
            zip.putNextEntry(ZipEntry(LibraryBackup.BOOKS))
            // writeJson closes nothing, so the zip can still be finished.
            writeJson(books, zip, photos)
            zip.closeEntry()
        }
    }

    private fun writeCsv(books: List<Book>, out: OutputStream) {
        val header = listOf(
            "title", "authors", "shelf", "rating", "notes", "isbn",
            "publisher", "published", "pages", "added", "updated", "cover_url", "finished", "ignore_for_suggestions", "description",
            "started", "current_page", "series", "series_number", "format", "rereads", "lent_to", "lent_at", "tags",
        )
        val writer = out.bufferedWriter(Charsets.UTF_8)
        // The byte-order mark makes Excel read æ, ø and å correctly.
        writer.write(0xFEFF)
        writer.write(header.joinToString(",") + "\r\n")
        for (b in books) {
            val row = listOf(
                b.title, b.authors, b.shelf.key, b.rating.takeIf { it > 0 }?.toString(), b.notes, null,
                b.publisher, b.publishedDate, b.pageCount?.toString(), localTime(b.addedAt), localTime(b.updatedAt), b.coverUrl,
                b.finishedAt?.let(::localTime), if (b.ignoreForSuggestions) "yes" else null, b.description,
                b.startedAt?.let(::localTime), b.currentPage?.toString(), b.series, b.seriesNumber, b.format.key,
                b.rereads.takeIf { it > 0 }?.toString(), b.lentTo, b.lentAt?.let(::localTime), b.tagList().joinToString(", ").ifEmpty { null },
            ).map { csvField(it.orEmpty()) }.toMutableList()
            // Written as ="978…" so Excel keeps it as text instead of 9,78821E+12.
            b.isbn?.let { row[5] = "=\"$it\"" }
            writer.write(row.joinToString(",") + "\r\n")
        }
        writer.flush()
    }

    private fun writeJson(books: List<Book>, out: OutputStream, photos: Map<Long, String> = emptyMap()) {
        val writer = out.bufferedWriter(Charsets.UTF_8)
        // Written a book at a time, so a large library is never one huge string in memory.
        writer.write("{\n\"app\": \"Shelfmark\",\n\"formatVersion\": 2,\n")
        writer.write("\"exportedAt\": " + JSONObject.quote(Instant.now().toString()) + ",\n\"books\": [\n")
        books.forEachIndexed { i, b ->
            if (i > 0) writer.write(",\n")
            writer.write(bookJson(b, photos).toString(2))
        }
        writer.write("\n]\n}\n")
        writer.flush()
    }

    private fun bookJson(b: Book, photos: Map<Long, String>): JSONObject =
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
            .put("updatedAt", Instant.ofEpochMilli(b.updatedAt).toString())
            .putOpt("finishedAt", b.finishedAt?.let { Instant.ofEpochMilli(it).toString() })
            .put("ignoreForSuggestions", b.ignoreForSuggestions)
            .putOpt("startedAt", b.startedAt?.let { Instant.ofEpochMilli(it).toString() })
            .putOpt("currentPage", b.currentPage)
            .putOpt("series", b.series)
            .putOpt("seriesNumber", b.seriesNumber)
            .put("format", b.format.key)
            .put("rereads", b.rereads)
            .putOpt("lentTo", b.lentTo)
            .putOpt("lentAt", b.lentAt?.let { Instant.ofEpochMilli(it).toString() })
            .putOpt("tags", b.tagList().takeIf { it.isNotEmpty() }?.let { JSONArray(it) })
            // Saves looking the genres up again after a restore.
            .putOpt("genres", b.genres)
            .putOpt("photo", photos[b.id])

    /** Local date and time to the second, so books added the same day keep their order. */
    private fun localTime(epochMillis: Long) =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
            .truncatedTo(ChronoUnit.SECONDS).format(localTimeFormat)

    private val localTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** Quotes a field when it holds a comma, quote or line break (RFC 4180). */
    private fun csvField(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}
