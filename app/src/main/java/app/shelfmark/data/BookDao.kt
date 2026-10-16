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

    @Query("UPDATE books SET shelf = :shelf, updatedAt = :now WHERE id = :id")
    suspend fun setShelf(id: Long, shelf: Shelf, now: Long)

    @Query("UPDATE books SET genres = :genres, profiledAt = :now WHERE id = :id")
    suspend fun setGenres(id: Long, genres: String?, now: Long)

    @Delete
    suspend fun delete(book: Book)
}
