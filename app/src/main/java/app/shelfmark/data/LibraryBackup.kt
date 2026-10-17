package app.shelfmark.data

import java.io.BufferedInputStream
import java.io.File
import java.util.zip.ZipInputStream

/** The layout of a full backup: books.json plus the user's cover photos. */
object LibraryBackup {
    const val BOOKS = "books.json"
    const val PHOTOS = "photos/"

    /** Photos bigger than this aren't cover photos; a damaged or foreign zip shouldn't fill the phone. */
    private const val MAX_PHOTO_BYTES = 20L * 1024 * 1024

    private val photoName = Regex("photos/[A-Za-z0-9_-]+\\.jpg")

    /** What a backup zip holds: the books' JSON and, by its path in the zip, the file each photo was saved to. */
    class Contents(val booksJson: String, val photos: Map<String, File>)

    /** Whether [input] starts like a zip file; nothing is consumed. */
    fun isZip(input: BufferedInputStream): Boolean {
        input.mark(2)
        val first = input.read()
        val second = input.read()
        input.reset()
        return first == 'P'.code && second == 'K'.code
    }

    /**
     * Reads a backup zip from [input], writing each photo straight to a file from [newFile],
     * so a large library is never held in memory at once. If reading fails part way, the
     * photos written so far are deleted again.
     * @throws ImportException if the zip has no books.json.
     */
    fun read(input: java.io.InputStream, newFile: () -> File): Contents {
        var json: String? = null
        val photos = mutableMapOf<String, File>()
        try {
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    when {
                        entry.isDirectory -> Unit
                        entry.name == BOOKS -> json = zip.readBytes().toString(Charsets.UTF_8)
                        // Only plain names under photos/; "../" paths are never written anywhere.
                        entry.name.matches(photoName) && entry.name !in photos -> {
                            val file = newFile()
                            if (copyLimited(zip, file)) photos[entry.name] = file else file.delete()
                        }
                    }
                }
            }
            return Contents(json ?: throw ImportException("No $BOOKS in the backup"), photos)
        } catch (e: Exception) {
            photos.values.forEach { it.delete() }
            throw e
        }
    }

    /** Copies the current entry to [file]; false if it is bigger than a cover photo can be. */
    private fun copyLimited(zip: ZipInputStream, file: File): Boolean {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        file.outputStream().use { out ->
            while (true) {
                val n = zip.read(buffer)
                if (n < 0) break
                total += n
                if (total > MAX_PHOTO_BYTES) return false
                out.write(buffer, 0, n)
            }
        }
        return true
    }
}
