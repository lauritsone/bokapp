package app.shelfmark.data

import java.util.concurrent.atomic.AtomicBoolean
import java.io.IOException
import org.json.JSONException
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private val nb = NbCatalog()
    private val ol = OlCatalog()
    private val releaseFinder = ReleaseFinder(dao, nb, ol)
    private val coverFinder = CoverFinder(nb)

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
                    } catch (e: JSONException) {
                        // An unexpected answer from a cover service: try again next run.
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

    /** Suggestions already put on Want to read, so they still show as added on a later visit. */
    val addedReleases: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())
    private val adding = Mutex()

    /**
     * Puts a suggested new book on the Want to read shelf; returns its id. A book that is
     * already in the library (a double tap, or added earlier) isn't added again.
     */
    suspend fun addToWantToRead(match: Match): Long = adding.withLock {
        val c = match.candidate
        addedReleases += c.record.id
        val existing = c.record.isbn?.let { dao.findByIsbn(it) }
            ?: dao.getAll().firstOrNull { nameKey(it) == foldText(c.record.title) + "|" + foldText(c.author) }
        if (existing != null) return existing.id
        save(
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

    /**
     * Editions in the catalogues (NB.no, then English ones on Open Library) whose title
     * contains [title], by [author] when one is given. For books whose barcode won't scan.
     * @throws IOException if neither catalogue can be reached.
     */
    suspend fun searchCatalogue(title: String, author: String): List<CatalogRecord> = withContext(Dispatchers.IO) {
        val wanted = foldText(title)
        if (wanted.isEmpty()) return@withContext emptyList()
        val name = TasteProfile.authorNames(author).firstOrNull()
        val sources = listOf(
            suspend { nb.search(listOfNotNull("\"${title.trim()}\"", name?.let { "\"$it\"" }).joinToString(" "), size = 30, norwegianOnly = false) },
            suspend { ol.search(listOfNotNull("title:\"${title.trim()}\"", name?.let { "author:\"$it\"" }).joinToString(" "), fromYear = 0, limit = 10) },
        )
        var reached = false
        val found = sources.flatMap { source ->
            try {
                source().also { reached = true }
            } catch (e: IOException) {
                emptyList()
            } catch (e: JSONException) {
                reached = true
                emptyList()
            }
        }
        if (!reached) throw IOException("No catalogue could be reached")
        found
            .filter { wanted in foldText(it.title) && (name == null || ReleaseFinder.wroteBy(it, name)) }
            .distinctBy { it.isbn ?: it.id }
            // The exact title before longer ones that contain it, then the newest edition.
            .sortedWith(compareBy({ ReleaseFinder.titleKey(it.title) != ReleaseFinder.titleKey(title) }, { -(it.year ?: 0) }))
            .take(20)
    }

    /** Everything worth filling in from a [searchCatalogue] result: the main author and the blurb too. */
    suspend fun bookInfo(record: CatalogRecord): BookInfo = withContext(Dispatchers.IO) {
        val fromOl = record.id.startsWith(OlCatalog.ID_PREFIX)
        val details = if (fromOl) null else runCatching { nb.details(record.id) }.getOrNull()
        val summary = if (fromOl) runCatching { ol.description(record) }.getOrNull() else details?.summary
        BookInfo(
            isbn = record.isbn.orEmpty(),
            title = ReleaseFinder.displayTitle(record.title),
            // Catalogues list translators and illustrators as creators too; the first is the author.
            authors = details?.author ?: record.creators.firstOrNull()?.let(::personName).orEmpty(),
            publisher = record.publisher,
            publishedDate = record.year?.toString(),
            pageCount = record.pageCount,
            description = summary,
            coverUrl = record.coverUrl,
        )
    }

    /**
     * Looks the book up again (by ISBN, else by title and author) and fills in the details
     * it lacks, such as for a book typed in by hand. Nothing the user entered is overwritten.
     * @throws IOException if no catalogue can be reached.
     */
    suspend fun refreshDetails(id: Long): RefreshResult = withContext(Dispatchers.IO) {
        val book = dao.get(id) ?: return@withContext RefreshResult.NotFound
        val info = when (val found = findDetails(book)) {
            is Found.Info -> found.info
            Found.Several -> return@withContext RefreshResult.Ambiguous
            Found.None -> return@withContext RefreshResult.NotFound
        }
        val isbnTaken = info.isbn.isNotBlank() && dao.findByIsbn(info.isbn)?.let { it.id != id } == true
        val updated = DetailRefresh.fillGaps(book, info, isbnTaken)
        val filled = DetailRefresh.filled(book, updated)
        // A cover not found before may be found now, with an ISBN or another try.
        val retryCover = updated.photoPath == null && (updated.coverUrl == null || CoverFinder.isGuess(updated.coverUrl))
        if (filled == 0) {
            if (retryCover) {
                dao.setCover(id, book.coverUrl, 0)
                repairCovers()
            }
            return@withContext RefreshResult.NothingNew
        }
        // A blurb or author to go by changes the genres, so they are learned again.
        save(updated.copy(genres = null, profiledAt = 0, coverCheckedAt = if (retryCover) 0 else updated.coverCheckedAt))
        lastReleases = null
        RefreshResult.Updated(filled)
    }

    private sealed interface Found {
        data class Info(val info: BookInfo) : Found
        data object None : Found
        data object Several : Found
    }

    /** The catalogue's details for [book]. */
    private suspend fun findDetails(book: Book): Found {
        val isbn = book.isbn?.let(Isbn::normalize)
        if (isbn != null) {
            // An ISBN typed wrong points at another book; only trust it if the title agrees.
            lookup(isbn)?.takeIf { DetailRefresh.sameTitle(book.title, it.title) }?.let { return Found.Info(it) }
        }
        if (book.title.isBlank()) return Found.None
        val byAuthor = if (book.authors.isBlank()) emptyList() else searchCatalogue(book.title, book.authors)
        val pick = DetailRefresh.pick(book.title, byAuthor, byAuthor = true).takeIf { it is DetailRefresh.PickResult.One }
            // A typed author spelled differently from the catalogue: try the title alone.
            ?: DetailRefresh.pick(book.title, searchCatalogue(book.title, ""), byAuthor = false, author = book.authors)
        return when (pick) {
            is DetailRefresh.PickResult.One -> {
                val record = pick.record
                val info = try {
                    bookInfo(record)
                } catch (e: JSONException) {
                    return Found.None
                }
                // The search result may lack an ISBN cover that the ISBN lookup has.
                val byIsbn = if (record.isbn != null && (info.coverUrl == null || info.description == null)) {
                    runCatching { lookup(record.isbn) }.getOrNull()
                } else {
                    null
                }
                Found.Info(
                    info.copy(
                        coverUrl = info.coverUrl ?: byIsbn?.coverUrl,
                        description = info.description ?: byIsbn?.description,
                        pageCount = info.pageCount ?: byIsbn?.pageCount,
                    ),
                )
            }
            DetailRefresh.PickResult.Several -> Found.Several
            DetailRefresh.PickResult.None -> Found.None
        }
    }

    /** Inserts or updates; returns the book's id. */
    suspend fun save(book: Book): Long {
        val now = System.currentTimeMillis()
        val previous = if (book.id == 0L) null else dao.get(book.id)
        val previousShelf = previous?.shelf
        // Corrected details may point at another catalogue record, so its genres are learned again.
        val identityChanged = previous != null &&
            (previous.isbn != book.isbn || foldText(previous.title) != foldText(book.title) || foldText(previous.authors) != foldText(book.authors))
        val finished = previousShelf != null && previousShelf != Shelf.Read && book.shelf == Shelf.Read
        val finishedAt = when {
            finished -> now
            book.shelf != Shelf.Read -> null
            else -> book.finishedAt
        }
        val relearn = if (identityChanged) book.copy(genres = null, profiledAt = 0) else book
        val rowId = dao.upsert(relearn.copy(updatedAt = now, finishedAt = finishedAt))
        if (identityChanged) lastReleases = null
        if (book.photoPath == null && (book.coverUrl == null || CoverFinder.isGuess(book.coverUrl))) repairCovers()
        return if (book.id == 0L) rowId else book.id
    }

    suspend fun setRating(id: Long, rating: Int) =
        dao.setRating(id, rating.coerceIn(0, 5), System.currentTimeMillis())

    suspend fun setNotes(id: Long, notes: String) =
        dao.setNotes(id, notes, System.currentTimeMillis())

    suspend fun setShelf(id: Long, shelf: Shelf) =
        dao.setShelf(id, shelf, System.currentTimeMillis())

    /** Leaves the book out of (or back in) the taste behind new-release suggestions. */
    suspend fun setIgnoreForSuggestions(id: Long, ignore: Boolean) {
        dao.setIgnoreForSuggestions(id, ignore, System.currentTimeMillis())
        // The scores change, so the next visit to the new-releases page searches again.
        lastReleases = null
    }

    /** Moves the book to Read and records today as the day it was finished. */
    suspend fun finish(id: Long) = setShelf(id, Shelf.Read)

    suspend fun review(id: Long, rating: Int, notes: String) {
        setRating(id, rating)
        setNotes(id, notes.trim())
    }

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
        val text = LibraryImport.decode(input.use { it.readBytes() })
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
