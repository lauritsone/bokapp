package app.shelfmark.ui.components

import java.util.Locale
import androidx.compose.material.icons.Icons
import app.shelfmark.ui.icons.MenuBook
import app.shelfmark.ui.icons.Headphones
import app.shelfmark.ui.icons.TabletAndroid
import androidx.compose.ui.graphics.vector.ImageVector
import app.shelfmark.R
import app.shelfmark.data.BookFormat
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

fun formatLabel(format: BookFormat): Int = when (format) {
    BookFormat.Paper -> R.string.format_paper
    BookFormat.Ebook -> R.string.format_ebook
    BookFormat.Audiobook -> R.string.format_audiobook
}

fun formatIcon(format: BookFormat): ImageVector = when (format) {
    BookFormat.Paper -> Icons.AutoMirrored.Rounded.MenuBook
    BookFormat.Ebook -> Icons.Rounded.TabletAndroid
    BookFormat.Audiobook -> Icons.Rounded.Headphones
}
