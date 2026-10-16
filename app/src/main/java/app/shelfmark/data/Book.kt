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

class Converters {
    @TypeConverter fun shelfToCode(shelf: Shelf): Int = shelf.code
    @TypeConverter fun codeToShelf(code: Int): Shelf = Shelf.fromCode(code)
}

/** The user's own photo wins over the online cover. */
fun Book.coverModel(): Any? = photoPath?.let { File(it) } ?: coverUrl
