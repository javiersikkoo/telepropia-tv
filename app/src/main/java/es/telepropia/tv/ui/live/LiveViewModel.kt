package es.telepropia.tv.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import es.telepropia.tv.AppContainer
import es.telepropia.tv.data.SourceRepository
import es.telepropia.tv.data.model.Failure
import es.telepropia.tv.data.model.LiveEvent
import es.telepropia.tv.data.model.LiveSnapshot
import es.telepropia.tv.data.model.Outcome
import es.telepropia.tv.data.model.Sport
import es.telepropia.tv.data.model.Sports
import es.telepropia.tv.domain.ListLiveEvents
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EventRow(val event: LiveEvent, val detectedAt: Long)

data class Degraded(val failing: Int, val active: Int)

sealed interface LiveState {
    data object Loading : LiveState
    data class Empty(val degraded: Degraded?) : LiveState
    data class Content(val rows: List<EventRow>, val degraded: Degraded?) : LiveState
    data class Error(val failure: Failure) : LiveState
}

class LiveViewModel(
    private val repository: SourceRepository,
    private val listLiveEvents: ListLiveEvents,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    val sports: List<Sport> = Sports.all

    val activeSportId: StateFlow<Int> =
        repository.activeSportId.stateIn(viewModelScope, SharingStarted.Eagerly, Sports.DEFAULT_ID)

    private val _state = MutableStateFlow<LiveState>(LiveState.Loading)
    val state: StateFlow<LiveState> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

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
            _refreshing.value = true
            try {
                val id = sportId ?: repository.activeSportId.first()
                if (_state.value is LiveState.Error) _state.value = LiveState.Loading
                _state.value = when (val outcome = listLiveEvents(id)) {
                    is Outcome.Ok -> toState(outcome.value)
                    is Outcome.Err -> LiveState.Error(outcome.failure)
                }
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun forgetDetections() {
        firstSeen.clear()
    }

    suspend fun needsSetup(): Boolean = !repository.hasHosts()

    private fun toState(snapshot: LiveSnapshot): LiveState {
        val degraded = if (snapshot.degraded) {
            Degraded(failing = snapshot.failingHosts.size, active = snapshot.activeHostCount)
        } else {
            null
        }
        if (snapshot.events.isEmpty()) {
            firstSeen.clear()
            return LiveState.Empty(degraded)
        }
        val now = clock()
        // Un evento que desaparece y vuelve cuenta como nuevo.
        firstSeen.keys.retainAll(snapshot.events.map { it.key }.toSet())
        val rows = snapshot.events.map { EventRow(it, firstSeen.getOrPut(it.key) { now }) }
        return LiveState.Content(rows, degraded)
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { LiveViewModel(container.repository, container.listLiveEvents) }
        }
    }
}
