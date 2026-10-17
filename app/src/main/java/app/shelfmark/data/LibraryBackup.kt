package app.shelfmark.data

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/** The layout of a full backup: books.json plus the user's cover photos. */
object LibraryBackup {
    const val BOOKS = "books.json"
    const val PHOTOS = "photos/"

    /** Photos bigger than this aren't cover photos; a damaged or foreign zip shouldn't fill the phone. */
    private const val MAX_PHOTO_BYTES = 20L * 1024 * 1024

    /** What a backup zip holds: the books' JSON and each photo's bytes by its path in the zip. */
    class Contents(val booksJson: String, val photos: Map<String, ByteArray>)

    fun isZip(bytes: ByteArray) = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    /** @throws ImportException if the zip has no books.json. */
    fun read(bytes: ByteArray): Contents {
        var json: String? = null
        val photos = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when {
                    entry.isDirectory -> Unit
                    entry.name == BOOKS -> json = zip.readBytes().toString(Charsets.UTF_8)
                    // Only plain names under photos/; "../" paths are never written anywhere.
                    entry.name.startsWith(PHOTOS) && entry.name.matches(Regex("photos/[A-Za-z0-9_-]+\\.jpg")) -> {
                        val data = readLimited(zip) ?: continue
                        photos[entry.name] = data
                    }
                }
            }
        }
        return Contents(json ?: throw ImportException("No $BOOKS in the backup"), photos)
    }

    private fun readLimited(zip: ZipInputStream): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = zip.read(buffer)
            if (n < 0) break
            total += n
            if (total > MAX_PHOTO_BYTES) return null
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}
