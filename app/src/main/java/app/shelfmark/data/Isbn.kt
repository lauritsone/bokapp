package app.shelfmark.data

/** ISBN helpers: validates check digits and converts everything to ISBN-13. */
object Isbn {

    /** Returns a valid ISBN-13, or null if [input] isn't a valid ISBN-10/13. */
    fun normalize(input: String): String? {
        val s = input.uppercase().filter { it.isDigit() || it == 'X' }
        return when {
            s.length == 13 && isValid13(s) -> s
            s.length == 10 && isValid10(s) -> to13(s)
            else -> null
        }
    }

    private fun isValid13(s: String): Boolean {
        if (!s.all { it.isDigit() }) return false
        if (!s.startsWith("978") && !s.startsWith("979")) return false // book barcodes only
        return checkDigit13(s.take(12)) == s.last() - '0'
    }

    private fun isValid10(s: String): Boolean {
        if (!s.take(9).all { it.isDigit() }) return false
        val last = s[9]
        if (!last.isDigit() && last != 'X') return false
        var sum = 0
        for (i in 0 until 9) sum += (s[i] - '0') * (10 - i)
        sum += if (last == 'X') 10 else last - '0'
        return sum % 11 == 0
    }

    private fun to13(isbn10: String): String {
        val core = "978" + isbn10.take(9)
        return core + checkDigit13(core)
    }

    private fun checkDigit13(first12: String): Int {
        var sum = 0
        first12.forEachIndexed { i, c -> sum += (c - '0') * if (i % 2 == 0) 1 else 3 }
        return (10 - sum % 10) % 10
    }
}
