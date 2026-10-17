package app.shelfmark.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

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
            val tags = o.optJSONArray("tags")
                ?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).trim().ifEmpty { null } }.joinToString(", ") }
                ?: o.text("tags")
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
                // 0 = unknown, so a re-import never counts it as the newer copy.
                updatedAt = parseTime(o.text("updatedAt")) ?: 0,
                finishedAt = parseTime(o.text("finishedAt")),
                ignoreForSuggestions = o.optBoolean("ignoreForSuggestions"),
                startedAt = parseTime(o.text("startedAt")),
                currentPage = o.optInt("currentPage").takeIf { it > 0 },
                series = o.text("series"),
                seriesNumber = o.text("seriesNumber"),
                format = BookFormat.parse(o.text("format")),
                rereads = o.optInt("rereads").coerceAtLeast(0),
                lentTo = o.text("lentTo"),
                lentAt = parseTime(o.text("lentAt")),
                tags = parseTags(tags).joinToString(", ").ifEmpty { null },
                genres = o.text("genres"),
                // A path inside a backup zip; BookRepository.import unpacks it.
                photoPath = o.text("photo"),
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
        "started" to listOf("started", "startedat", "datestarted", "startet"),
        "currentPage" to listOf("currentpage", "page", "side"),
        "series" to listOf("series", "serie"),
        "seriesNumber" to listOf("seriesnumber", "number", "nummer", "serienummer"),
        "format" to listOf("format", "binding"),
        "rereads" to listOf("rereads"),
        "readCount" to listOf("readcount"),
        "lentTo" to listOf("lentto", "lanttil", "utlant"),
        "lentAt" to listOf("lentat", "lantut"),
        "tags" to listOf("tags", "etiketter", "stikkord"),
        "bookshelves" to listOf("bookshelves"),
    )

    private val nonLetters = Regex("[^a-z]")

    /** Goodreads puts the series in the title: "Snømannen (Harry Hole, #7)". */
    private val seriesInTitle = Regex("^(.+?)\\s*\\(([^()]+?),?\\s*#\\s*([0-9]+(?:[.,][0-9]+)?)\\)$")

    /** Goodreads' own shelves, which aren't tags. */
    private val goodreadsShelves = setOf("read", "toread", "currentlyreading")

    private fun columnKey(name: String) = foldText(name).replace(" ", "")

    private fun parseCsv(body: String): List<Book> {
        val rows = readCsv(body, delimiter = guessDelimiter(body))
        if (rows.isEmpty()) return emptyList()
        val header = rows.first().map(::columnKey)
        val index = aliases.mapValues { (_, names) -> names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } } }
        if (index["title"] == null) throw ImportException("The CSV file has no title column")

        return rows.drop(1).mapNotNull { row ->
            fun cell(key: String) = index[key]?.let { row.getOrNull(it) }?.trim()?.ifEmpty { null }
            val fullTitle = cell("title") ?: return@mapNotNull null
            val inTitle = if (cell("series") == null) seriesInTitle.find(fullTitle) else null
            val title = inTitle?.groupValues?.get(1) ?: fullTitle
            val tags = cell("tags") ?: cell("bookshelves")?.split(',')
                ?.map { it.trim() }?.filterNot { foldText(it).replace(nonLetters, "") in goodreadsShelves }?.joinToString(", ")
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
                updatedAt = parseTime(cell("updated")) ?: 0,
                finishedAt = parseTime(cell("finished")),
                ignoreForSuggestions = parseYes(cell("ignore")),
                startedAt = parseTime(cell("started")),
                currentPage = cell("currentPage")?.filter(Char::isDigit)?.toIntOrNull()?.takeIf { it > 0 },
                series = cell("series") ?: inTitle?.groupValues?.get(2)?.trim(),
                seriesNumber = cell("seriesNumber") ?: inTitle?.groupValues?.get(3),
                format = BookFormat.parse(cell("format")),
                rereads = cell("rereads")?.toIntOrNull()?.coerceAtLeast(0)
                    ?: cell("readCount")?.toIntOrNull()?.let { (it - 1).coerceAtLeast(0) } ?: 0,
                lentTo = cell("lentTo"),
                lentAt = parseTime(cell("lentAt")),
                tags = parseTags(tags).joinToString(", ").ifEmpty { null },
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

    // ---------- Merging into the library ----------

    /**
     * [existing] updated from [incoming] when the import is the newer copy (a backup from
     * another phone, say), or null when it isn't. What the user records (shelf, rating, notes,
     * dates …) comes from the import; details only fill gaps; the id is kept.
     */
    fun merge(existing: Book, incoming: Book): Book? {
        if (incoming.updatedAt <= existing.updatedAt) return null
        return existing.copy(
            shelf = incoming.shelf,
            rating = incoming.rating,
            notes = incoming.notes.ifBlank { existing.notes },
            finishedAt = if (incoming.shelf == Shelf.Read) incoming.finishedAt ?: existing.finishedAt else null,
            startedAt = incoming.startedAt ?: existing.startedAt,
            currentPage = incoming.currentPage,
            series = incoming.series ?: existing.series,
            seriesNumber = incoming.seriesNumber ?: existing.seriesNumber,
            format = incoming.format,
            rereads = maxOf(incoming.rereads, existing.rereads),
            lentTo = incoming.lentTo,
            lentAt = incoming.lentAt,
            tags = incoming.tags ?: existing.tags,
            ignoreForSuggestions = incoming.ignoreForSuggestions,
            isbn = existing.isbn ?: incoming.isbn,
            publisher = existing.publisher ?: incoming.publisher,
            publishedDate = existing.publishedDate ?: incoming.publishedDate,
            pageCount = existing.pageCount ?: incoming.pageCount,
            description = existing.description ?: incoming.description,
            coverUrl = existing.coverUrl ?: incoming.coverUrl,
            photoPath = existing.photoPath ?: incoming.photoPath,
            genres = existing.genres ?: incoming.genres,
            updatedAt = incoming.updatedAt,
        )
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
        // Excel turns long numbers into 9,78821E+12; what's left of those isn't an ISBN.
        return Isbn.normalize(raw) ?: raw.takeIf { it.length == 10 || it.length == 13 }
    }

    private val localTimes = listOf("yyyy-MM-dd HH:mm[:ss]", "yyyy-MM-dd'T'HH:mm[:ss]", "d.M.yyyy H:mm[:ss]")
        .map(DateTimeFormatter::ofPattern)
    private val localDates = listOf("yyyy-MM-dd", "yyyy/MM/dd", "d.M.yyyy").map(DateTimeFormatter::ofPattern)

    /**
     * Accepts ISO instants, epoch milliseconds, local times like 2024-05-01 14:30:00, and
     * dates like 2024-05-01, 2024/05/01 or 01.05.2024 (Norwegian Excel).
     */
    private fun parseTime(raw: String?): Long? {
        val value = raw?.trim()?.ifEmpty { null } ?: return null
        value.toLongOrNull()?.let { n ->
            return when {
                n > 100_000_000_000 -> n // epoch milliseconds (from 1973 on)
                n > 1_000_000_000 -> n * 1000 // epoch seconds, as other apps export
                n in 1000..9999 -> LocalDate.of(n.toInt(), 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                else -> null
            }
        }
        try {
            return Instant.parse(value).toEpochMilli()
        } catch (_: DateTimeParseException) {
        }
        val zone = ZoneId.systemDefault()
        for (f in localTimes) {
            try {
                return LocalDateTime.parse(value, f).atZone(zone).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
            }
        }
        for (f in localDates) {
            try {
                return LocalDate.parse(value.substringBefore(' '), f).atStartOfDay(zone).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
            }
        }
        return null
    }

    /**
     * Reads a file's bytes as text: UTF-8 when the bytes are valid UTF-8, otherwise
     * Windows-1252, which Norwegian Excel uses for "CSV (semicolon separated)".
     */
    fun decode(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: CharacterCodingException) {
        String(bytes, Charset.forName("windows-1252"))
    }
}
