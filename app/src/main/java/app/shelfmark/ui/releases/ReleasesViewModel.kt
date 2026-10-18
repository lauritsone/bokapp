package app.shelfmark.ui.releases

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.shelfmark.data.BookRepository
import app.shelfmark.data.Match
import app.shelfmark.data.ReleaseKind
import app.shelfmark.data.ReleaseProgress
import app.shelfmark.data.ReleaseResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface ReleasesState {
    data class Loading(val progress: ReleaseProgress? = null) : ReleasesState
    data class Loaded(val result: ReleaseResult, val added: Set<String> = emptySet()) : ReleasesState
    data object Offline : ReleasesState
}

class ReleasesViewModel(private val repo: BookRepository) : ViewModel() {

    private val _kind = MutableStateFlow(ReleaseKind.New)

    /** New books or older ones; each kind is searched (and kept) on its own. */
    val kind: StateFlow<ReleaseKind> = _kind.asStateFlow()

    private val _state = MutableStateFlow(cachedOrLoading(ReleaseKind.New))
    val state: StateFlow<ReleasesState> = _state.asStateFlow()
    private var search: Job? = null

    init {
        if (repo.lastReleases(ReleaseKind.New) == null) refresh()
    }

    private fun cachedOrLoading(kind: ReleaseKind): ReleasesState =
        repo.lastReleases(kind)?.let { ReleasesState.Loaded(it, repo.addedReleases.toSet()) } ?: ReleasesState.Loading()

    fun setKind(kind: ReleaseKind) {
        if (kind == _kind.value) return
        _kind.value = kind
        // A search still running for the other kind is no longer wanted.
        search?.cancel()
        _state.value = cachedOrLoading(kind)
        if (_state.value is ReleasesState.Loading) refresh()
    }

    fun refresh() {
        val kind = _kind.value
        search?.cancel()
        _state.value = ReleasesState.Loading()
        search = viewModelScope.launch {
            _state.value = try {
                ReleasesState.Loaded(
                    repo.findReleases(kind) { p ->
                        // A search for the other kind may still report in as it stops.
                        if (_kind.value == kind) _state.update { if (it is ReleasesState.Loading) ReleasesState.Loading(p) else it }
                    },
                    repo.addedReleases.toSet(),
                )
            } catch (e: IOException) {
                ReleasesState.Offline
            }
        }
    }

    private val _justAdded = MutableStateFlow<Long?>(null)

    /** The id of the book just put on Want to read, until the page has said so. */
    val justAdded: StateFlow<Long?> = _justAdded.asStateFlow()

    fun addedShown() {
        _justAdded.value = null
    }

    fun addToWantToRead(match: Match) {
        val id = match.candidate.record.id
        if ((_state.value as? ReleasesState.Loaded)?.added?.contains(id) == true) return
        viewModelScope.launch {
            val bookId = repo.addToWantToRead(match)
            _state.update { s -> if (s is ReleasesState.Loaded) s.copy(added = s.added + match.candidate.record.id) else s }
            _justAdded.value = bookId
        }
    }
}
