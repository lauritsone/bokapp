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
import app.shelfmark.ui.icons.CloudOff
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
import app.shelfmark.ui.components.BarSpinner
import app.shelfmark.ui.components.BookCover
import app.shelfmark.ui.components.CoverSize
import app.shelfmark.ui.components.EmptyState
import app.shelfmark.ui.components.LoadingState
import app.shelfmark.ui.components.ShelfColors
import app.shelfmark.ui.components.ShelfmarkTopBar
import app.shelfmark.ui.icons.AutoAwesome
import app.shelfmark.ui.theme.Dimens
import app.shelfmark.ui.theme.LocalExtendedColors
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalResources
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import app.shelfmark.ui.components.colors
import app.shelfmark.ui.components.rememberRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleasesScreen(onBack: () -> Unit, onOpenBook: (Long) -> Unit) {
    val repo = rememberRepository()
    val vm: ReleasesViewModel = viewModel(factory = viewModelFactory { initializer { ReleasesViewModel(repo) } })
    val state by vm.state.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<Match?>(null) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val justAdded by vm.justAdded.collectAsStateWithLifecycle()
    LaunchedEffect(justAdded) {
        val id = justAdded ?: return@LaunchedEffect
        val choice = snackbar.showSnackbar(
            resources.getString(R.string.added_to_want),
            actionLabel = resources.getString(R.string.open),
            duration = SnackbarDuration.Short,
        )
        vm.addedShown()
        if (choice == SnackbarResult.ActionPerformed) onOpenBook(id)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            ShelfmarkTopBar(
                title = stringResource(R.string.releases_title),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
                actions = {
                    val loading = state is ReleasesState.Loading
                    IconButton(onClick = vm::refresh, enabled = !loading) {
                        if (loading) BarSpinner() else Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            is ReleasesState.Loading -> LoadingState(Modifier.padding(padding), text = progressText(s.progress))
            ReleasesState.Offline -> EmptyState(
                title = null,
                body = stringResource(R.string.releases_offline),
                icon = Icons.Rounded.CloudOff,
                modifier = Modifier.padding(padding).padding(horizontal = Dimens.gutter),
            ) {
                Button(onClick = vm::refresh) { Text(stringResource(R.string.try_again)) }
            }
            is ReleasesState.Loaded -> ReleaseList(s, padding, onOpen = { open = it }, onRefresh = vm::refresh)
        }
    }

    val current = open
    val loaded = state as? ReleasesState.Loaded
    if (current != null && loaded != null) {
        ModalBottomSheet(onDismissRequest = { open = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            ReleaseDetails(
                match = current,
                added = current.candidate.record.id in loaded.added,
                onAdd = {
                    vm.addToWantToRead(current)
                    // Closed, so the message about it shows.
                    open = null
                },
            )
        }
    }
}

@Composable
private fun progressText(progress: ReleaseProgress?): String = when (progress) {
    is ReleaseProgress.LearningTaste -> pluralStringResource(R.plurals.releases_learning, progress.total, progress.done, progress.total)
    is ReleaseProgress.ReadingDetails -> stringResource(R.string.releases_reading, progress.done, progress.total)
    else -> stringResource(R.string.releases_searching)
}

/** Which languages to show; English books come from Open Library, Norwegian from NB.no. */
private enum class LanguageFilter(@StringRes val label: Int, val language: String?) {
    All(R.string.lang_all, null),
    Norwegian(R.string.lang_norwegian, "nob"),
    English(R.string.lang_english, "eng"),
}

@Composable
private fun ReleaseList(state: ReleasesState.Loaded, padding: PaddingValues, onOpen: (Match) -> Unit, onRefresh: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf(LanguageFilter.All) }
    val shown = state.result.matches.filter { filter.language == null || it.candidate.record.language == filter.language }
    val layoutDirection = LocalLayoutDirection.current
    LazyColumn(
        Modifier.fillMaxSize(),
        // Scrolls under the top bar and the navigation bar.
        contentPadding = PaddingValues(
            start = padding.calculateStartPadding(layoutDirection) + Dimens.gutter,
            end = padding.calculateEndPadding(layoutDirection) + Dimens.gutter,
            top = padding.calculateTopPadding() + Dimens.topGap,
            bottom = padding.calculateBottomPadding() + Dimens.bottomGap,
        ),
    ) {
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
                            label = { Text(stringResource(f.label)) },
                        )
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            item(key = "empty") {
                EmptyState(title = null, body = stringResource(R.string.releases_empty), icon = Icons.Rounded.AutoAwesome) {
                    // Books may be waiting in the other language; otherwise, a new search.
                    if (filter != LanguageFilter.All && state.result.matches.isNotEmpty()) {
                        TextButton(onClick = { filter = LanguageFilter.All }) { Text(stringResource(R.string.show_all_languages)) }
                    } else {
                        TextButton(onClick = onRefresh) { Text(stringResource(R.string.try_again)) }
                    }
                }
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
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        BookCover(c.record.coverUrl, c.record.title, CoverSize.Standard)
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

/** The score as a pill: gold for strong matches, blue-grey for good ones, grey otherwise. */
@Composable
private fun MatchBadge(percent: Int) {
    val extended = LocalExtendedColors.current
    val colors = when {
        percent >= 75 -> ShelfColors(extended.strongMatchContainer, extended.onStrongMatchContainer)
        percent >= 50 -> ShelfColors(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
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
            .padding(horizontal = Dimens.gutter)
            .padding(bottom = Dimens.bottomGap)
            .navigationBarsPadding(),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            BookCover(c.record.coverUrl, c.record.title, CoverSize.Medium)
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(c.record.title, style = MaterialTheme.typography.titleLarge)
                if (c.author.isNotBlank()) {
                    Text(c.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().height(Dimens.buttonHeight)) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.added_to_want))
            }
        } else {
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().height(Dimens.buttonHeight)) {
                Text(stringResource(R.string.add_to_want))
            }
        }
        // The description comes last, so the button stays in reach without scrolling.
        c.summary?.let {
            Spacer(Modifier.height(20.dp))
            Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
