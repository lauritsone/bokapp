package app.shelfmark.ui.components

import java.util.Locale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.shelfmark.ShelfmarkApp
import app.shelfmark.data.BookRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun rememberRepository(): BookRepository {
    val context = LocalContext.current
    return remember { (context.applicationContext as ShelfmarkApp).repository }
}

fun formatDate(epochMillis: Long, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
