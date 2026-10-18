package app.shelfmark.data

import java.util.concurrent.atomic.AtomicBoolean
import java.io.IOException
import org.json.JSONException
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class BookRepository(
    private val dao: BookDao,
    private val filesDir: File,
    cacheDir: File,
) {
    val books: Flow<List<Book>> = dao.observeAll()

    private val nb = NbCatalog(DiskCache(File(cacheDir, "catalogue"), maxAgeMs = 7L * 24 * 60 * 60 * 1000))
    private val bookLookup = BookLookup(nb)
    private val ol = OlCatalog()
    private val releaseFinder = ReleaseFinder(dao, nb, ol)
    private val coverFinder = CoverFinder(nb)

    /** Work that should finish even if the user leaves the screen that started it. */
    private val background = CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
            // A failed background job (a database or parsing error) must never crash the app.
            CoroutineExceptionHandler { _, e -> android.util.Log.w("Shelfmark", "Background work failed", e) },
    )
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
                // A request that came in just as the last batch ended would otherwise be lost.
                if (repairCoversAgain) repairCovers()
            }
        }
    }

    private suspend fun repairCoverBatch() {
        val pending = dao.uncheckedCovers()
        val permits = Semaphore(4)
        // Found covers are saved a batch at a time: each write redraws the library, and an
        // import can leave hundreds of books to check.
        for (batch in pending.chunked(COVER_BATCH)) {
            val found = java.util.concurrent.ConcurrentLinkedQueue<Pair<Book, String?>>()
            coroutineScope {
                batch.map { book ->
                    launch {
                        permits.withPermit {
                            val guess = book.coverUrl
                            try {
                                val cover = if (guess != null && imageExists(guess)) guess else coverFinder.find(book, guessChecked = guess != null)
                                found += book to cover
                            } catch (e: IOException) {
                                // Offline: leave it for the next run.
                            } catch (e: JSONException) {
                                // An unexpected answer from a cover service: try again next run.
                            }
                        }
                    }
                }.joinAll()
            }
            if (found.isNotEmpty()) dao.setCovers(found.toList(), System.currentTimeMillis())
        }
    }

    /** The last search of each kind, kept while the app runs so revisiting the page is instant. */
    private val releases = java.util.concurrent.ConcurrentHashMap<ReleaseKind, ReleaseResult>()

    fun lastReleases(kind: ReleaseKind): ReleaseResult? = releases[kind]

    /** The library changed in a way that changes the suggestions; the next visit searches again. */
    private fun forgetReleases() = releases.clear()

    /** Books scored against the user's taste. @throws java.io.IOException when offline. */
    suspend fun findReleases(kind: ReleaseKind, onProgress: (ReleaseProgress) -> Unit): ReleaseResult =
        withContext(Dispatchers.IO) { releaseFinder.find(kind, onProgress) }.also { releases[kind] = it }

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
        val key = foldText(c.record.title) + "|" + foldText(c.author)
        val existing = c.record.isbn?.let { dao.findByIsbn(it) }?.id
            ?: dao.names().firstOrNull { foldText(it.title) + "|" + foldText(it.authors) == key }?.id
        if (existing != null) return existing
        val kept = HashMap(releases)
        val id = save(
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
                profiledAt = Genres.profiledNow(),
                coverCheckedAt = System.currentTimeMillis(),
            ),
        )
        // The added suggestion shows as added; the rest of the list still holds.
        releases.putAll(kept)
        id
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
        // Both catalogues at once; null means it couldn't be reached.
        val answers = coroutineScope {
            sources.map { source ->
                async {
                    try {
                        source()
                    } catch (e: IOException) {
                        null
                    } catch (e: JSONException) {
                        emptyList()
                    }
                }
            }.awaitAll()
        }
        if (answers.all { it == null }) throw IOException("No catalogue could be reached")
        val key = ReleaseFinder.titleKey(title)
        answers.flatMap { it.orEmpty() }
            .filter { wanted in foldText(it.title) && (name == null || ReleaseFinder.wroteBy(it, name)) }
            .distinctBy { it.isbn ?: it.id }
            // The exact title before longer ones that contain it, then the newest edition.
            .sortedWith(compareBy({ ReleaseFinder.titleKey(it.title) != key }, { -(it.year ?: 0) }))
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
            series = details?.series,
            nextInSeries = details?.succeeding,
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
        // A blurb or author to go by changes the genres, so they are learned again; one write,
        // so the library redraws once.
        save(updated, relearn = true, retryCover = retryCover)
        forgetReleases()
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

    /**
     * Inserts or updates; returns the book's id. [relearn] looks the genres up again even
     * when the title and author are unchanged; [retryCover] looks for a cover again if the
     * book still has none.
     */
    suspend fun save(book: Book, relearn: Boolean = false, retryCover: Boolean = false): Long {
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
        // Like the shelf buttons: starting a book (new or moved) on Reading starts it today.
        val startedAt = when {
            book.shelf == Shelf.WantToRead -> null
            // A new book may come with its own start date; a moved one starts now, as with the shelf buttons.
            book.shelf == Shelf.Reading && previousShelf != Shelf.Reading && (previous != null || book.startedAt == null) -> now
            else -> book.startedAt
        }
        // The edit screen holds the book as it was when opened; covers and genres found in the
        // background since then are kept rather than written back over.
        val merged = when {
            previous == null -> book
            identityChanged -> book.copy(genres = null, profiledAt = 0)
            else -> book.copy(
                genres = previous.genres,
                profiledAt = previous.profiledAt,
                coverUrl = if (book.coverUrl == null || CoverFinder.isGuess(book.coverUrl)) previous.coverUrl ?: book.coverUrl else book.coverUrl,
                coverCheckedAt = maxOf(book.coverCheckedAt, previous.coverCheckedAt),
            ).let { if (relearn) it.copy(genres = null, profiledAt = 0) else it }
        }.let { m ->
            val stillNoCover = m.photoPath == null && (m.coverUrl == null || CoverFinder.isGuess(m.coverUrl))
            if (retryCover && stillNoCover) m.copy(coverCheckedAt = 0) else m
        }
        val rowId = dao.upsert(
            merged.copy(
                updatedAt = now,
                finishedAt = finishedAt,
                startedAt = startedAt,
                currentPage = merged.currentPage.takeIf { book.shelf == Shelf.Reading },
                tags = parseTags(merged.tags).joinToString(", ").ifEmpty { null },
                series = merged.series?.trim()?.ifEmpty { null },
                seriesNumber = merged.seriesNumber?.trim()?.ifEmpty { null },
            ),
        )
        // A replaced cover photo is no longer needed.
        previous?.photoPath?.takeIf { it != book.photoPath }?.let { withContext(Dispatchers.IO) { File(it).delete() } }
        // New, changed or moved books change what's suggested (and an owned book isn't new to the user).
        if (previous == null || identityChanged || previous.shelf != book.shelf || previous.rating != book.rating) forgetReleases()
        if (merged.photoPath == null && (merged.coverUrl == null || CoverFinder.isGuess(merged.coverUrl))) repairCovers()
        return if (book.id == 0L) rowId else book.id
    }

    suspend fun setRating(id: Long, rating: Int) {
        dao.setRating(id, rating.coerceIn(0, 5), System.currentTimeMillis())
        forgetReleases()
    }

    suspend fun setNotes(id: Long, notes: String) =
        dao.setNotes(id, notes, System.currentTimeMillis())

    suspend fun setShelf(id: Long, shelf: Shelf) {
        dao.setShelf(id, shelf, System.currentTimeMillis())
        forgetReleases()
    }

    /** Leaves the book out of (or back in) the taste behind new-release suggestions. */
    suspend fun setIgnoreForSuggestions(id: Long, ignore: Boolean) {
        dao.setIgnoreForSuggestions(id, ignore, System.currentTimeMillis())
        // The scores change, so the next visit to the new-releases page searches again.
        forgetReleases()
    }

    /** Moves the book to Read and records today as the day it was finished. */
    suspend fun finish(id: Long) = setShelf(id, Shelf.Read)

    suspend fun review(id: Long, rating: Int, notes: String) {
        dao.setReview(id, rating.coerceIn(0, 5), notes.trim(), System.currentTimeMillis())
        forgetReleases()
    }

    /** The page the user is on; past the last page counts as the last. */
    suspend fun setProgress(id: Long, page: Int?) {
        val total = dao.get(id)?.pageCount
        val clamped = page?.coerceAtLeast(0)?.let { p -> total?.takeIf { it > 0 }?.let { p.coerceAtMost(it) } ?: p }
        dao.setProgress(id, clamped, System.currentTimeMillis())
    }

    /** Sets when the book was started and finished; a finish before the start moves the start back. */
    suspend fun setDates(id: Long, startedAt: Long?, finishedAt: Long?) {
        val start = if (startedAt != null && finishedAt != null && startedAt > finishedAt) finishedAt else startedAt
        dao.setDates(id, start, finishedAt, System.currentTimeMillis())
    }

    suspend fun lend(id: Long, to: String) =
        dao.setLent(id, to.trim().ifEmpty { null }, System.currentTimeMillis().takeIf { to.isNotBlank() }, System.currentTimeMillis())

    suspend fun returned(id: Long) = dao.setLent(id, null, null, System.currentTimeMillis())

    /** Starts the book again: back on Reading, counted as a re-read, rating and notes kept. */
    suspend fun readAgain(id: Long) {
        dao.readAgain(id, System.currentTimeMillis())
        forgetReleases()
    }

    /**
     * The book after [book] in its series, from the National Library's record, or null if
     * the catalogue doesn't say. @throws IOException when offline.
     */
    suspend fun nextInSeries(book: Book): NextInSeries? = withContext(Dispatchers.IO) {
        val author = TasteProfile.authorNames(book.authors).firstOrNull()
        val record = book.isbn?.let { isbn -> Isbn.normalize(isbn)?.let { nb.findByIsbn(it) } }
            ?.takeIf { DetailRefresh.sameTitle(book.title, it.title) }
            ?: author?.let { a ->
                nb.search("\"${book.title}\" \"$a\"", size = 10, norwegianOnly = false)
                    .firstOrNull { ReleaseFinder.wroteBy(it, a) && DetailRefresh.sameTitle(book.title, it.title) }
            }
            ?: return@withContext null
        val next = try {
            nb.details(record.id).succeeding
        } catch (e: JSONException) {
            null
        } ?: return@withContext null
        val nextKey = ReleaseFinder.titleKey(next)
        val owned = dao.names().firstOrNull { it.id != book.id && ReleaseFinder.titleKey(it.title) == nextKey }
            ?.let { dao.get(it.id) }
        if (owned != null) return@withContext NextInSeries(next, owned = owned, record = null)
        // The edition to add: the same author's, newest first, Norwegian when there is one.
        val editions = nb.search(listOfNotNull("\"$next\"", author?.let { "\"$it\"" }).joinToString(" "), size = 20, norwegianOnly = false)
            .filter { ReleaseFinder.titleKey(it.title) == nextKey && (author == null || ReleaseFinder.wroteBy(it, author)) }
        val edition = editions.sortedWith(compareBy({ it.language != "nob" }, { it.isbn == null }, { -(it.year ?: 0) })).firstOrNull()
        NextInSeries(next, owned = null, record = edition)
    }

    /**
     * Puts the next book of [after]'s series on Want to read; returns its id. The series and
     * number carry over. @throws IOException when offline.
     */
    suspend fun addNextInSeries(after: Book, next: NextInSeries): Long {
        next.owned?.let { return it.id }
        val record = next.record
        val info = record?.let { r ->
            try {
                bookInfo(r)
            } catch (e: JSONException) {
                null
            }
        }
        val number = after.seriesNumber?.replace(',', '.')?.toDoubleOrNull()?.let { n ->
            if (n % 1.0 == 0.0) (n.toInt() + 1).toString() else null
        }
        return save(
            Book(
                title = info?.title ?: next.title,
                authors = info?.authors?.ifBlank { null } ?: after.authors,
                isbn = record?.isbn?.takeIf { dao.findByIsbn(it) == null },
                publisher = info?.publisher,
                publishedDate = info?.publishedDate,
                pageCount = info?.pageCount,
                description = info?.description,
                coverUrl = info?.coverUrl,
                shelf = Shelf.WantToRead,
                series = after.series ?: info?.series,
                seriesNumber = number,
                format = after.format,
            ),
        )
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
        val (backup, text) = input.buffered().use { stream ->
            if (LibraryBackup.isZip(stream)) {
                // Photos go straight to storage as they are read, so a big backup fits in memory.
                val contents = try {
                    LibraryBackup.read(stream, ::newPhotoFile)
                } catch (e: java.util.zip.ZipException) {
                    throw ImportException("Not a readable zip file")
                }
                contents to contents.booksJson
            } else {
                null to LibraryImport.decode(stream.readBytes())
            }
        }
        // Every photo this import unpacked. Whatever no saved book ends up using is deleted
        // at the end, however the import ends; the library's own photos are never touched.
        val unpacked = backup?.photos?.values.orEmpty().toMutableList()
        var saved: List<Book> = emptyList()
        var existing: List<Book> = emptyList()
        var total = 0
        val fresh = mutableListOf<Book>()
        val updates = mutableListOf<Book>()
        try {
            val assigned = mutableSetOf<File>()
            val incoming = LibraryImport.parse(text).map { book ->
                // A photo path only means something inside a backup zip.
                val file = book.photoPath?.let { backup?.photos?.get(it) }
                // Two books naming the same photo each get their own copy, so deleting one never removes the other's.
                val own = when {
                    file == null -> null
                    assigned.add(file) -> file
                    else -> newPhotoFile().also { copy -> file.copyTo(copy); unpacked += copy }
                }
                book.copy(photoPath = own?.absolutePath)
            }
            total = incoming.size
            existing = dao.getAll()
            val byIsbn = existing.filter { it.isbn != null }.associateBy { it.isbn }.toMutableMap()
            val byName = existing.associateBy(::nameKey).toMutableMap()
            val now = System.currentTimeMillis()
            for (book in incoming) {
                val match = book.isbn?.let { byIsbn[it] } ?: byName[nameKey(book)]
                if (match == null) {
                    fresh += book.copy(
                        updatedAt = book.updatedAt.takeIf { it > 0 } ?: now,
                        // Genres from a backup needn't be looked up again.
                        profiledAt = if (book.genres != null) Genres.profiledNow() else 0,
                    )
                    book.isbn?.let { byIsbn[it] = book }
                    byName[nameKey(book)] = book
                    continue
                }
                // Listed twice in the file: the first copy is kept.
                if (match.id == 0L) continue
                // Already in the library: the newer copy wins.
                val merged = LibraryImport.merge(match, book) ?: continue
                updates += merged
                byName[nameKey(merged)] = merged.copy(id = 0)
                merged.isbn?.let { byIsbn[it] = merged.copy(id = 0) }
            }
            // One transaction: an import is all or nothing, and the library redraws once.
            dao.upsertAll(updates + fresh)
            saved = updates + fresh
        } finally {
            val used = saved.mapNotNull { it.photoPath }.toSet()
            unpacked.filter { it.absolutePath !in used }.forEach { it.delete() }
        }
        // Replaced photos go only once the new ones are safely in.
        val oldPhotos = existing.associate { it.id to it.photoPath }
        for (merged in updates) {
            val old = oldPhotos[merged.id]
            if (old != null && old != merged.photoPath) File(old).delete()
        }
        if (fresh.isNotEmpty() || updates.isNotEmpty()) {
            repairCovers()
            forgetReleases()
        }
        ImportResult(added = fresh.size, updated = updates.size, skipped = total - fresh.size - updates.size)
    }

    private fun nameKey(book: Book) = foldText(book.title) + "|" + foldText(book.authors)

    /** The book removed last and when, until the library has offered to undo it. */
    val lastRemoved = MutableStateFlow<Removed?>(null)

    /**
     * Removes the book. Its cover photo stays until the next start, so Undo can bring the
     * book back whole (see [deleteUnusedPhotos]).
     */
    suspend fun delete(book: Book) {
        dao.delete(book)
        forgetReleases()
        lastRemoved.value = Removed(book, System.currentTimeMillis())
    }

    /** Runs [deleteUnusedPhotos] in the background. */
    fun cleanUpInBackground() {
        background.launch { deleteUnusedPhotos() }
    }

    /** Brings a removed book back, as it was. */
    suspend fun restore(book: Book) {
        dao.upsert(book)
        forgetReleases()
        repairCovers()
    }

    /** Undoes a change to the reading log (shelf, rating, dates, lending …), back to [before]. */
    suspend fun undo(before: Book) {
        dao.restoreLog(
            before.id, before.shelf, before.rating, before.notes, before.startedAt, before.finishedAt,
            before.currentPage, before.rereads, before.lentTo, before.lentAt, System.currentTimeMillis(),
        )
        forgetReleases()
    }

    /**
     * Deletes cover photos no book uses: those of removed books, and of new books that were
     * never saved. A photo from the last day is kept, as its book may still be on the edit screen.
     */
    suspend fun deleteUnusedPhotos() = withContext(Dispatchers.IO) {
        val used = dao.photoPaths().toSet()
        val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        File(filesDir, "covers").listFiles()
            ?.filter { it.absolutePath !in used && it.lastModified() < cutoff }
            ?.forEach { it.delete() }
    }

    /** A fresh file in app-private storage for a cover photo. */
    fun newPhotoFile(): File {
        val dir = File(filesDir, "covers").apply { mkdirs() }
        // An import saves many photos in the same millisecond; each needs its own name.
        val stamp = System.currentTimeMillis()
        return generateSequence(0) { it + 1 }
            .map { n -> File(dir, if (n == 0) "cover_$stamp.jpg" else "cover_${stamp}_$n.jpg") }
            .first { !it.exists() }
    }
}

/** How many books' covers are looked for before the ones found are saved. */
private const val COVER_BATCH = 16

/** The next book in a series: on a shelf already ([owned]), or a catalogue edition to add. */
data class NextInSeries(val title: String, val owned: Book?, val record: CatalogRecord?)

/** A book just removed, at [at], for Undo. */
data class Removed(val book: Book, val at: Long)

/** [updated] books were already in the library, and the imported copy was newer. */
data class ImportResult(val added: Int, val skipped: Int, val updated: Int = 0)
