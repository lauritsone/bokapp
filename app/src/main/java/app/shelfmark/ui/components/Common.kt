package app.shelfmark.ui.components

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

private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

fun formatDate(epochMillis: Long): String =
    dateFormat.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

const val NotesPlaceholder =
    "What stayed with you? A favourite line, how it made you feel, who you'd give it to…"
