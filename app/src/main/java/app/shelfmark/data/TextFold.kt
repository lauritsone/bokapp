package app.shelfmark.data

import java.text.Normalizer
import java.util.Locale

private val combiningMarks = Regex("\\p{Mn}+")
private val separators = Regex("[^\\p{L}\\p{N}]+")

/**
 * Folds text for loose comparison: lower case, no accents, punctuation as single spaces.
 * "Britt-Marie" → "britt marie", "Långstrump" → "langstrump", "Nesbø" → "nesbo".
 */
fun foldText(s: String): String {
    // ø and æ don't decompose into a base letter, so spell them out first.
    val lower = s.lowercase(Locale.ROOT)
        .replace("ø", "o")
        .replace("æ", "ae")
        .replace("ß", "ss")
    return Normalizer.normalize(lower, Normalizer.Form.NFD)
        .replace(combiningMarks, "")
        .replace(separators, " ")
        .trim()
}
