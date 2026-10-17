package app.shelfmark.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<Book>>

    @Query("SELECT * FROM books ORDER BY addedAt")
    suspend fun getAll(): List<Book>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observe(id: Long): Flow<Book?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: Long): Book?

    @Query("SELECT * FROM books WHERE isbn = :isbn LIMIT 1")
    suspend fun findByIsbn(isbn: String): Book?

    @Insert
    suspend fun insertAll(books: List<Book>)

    @Upsert
    suspend fun upsert(book: Book): Long

    @Query("UPDATE books SET rating = :rating, updatedAt = :now WHERE id = :id")
    suspend fun setRating(id: Long, rating: Int, now: Long)

    @Query("UPDATE books SET notes = :notes, updatedAt = :now WHERE id = :id")
    suspend fun setNotes(id: Long, notes: String, now: Long)

    /**
     * Moving a book onto Read from another shelf counts as finishing it now; moving it off Read
     * un-finishes it. Moving it onto Reading starts it now; back to Want to read clears both.
     */
    @Query(
        "UPDATE books SET finishedAt = CASE WHEN :shelf = 0 AND shelf != 0 THEN :now WHEN :shelf != 0 THEN NULL ELSE finishedAt END, " +
            "startedAt = CASE WHEN :shelf = 1 AND shelf != 1 THEN :now WHEN :shelf = 2 THEN NULL ELSE startedAt END, " +
            "currentPage = CASE WHEN :shelf = 1 THEN currentPage ELSE NULL END, " +
            "shelf = :shelf, updatedAt = :now WHERE id = :id",
    )
    suspend fun setShelf(id: Long, shelf: Shelf, now: Long)

    @Query("UPDATE books SET ignoreForSuggestions = :ignore, updatedAt = :now WHERE id = :id")
    suspend fun setIgnoreForSuggestions(id: Long, ignore: Boolean, now: Long)

    @Query("UPDATE books SET genres = :genres, profiledAt = :now WHERE id = :id")
    suspend fun setGenres(id: Long, genres: String?, now: Long)

    /** Sets the cover and marks it as checked. */
    @Query("UPDATE books SET coverUrl = :coverUrl, coverCheckedAt = :now WHERE id = :id")
    suspend fun setCover(id: Long, coverUrl: String?, now: Long)

    @Query("UPDATE books SET currentPage = :page, updatedAt = :now WHERE id = :id")
    suspend fun setProgress(id: Long, page: Int?, now: Long)

    @Query("UPDATE books SET startedAt = :startedAt, finishedAt = :finishedAt, updatedAt = :now WHERE id = :id")
    suspend fun setDates(id: Long, startedAt: Long?, finishedAt: Long?, now: Long)

    @Query("UPDATE books SET lentTo = :lentTo, lentAt = :lentAt, updatedAt = :now WHERE id = :id")
    suspend fun setLent(id: Long, lentTo: String?, lentAt: Long?, now: Long)

    /** Back on Reading for another read: the rating and notes stay, the dates start over. */
    @Query(
        "UPDATE books SET shelf = 1, rereads = rereads + 1, startedAt = :now, finishedAt = NULL, currentPage = NULL, " +
            "updatedAt = :now WHERE id = :id",
    )
    suspend fun readAgain(id: Long, now: Long)

    @Delete
    suspend fun delete(book: Book)
}
