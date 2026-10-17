package app.shelfmark.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
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

    /** Just enough of every book to tell whether one is already in the library. */
    @Query("SELECT id, title, authors FROM books")
    suspend fun names(): List<BookName>

    /**
     * Books without a photo whose cover hasn't been looked for yet, and that have no cover
     * or only a guessed one (see [CoverFinder.isGuess]). A real cover needs no check.
     */
    @Query(
        "SELECT * FROM books WHERE photoPath IS NULL AND coverCheckedAt = 0 " +
            "AND (coverUrl IS NULL OR coverUrl LIKE '%covers.openlibrary.org/b/isbn/%')",
    )
    suspend fun uncheckedCovers(): List<Book>

    /** The books of a series, in any spelling of its name's case. */
    @Query("SELECT * FROM books WHERE series = :series COLLATE NOCASE")
    fun observeSeries(series: String): Flow<List<Book>>

    /** Books in the taste profile whose genres were learned before [since] (or never). */
    @Query("SELECT * FROM books WHERE ignoreForSuggestions = 0 AND profiledAt < :since LIMIT :limit")
    suspend fun unprofiled(since: Long, limit: Int): List<Book>

    @Upsert
    suspend fun upsert(book: Book): Long

    /** In one transaction: new books (id 0) are added, the others replaced. */
    @Upsert
    suspend fun upsertAll(books: List<Book>)

    @Query("UPDATE books SET rating = :rating, updatedAt = :now WHERE id = :id")
    suspend fun setRating(id: Long, rating: Int, now: Long)

    @Query("UPDATE books SET notes = :notes, updatedAt = :now WHERE id = :id")
    suspend fun setNotes(id: Long, notes: String, now: Long)

    @Query("UPDATE books SET rating = :rating, notes = :notes, updatedAt = :now WHERE id = :id")
    suspend fun setReview(id: Long, rating: Int, notes: String, now: Long)

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

    /** Learned genres, unless the book was edited or re-profiled since [read] was read. */
    @Query(
        "UPDATE books SET genres = :genres, profiledAt = :now WHERE id = :id " +
            "AND updatedAt = :readUpdatedAt AND profiledAt = :readProfiledAt",
    )
    suspend fun setGenresIfUnchanged(id: Long, genres: String?, now: Long, readUpdatedAt: Long, readProfiledAt: Long)

    /**
     * Genres learned in the background for several books, in one transaction so the library
     * redraws once. A book changed while its genres were looked up keeps the newer state.
     */
    @Transaction
    suspend fun setGenres(learned: List<Pair<Book, String?>>, now: Long) {
        for ((read, g) in learned) setGenresIfUnchanged(read.id, g, now, read.updatedAt, read.profiledAt)
    }

    /** Sets the cover and marks it as checked. */
    @Query("UPDATE books SET coverUrl = :coverUrl, coverCheckedAt = :now WHERE id = :id")
    suspend fun setCover(id: Long, coverUrl: String?, now: Long)

    /** A cover found in the background, unless the book's cover or details changed since it was read. */
    @Query(
        "UPDATE books SET coverUrl = :coverUrl, coverCheckedAt = :now WHERE id = :id AND photoPath IS NULL " +
            "AND coverCheckedAt = 0 AND coverUrl IS :readCoverUrl AND updatedAt = :readUpdatedAt",
    )
    suspend fun setCoverIfUnchanged(id: Long, coverUrl: String?, now: Long, readCoverUrl: String?, readUpdatedAt: Long)

    /**
     * Covers found in the background for several books, in one transaction so the library
     * redraws once. A book edited (or given a cover) while its cover was looked for keeps
     * the newer state, and is looked at again on the next run.
     */
    @Transaction
    suspend fun setCovers(found: List<Pair<Book, String?>>, now: Long) {
        for ((read, url) in found) setCoverIfUnchanged(read.id, url, now, read.coverUrl, read.updatedAt)
    }

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
