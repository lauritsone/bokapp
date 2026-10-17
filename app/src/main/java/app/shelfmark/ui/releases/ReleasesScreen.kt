package app.shelfmark.ui.releases

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.FilterChip
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.shelfmark.R
import app.shelfmark.data.Match
import app.shelfmark.data.MatchReason
import app.shelfmark.data.ReleaseProgress
import app.shelfmark.data.Shelf
import app.shelfmark.ui.components.BookCover
import app.shelfmark.ui.components.colors
import app.shelfmark.ui.components.rememberRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleasesScreen(onBack: () -> Unit) {
    val repo = rememberRepository()
    val vm: ReleasesViewModel = viewModel(factory = viewModelFactory { initializer { ReleasesViewModel(repo) } })
    val state by vm.state.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<Match?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.releases_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = vm::refresh, enabled = state !is ReleasesState.Loading) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is ReleasesState.Loading -> Loading(s.progress)
                ReleasesState.Offline -> Offline(onRetry = vm::refresh)
                is ReleasesState.Loaded -> ReleaseList(s, onOpen = { open = it })
            }
        }
    }

    val current = open
    val loaded = state as? ReleasesState.Loaded
    if (current != null && loaded != null) {
        ModalBottomSheet(onDismissRequest = { open = null }) {
            ReleaseDetails(
                match = current,
                added = current.candidate.record.id in loaded.added,
                onAdd = { vm.addToWantToRead(current) },
            )
        }
    }
}

@Composable
private fun Loading(progress: ReleaseProgress?) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(20.dp))
        Text(
            when (progress) {
                is ReleaseProgress.LearningTaste -> stringResource(R.string.releases_learning, progress.done, progress.total)
                is ReleaseProgress.ReadingDetails -> stringResource(R.string.releases_reading, progress.done, progress.total)
                else -> stringResource(R.string.releases_searching)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Offline(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.releases_offline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.try_again)) }
    }
}

/** Which languages to show; English books come from Open Library, Norwegian from NB.no. */
private enum class LanguageFilter(@StringRes val label: Int, val language: String?) {
    All(R.string.lang_all, null),
    Norwegian(R.string.lang_norwegian, "nob"),
    English(R.string.lang_english, "eng"),
}

@Composable
private fun ReleaseList(state: ReleasesState.Loaded, onOpen: (Match) -> Unit) {
    var filter by rememberSaveable { mutableStateOf(LanguageFilter.All) }
    val shown = state.result.matches.filter { filter.language == null || it.candidate.record.language == filter.language }
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
        item(key = "intro") {
            Column(Modifier.padding(bottom = 8.dp)) {
                Text(
                    stringResource(R.string.releases_source),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.result.profileSize < 5) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.releases_few_books),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (state.result.ignored > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        pluralStringResource(R.plurals.releases_ignored, state.result.ignored, state.result.ignored),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LanguageFilter.entries.forEach { f ->
                        FilterChip(
                            selected = f == filter,
                            onClick = { filter = f },
                            label = { Text(stringResource(f.label), style = MaterialTheme.typography.labelLarge) },
                        )
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.releases_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                )
            }
        }
        items(shown, key = { it.candidate.record.id }) { match ->
            ReleaseRow(match, added = match.candidate.record.id in state.added, onClick = { onOpen(match) })
            HorizontalDivider(
                modifier = Modifier.padding(start = 88.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun ReleaseRow(match: Match, added: Boolean, onClick: () -> Unit) {
    val c = match.candidate
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        BookCover(c.record.coverUrl, c.record.title, Modifier.width(68.dp), elevation = 3.dp)
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(c.record.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val byline = listOfNotNull(
                c.author.ifBlank { null },
                if (c.record.language == "eng") stringResource(R.string.lang_english) else null,
            ).joinToString(" · ")
            if (byline.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    byline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MatchBadge(match.percent)
                if (added) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.added_to_want), tint = Shelf.WantToRead.colors().content, modifier = Modifier.size(18.dp))
                }
            }
            match.reasons.firstOrNull()?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    reasonText(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The score as a pill: green for strong matches, indigo for good ones, grey otherwise. */
@Composable
private fun MatchBadge(percent: Int) {
    val colors = when {
        percent >= 75 -> Shelf.Read.colors()
        percent >= 50 -> Shelf.Reading.colors()
        else -> null
    }
    Text(
        stringResource(R.string.match_percent, percent),
        style = MaterialTheme.typography.labelLarge,
        color = colors?.content ?: MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .background(colors?.container ?: MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
private fun reasonText(reason: MatchReason): String = when (reason) {
    is MatchReason.Author ->
        if (reason.rating != null) stringResource(R.string.reason_author, reason.name, reason.rating)
        else stringResource(R.string.reason_author_unrated, reason.name)
    is MatchReason.Genre -> pluralStringResource(
        R.plurals.reason_genre,
        reason.books,
        genreNames[reason.tag]?.let { stringResource(it) } ?: reason.label,
        reason.books,
    )
    // Subjects are catalogued in Norwegian with a capital ("Vennskap"); places keep theirs.
    is MatchReason.Theme -> pluralStringResource(R.plurals.reason_theme, reason.books, reason.label.lowercaseUnlessName(), reason.books)
    is MatchReason.Series -> stringResource(R.string.reason_series, reason.name)
}

private val placeNames = Regex("^(Oslo|Bergen|Trondheim|Stavanger|Paris|London|Norge|Sverige|Danmark|[A-ZÆØÅ][a-zæøå]+ (fylke|kommune))$")

private fun String.lowercaseUnlessName() = if (placeNames.matches(this)) this else replaceFirstChar { it.lowercase() }

/** Short names, in the app's language, for the shared genre tags. */
private val genreNames = mapOf(
    "crime" to R.string.genre_crime, "fantasy" to R.string.genre_fantasy, "scifi" to R.string.genre_scifi,
    "horror" to R.string.genre_horror, "romance" to R.string.genre_romance, "feelgood" to R.string.genre_feelgood, "historical" to R.string.genre_historical,
    "novel" to R.string.genre_novel, "shortstories" to R.string.genre_shortstories, "poetry" to R.string.genre_poetry,
    "children" to R.string.genre_children, "youngadult" to R.string.genre_youngadult, "biography" to R.string.genre_biography,
    "history" to R.string.genre_history, "essays" to R.string.genre_essays, "humor" to R.string.genre_humor,
    "selfhelp" to R.string.genre_selfhelp, "cooking" to R.string.genre_cooking, "travel" to R.string.genre_travel,
    "nature" to R.string.genre_nature, "sport" to R.string.genre_sport, "comics" to R.string.genre_comics,
)

@Composable
private fun ReleaseDetails(match: Match, added: Boolean, onAdd: () -> Unit) {
    val c = match.candidate
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp)
            .navigationBarsPadding(),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            BookCover(c.record.coverUrl, c.record.title, Modifier.width(96.dp), elevation = 6.dp)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(c.record.title, style = MaterialTheme.typography.titleLarge)
                if (c.author.isNotBlank()) {
                    Text(c.author, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val facts = listOfNotNull(
                    c.record.publisher,
                    c.record.year?.toString(),
                    if (c.record.language == "eng") stringResource(R.string.lang_english) else null,
                    c.genreLabels.takeIf { it.isNotEmpty() }?.take(4)?.joinToString(", "),
                )
                Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                MatchBadge(match.percent)
            }
        }
        if (match.reasons.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            match.reasons.forEach {
                Text("•  " + reasonText(it), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
        if (added) {
            OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.added_to_want))
            }
        } else {
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.add_to_want), style = MaterialTheme.typography.labelLarge)
            }
        }
        // The description comes last, so the button stays in reach without scrolling.
        c.summary?.let {
            Spacer(Modifier.height(20.dp))
            Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
