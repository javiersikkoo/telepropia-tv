package es.telepropia.tv.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import es.telepropia.tv.AppContainer
import es.telepropia.tv.data.SourceRepository
import es.telepropia.tv.data.model.Failure
import es.telepropia.tv.data.model.Match
import es.telepropia.tv.data.model.Outcome
import es.telepropia.tv.data.model.Sport
import es.telepropia.tv.data.model.Sports
import es.telepropia.tv.domain.ListLiveMatches
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LiveRow(val match: Match, val detectedAt: Long)

sealed interface LiveState {
    data object Loading : LiveState
    data object Empty : LiveState
    data class Content(val rows: List<LiveRow>) : LiveState
    data class Error(val failure: Failure) : LiveState
}

class LiveViewModel(
    private val repository: SourceRepository,
    private val listLiveMatches: ListLiveMatches,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    val sports: List<Sport> = Sports.all

    val activeSportId: StateFlow<Int> =
        repository.activeSportId.stateIn(viewModelScope, SharingStarted.Eagerly, Sports.DEFAULT_ID)

    private val _state = MutableStateFlow<LiveState>(LiveState.Loading)
    val state: StateFlow<LiveState> = _state.asStateFlow()

    // Solo etiqueta, no ordena. Vive en memoria: se pierde al cambiar de deporte y al salir de la pantalla.
    private val firstSeen = HashMap<String, Long>()
    private var loadJob: Job? = null

    fun selectSport(sportId: Int) {
        firstSeen.clear()
        _state.value = LiveState.Loading
        viewModelScope.launch { repository.setActiveSport(sportId) }
        refresh(sportId)
    }

    fun refresh(sportId: Int? = null) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val id = sportId ?: repository.activeSportId.first()
            if (_state.value !is LiveState.Content) _state.value = LiveState.Loading
            _state.value = when (val outcome = listLiveMatches(id)) {
                is Outcome.Ok -> toState(outcome.value)
                is Outcome.Err -> LiveState.Error(outcome.failure)
            }
        }
    }

    fun forgetDetections() {
        firstSeen.clear()
    }

    suspend fun needsSetup(): Boolean = !repository.hasHosts()

    private fun toState(matches: List<Match>): LiveState {
        if (matches.isEmpty()) {
            firstSeen.clear()
            return LiveState.Empty
        }
        val now = clock()
        val present = matches.map { it.matchId }.toSet()
        // Un partido que desaparece y vuelve cuenta como nuevo.
        firstSeen.keys.retainAll(present)
        val rows = matches.map { match ->
            LiveRow(match, firstSeen.getOrPut(match.matchId) { now })
        }
        return LiveState.Content(rows)
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { LiveViewModel(container.repository, container.listLiveMatches) }
        }
    }
}
