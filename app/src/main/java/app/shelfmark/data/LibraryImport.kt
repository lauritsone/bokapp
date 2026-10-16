package app.shelfmark.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException

class ImportException(message: String) : Exception(message)

/** Spreadsheet apps often start UTF-8 files with this invisible mark. */
private const val ByteOrderMark = '\uFEFF'

/**
 * Reads books from a Shelfmark JSON backup or a CSV file. CSV columns are matched by
 * name, so Shelfmark's own export, a spreadsheet saved with ";" (as Norwegian Excel
 * does) and a Goodreads library export all work.
 */
object LibraryImport {

    /** @throws ImportException if [text] is neither valid JSON nor a CSV with a title column. */
    fun parse(text: String): List<Book> {
        val body = text.trimStart(ByteOrderMark).trim()
        if (body.isEmpty()) return emptyList()
        return if (body.startsWith("{") || body.startsWith("[")) parseJson(body) else parseCsv(body)
    }

    // ---------- JSON (Shelfmark backup) ----------

    private fun parseJson(body: String): List<Book> {
        val array = try {
            if (body.startsWith("[")) JSONArray(body) else JSONObject(body).optJSONArray("books")
        } catch (e: JSONException) {
            throw ImportException("Not valid JSON")
        } ?: throw ImportException("No \"books\" list in the JSON file")

        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val title = o.text("title") ?: return@mapNotNull null
            val authors = o.optJSONArray("authors")
                ?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).trim().ifEmpty { null } }.joinToString(", ") }
                ?: o.text("authors").orEmpty()
            val now = System.currentTimeMillis()
            Book(
                title = title,
                authors = authors,
                shelf = parseShelf(o.text("shelf")),
                rating = o.optInt("rating").coerceIn(0, 5),
                notes = o.text("notes").orEmpty(),
                isbn = cleanIsbn(o.text("isbn")),
                publisher = o.text("publisher"),
                publishedDate = o.text("publishedDate"),
                pageCount = o.optInt("pageCount").takeIf { it > 0 },
                description = o.text("description"),
                coverUrl = o.text("coverUrl"),
                addedAt = parseTime(o.text("addedAt")) ?: now,
                updatedAt = parseTime(o.text("updatedAt")) ?: now,
                finishedAt = parseTime(o.text("finishedAt")),
                ignoreForSuggestions = o.optBoolean("ignoreForSuggestions"),
            )
        }
    }

    private fun JSONObject.text(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).trim().ifEmpty { null }

    // ---------- CSV ----------

    /** Column names we understand, after [columnKey]. Includes Goodreads' export names. */
    private val aliases = mapOf(
        "title" to listOf("title", "tittel"),
        "authors" to listOf("authors", "author", "forfatter", "forfattere"),
        "extraAuthors" to listOf("additionalauthors"),
        "shelf" to listOf("shelf", "exclusiveshelf", "status", "hylle"),
        "rating" to listOf("rating", "myrating", "karakter"),
        "notes" to listOf("notes", "notater", "myreview"),
        "privateNotes" to listOf("privatenotes"),
        "isbn13" to listOf("isbn13"),
        "isbn" to listOf("isbn"),
        "publisher" to listOf("publisher", "forlag"),
        "published" to listOf("published", "publisheddate", "yearpublished", "originalpublicationyear", "utgitt"),
        "pages" to listOf("pages", "pagecount", "numberofpages", "sider"),
        "description" to listOf("description", "beskrivelse"),
        "cover" to listOf("coverurl", "cover"),
        "added" to listOf("added", "addedat", "dateadded", "lagttil"),
        "updated" to listOf("updated", "updatedat"),
        "finished" to listOf("finished", "finishedat", "dateread", "ferdig", "fullfort"),
        "ignore" to listOf("ignoreforsuggestions", "ignore", "ikkeiforslag"),
    )

    private fun columnKey(name: String) = foldText(name).replace(" ", "")

    private fun parseCsv(body: String): List<Book> {
        val rows = readCsv(body, delimiter = guessDelimiter(body))
        if (rows.isEmpty()) return emptyList()
        val header = rows.first().map(::columnKey)
        val index = aliases.mapValues { (_, names) -> names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } } }
        if (index["title"] == null) throw ImportException("The CSV file has no title column")

        return rows.drop(1).mapNotNull { row ->
            fun cell(key: String) = index[key]?.let { row.getOrNull(it) }?.trim()?.ifEmpty { null }
            val title = cell("title") ?: return@mapNotNull null
            val authors = listOfNotNull(cell("authors"), cell("extraAuthors")).joinToString(", ")
            val isbn = cleanIsbn(cell("isbn13")) ?: cleanIsbn(cell("isbn"))
            val now = System.currentTimeMillis()
            Book(
                title = title,
                authors = authors,
                shelf = parseShelf(cell("shelf")),
                rating = cell("rating")?.toDoubleOrNull()?.toInt()?.coerceIn(0, 5) ?: 0,
                notes = listOfNotNull(cell("notes"), cell("privateNotes")).joinToString("\n\n"),
                isbn = isbn,
                publisher = cell("publisher"),
                publishedDate = cell("published"),
                pageCount = cell("pages")?.filter(Char::isDigit)?.toIntOrNull()?.takeIf { it > 0 },
                description = cell("description"),
                // Without a cover link, BookRepository.repairCovers finds one after the import.
                coverUrl = cell("cover"),
                addedAt = parseTime(cell("added")) ?: now,
                updatedAt = parseTime(cell("updated")) ?: now,
                finishedAt = parseTime(cell("finished")),
                ignoreForSuggestions = parseYes(cell("ignore")),
            )
        }
    }

    /** Picks ",", ";" or tab, whichever the header line uses most. */
    private fun guessDelimiter(body: String): Char {
        val firstLine = body.lineSequence().first()
        return listOf(',', ';', '\t').maxBy { d -> firstLine.count { it == d } }
    }

    /** RFC 4180: quoted fields may hold delimiters, line breaks and doubled quotes. */
    internal fun readCsv(body: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        fun endField() { row.add(field.toString()); field.setLength(0) }
        fun endRow() {
            endField()
            if (row.any { it.isNotBlank() }) rows.add(row)
            row = mutableListOf()
        }
        while (i < body.length) {
            val c = body[i]
            when {
                quoted && c == '"' && body.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                quoted && c == '"' -> quoted = false
                quoted -> field.append(c)
                c == '"' -> quoted = true
                c == delimiter -> endField()
                c == '\r' -> Unit
                c == '\n' -> endRow()
                else -> field.append(c)
            }
            i++
        }
        endRow()
        return rows
    }

    // ---------- Shared ----------

    private fun parseShelf(value: String?): Shelf = when (value?.let { foldText(it).replace(" ", "") }) {
        "reading", "currentlyreading", "lesernaa", "leserna" -> Shelf.Reading
        "wanttoread", "toread", "villese" -> Shelf.WantToRead
        else -> Shelf.Read
    }

    /** Goodreads writes ISBNs as ="9788203362446"; keep only the digits (and X). */
    private fun parseYes(value: String?): Boolean =
        value?.let { foldText(it) } in setOf("yes", "true", "1", "ja", "x")

    private fun cleanIsbn(value: String?): String? {
        val raw = value?.filter { it.isDigit() || it == 'X' || it == 'x' }?.ifEmpty { null } ?: return null
        return Isbn.normalize(raw) ?: raw
    }

    /** Accepts ISO instants, epoch milliseconds and dates like 2024-05-01 or 2024/05/01. */
    private fun parseTime(value: String?): Long? {
        if (value == null) return null
        value.toLongOrNull()?.let { return it }
        try {
            return Instant.parse(value).toEpochMilli()
        } catch (_: DateTimeParseException) {
        }
        return try {
            LocalDate.parse(value.take(10).replace('/', '-'))
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
