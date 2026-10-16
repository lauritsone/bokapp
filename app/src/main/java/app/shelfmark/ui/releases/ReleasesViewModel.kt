package app.shelfmark.ui.releases

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.shelfmark.data.BookRepository
import app.shelfmark.data.Match
import app.shelfmark.data.ReleaseProgress
import app.shelfmark.data.ReleaseResult
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

    private val _state = MutableStateFlow<ReleasesState>(
        repo.lastReleases?.let { ReleasesState.Loaded(it, repo.addedReleases.toSet()) } ?: ReleasesState.Loading(),
    )
    val state: StateFlow<ReleasesState> = _state.asStateFlow()

    init {
        if (repo.lastReleases == null) refresh()
    }

    fun refresh() {
        _state.value = ReleasesState.Loading()
        viewModelScope.launch {
            _state.value = try {
                ReleasesState.Loaded(
                    repo.findReleases { p -> _state.update { if (it is ReleasesState.Loading) ReleasesState.Loading(p) else it } },
                    repo.addedReleases.toSet(),
                )
            } catch (e: IOException) {
                ReleasesState.Offline
            }
        }
    }

    fun addToWantToRead(match: Match) {
        val id = match.candidate.record.id
        if ((_state.value as? ReleasesState.Loaded)?.added?.contains(id) == true) return
        viewModelScope.launch {
            repo.addToWantToRead(match)
            _state.update { s -> if (s is ReleasesState.Loaded) s.copy(added = s.added + match.candidate.record.id) else s }
        }
    }
}
