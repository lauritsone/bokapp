package app.shelfmark.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class BookRepository(
    private val dao: BookDao,
    private val bookLookup: BookLookup,
    private val filesDir: File,
) {
    val books: Flow<List<Book>> = dao.observeAll()

    fun observe(id: Long): Flow<Book?> = dao.observe(id)

    suspend fun get(id: Long): Book? = dao.get(id)

    suspend fun findByIsbn(isbn: String): Book? = dao.findByIsbn(isbn)

    suspend fun lookup(isbn: String): BookInfo? = bookLookup.lookup(isbn)

    /** Inserts or updates; returns the book's id. */
    suspend fun save(book: Book): Long {
        val rowId = dao.upsert(book.copy(updatedAt = System.currentTimeMillis()))
        return if (book.id == 0L) rowId else book.id
    }

    suspend fun setRating(id: Long, rating: Int) =
        dao.setRating(id, rating.coerceIn(0, 5), System.currentTimeMillis())

    suspend fun setNotes(id: Long, notes: String) =
        dao.setNotes(id, notes, System.currentTimeMillis())

    suspend fun delete(book: Book) {
        dao.delete(book)
        book.photoPath?.let { path -> withContext(Dispatchers.IO) { File(path).delete() } }
    }

    /** A fresh file in app-private storage for a cover photo. */
    fun newPhotoFile(): File {
        val dir = File(filesDir, "covers").apply { mkdirs() }
        return File(dir, "cover_${System.currentTimeMillis()}.jpg")
    }
}
