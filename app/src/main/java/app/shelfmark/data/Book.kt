package app.shelfmark.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import java.io.File

@Entity(tableName = "books")
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val isbn: String? = null,
    val title: String = "",
    val authors: String = "",
    val publisher: String? = null,
    val publishedDate: String? = null,
    val pageCount: Int? = null,
    val description: String? = null,
    /** Cover image found online. */
    val coverUrl: String? = null,
    /** Cover photo the user took, stored in app-private storage. */
    val photoPath: String? = null,
    /** 0 = not rated, otherwise 1–5 stars. */
    val rating: Int = 0,
    val notes: String = "",
    @ColumnInfo(defaultValue = "0") val shelf: Shelf = Shelf.Read,
    /** Genre tags from the library catalogues (see [Genres]); used for match scores. */
    val genres: String? = null,
    /** When [genres] was last looked up; 0 = not yet. */
    @ColumnInfo(defaultValue = "0") val profiledAt: Long = 0,
    /** When a missing cover was last looked for; 0 = not yet. */
    @ColumnInfo(defaultValue = "0") val coverCheckedAt: Long = 0,
    /** When the book was marked as finished; null if unknown or not finished. */
    val finishedAt: Long? = null,
    /** Left out of the taste profile behind new-release suggestions (a one-off read, a gift …). */
    @ColumnInfo(defaultValue = "0") val ignoreForSuggestions: Boolean = false,
    /** When the user started reading it (the latest time, for a re-read); null if unknown. */
    val startedAt: Long? = null,
    /** How far into the book the user is, for the Reading shelf. */
    val currentPage: Int? = null,
    /** The series the book belongs to, e.g. "Harry Hole". */
    val series: String? = null,
    /** Its place in [series]: "3", or "3.5" for a novella between two books. */
    val seriesNumber: String? = null,
    @ColumnInfo(defaultValue = "0") val format: BookFormat = BookFormat.Paper,
    /** How many times the user has started the book again after reading it. */
    @ColumnInfo(defaultValue = "0") val rereads: Int = 0,
    /** Who has the book on loan, and since when. */
    val lentTo: String? = null,
    val lentAt: Long? = null,
    /** The user's own labels, comma separated: "book club, gift". See [tagList]. */
    val tags: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Which list a book is on. Stored as [code]; books from before shelves existed are [Read]. */
enum class Shelf(val code: Int, val key: String) {
    Read(0, "read"),
    Reading(1, "reading"),
    WantToRead(2, "want_to_read"),
    ;

    companion object {
        fun fromCode(code: Int) = entries.firstOrNull { it.code == code } ?: Read
    }
}

/** How the user reads the book. Stored as [code]. */
enum class BookFormat(val code: Int, val key: String) {
    Paper(0, "paper"),
    Ebook(1, "ebook"),
    Audiobook(2, "audiobook"),
    ;

    companion object {
        fun fromCode(code: Int) = entries.firstOrNull { it.code == code } ?: Paper

        /** "ebook", "e-bok", "Audible" … as a format; paper for anything else. */
        fun parse(value: String?): BookFormat {
            val v = value?.let { foldText(it).replace(Regex("[^a-z]"), "") } ?: return Paper
            return when {
                v.startsWith("e") && ("book" in v || "bok" in v || v == "e") || "kindle" in v || "epub" in v -> Ebook
                "audio" in v || "lyd" in v || "audible" in v || "storytel" in v -> Audiobook
                else -> Paper
            }
        }
    }
}

class Converters {
    @TypeConverter fun shelfToCode(shelf: Shelf): Int = shelf.code
    @TypeConverter fun codeToShelf(code: Int): Shelf = Shelf.fromCode(code)
    @TypeConverter fun formatToCode(format: BookFormat): Int = format.code
    @TypeConverter fun codeToFormat(code: Int): BookFormat = BookFormat.fromCode(code)
}

/** The user's tags, trimmed and without duplicates. */
fun Book.tagList(): List<String> = parseTags(tags)

fun parseTags(raw: String?): List<String> =
    raw.orEmpty().split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { foldText(it) }

/** How far through the book, 0–1, when both the page and the page count are known. */
fun Book.progress(): Float? {
    val page = currentPage ?: return null
    val total = pageCount?.takeIf { it > 0 } ?: return null
    return (page.toFloat() / total).coerceIn(0f, 1f)
}

/** [seriesNumber] as a number for sorting; books without one go last. */
fun Book.seriesOrder(): Double = seriesNumber?.replace(',', '.')?.toDoubleOrNull() ?: Double.MAX_VALUE

/** The user's own photo wins over the online cover. */
fun Book.coverModel(): Any? = photoPath?.let { File(it) } ?: coverUrl
