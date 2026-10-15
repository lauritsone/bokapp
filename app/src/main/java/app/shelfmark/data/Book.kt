package app.shelfmark.data

import androidx.room.Entity
import androidx.room.PrimaryKey
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
    val addedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** The user's own photo wins over the online cover. */
fun Book.coverModel(): Any? = photoPath?.let { File(it) } ?: coverUrl
