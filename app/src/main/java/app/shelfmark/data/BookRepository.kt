package app.shelfmark.data

import java.util.concurrent.atomic.AtomicBoolean
import java.io.IOException
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class BookRepository(
    private val dao: BookDao,
    private val bookLookup: BookLookup,
    private val filesDir: File,
) {
    val books: Flow<List<Book>> = dao.observeAll()

    private val releaseFinder = ReleaseFinder(dao, NbCatalog(), OlCatalog())
    private val coverFinder = CoverFinder(NbCatalog())

    /** Work that should finish even if the user leaves the screen that started it. */
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repairingCovers = AtomicBoolean(false)
    @Volatile private var repairCoversAgain = false

    /**
     * Looks for real covers, in the background, for books that have none or only a guessed
     * link (imports, books scanned while a cover service was down). Covers show up as they
     * are found. Books that can't be checked (offline) are tried again next time.
     */
    fun repairCovers() {
        if (!repairingCovers.compareAndSet(false, true)) {
            repairCoversAgain = true
            return
        }
        background.launch {
            try {
                do {
                    repairCoversAgain = false
                    repairCoverBatch()
                } while (repairCoversAgain)
            } finally {
                repairingCovers.set(false)
            }
        }
    }

    private suspend fun repairCoverBatch() = coroutineScope {
        val pending = dao.getAll().filter {
            it.photoPath == null && it.coverCheckedAt == 0L && (it.coverUrl == null || CoverFinder.isGuess(it.coverUrl))
        }
        val permits = Semaphore(4)
        pending.map { book ->
            launch {
                permits.withPermit {
                    val guess = book.coverUrl
                    try {
                        if (guess != null && imageExists(guess)) {
                            dao.setCover(book.id, guess, System.currentTimeMillis())
                        } else {
                            dao.setCover(book.id, coverFinder.find(book), System.currentTimeMillis())
                        }
                    } catch (e: IOException) {
                        // Offline: leave it for the next run.
                    }
                }
            }
        }.joinAll()
    }

    /** The last search, kept while the app runs so revisiting the page is instant. */
    var lastReleases: ReleaseResult? = null
        private set

    /** New books scored against the user's taste. @throws java.io.IOException when offline. */
    suspend fun findReleases(onProgress: (ReleaseProgress) -> Unit): ReleaseResult =
        withContext(Dispatchers.IO) { releaseFinder.find(onProgress) }.also { lastReleases = it }

    /** Puts a suggested new book on the Want to read shelf; returns its id. */
    suspend fun addToWantToRead(match: Match): Long {
        val c = match.candidate
        return save(
            Book(
                title = c.record.title,
                authors = c.author,
                isbn = c.record.isbn,
                publisher = c.record.publisher,
                publishedDate = c.record.year?.toString(),
                pageCount = c.record.pageCount,
                description = c.summary,
                coverUrl = c.record.coverUrl,
                shelf = Shelf.WantToRead,
                genres = Genres.encode(c.tags).ifEmpty { null },
                profiledAt = System.currentTimeMillis(),
                coverCheckedAt = System.currentTimeMillis(),
            ),
        )
    }

    fun observe(id: Long): Flow<Book?> = dao.observe(id)

    suspend fun get(id: Long): Book? = dao.get(id)

    suspend fun findByIsbn(isbn: String): Book? = dao.findByIsbn(isbn)

    suspend fun lookup(isbn: String): BookInfo? = bookLookup.lookup(isbn)

    /** Inserts or updates; returns the book's id. */
    suspend fun save(book: Book): Long {
        val rowId = dao.upsert(book.copy(updatedAt = System.currentTimeMillis()))
        if (book.photoPath == null && (book.coverUrl == null || CoverFinder.isGuess(book.coverUrl))) repairCovers()
        return if (book.id == 0L) rowId else book.id
    }

    suspend fun setRating(id: Long, rating: Int) =
        dao.setRating(id, rating.coerceIn(0, 5), System.currentTimeMillis())

    suspend fun setNotes(id: Long, notes: String) =
        dao.setNotes(id, notes, System.currentTimeMillis())

    suspend fun setShelf(id: Long, shelf: Shelf) =
        dao.setShelf(id, shelf, System.currentTimeMillis())

    /** Writes every book to [out] and returns how many were exported. */
    suspend fun export(format: ExportFormat, out: OutputStream): Int = withContext(Dispatchers.IO) {
        val books = dao.getAll()
        out.use { LibraryExport.write(format, books, it) }
        books.size
    }

    /**
     * Adds the books in a JSON backup or CSV file. Books already in the library (same
     * ISBN, or same title and author) are skipped, so importing twice is harmless.
     * @throws ImportException if the file can't be read as either format.
     */
    suspend fun import(input: InputStream): ImportResult = withContext(Dispatchers.IO) {
        val text = input.use { it.readBytes().toString(Charsets.UTF_8) }
        val incoming = LibraryImport.parse(text)
        val existing = dao.getAll()
        val seenIsbns = existing.mapNotNull { it.isbn }.toMutableSet()
        val seenNames = existing.map(::nameKey).toMutableSet()
        val fresh = incoming.filter { book ->
            val duplicate = (book.isbn != null && book.isbn in seenIsbns) || nameKey(book) in seenNames
            if (!duplicate) {
                book.isbn?.let(seenIsbns::add)
                seenNames.add(nameKey(book))
            }
            !duplicate
        }
        dao.insertAll(fresh)
        if (fresh.isNotEmpty()) repairCovers()
        ImportResult(added = fresh.size, skipped = incoming.size - fresh.size)
    }

    private fun nameKey(book: Book) = foldText(book.title) + "|" + foldText(book.authors)

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

data class ImportResult(val added: Int, val skipped: Int)
