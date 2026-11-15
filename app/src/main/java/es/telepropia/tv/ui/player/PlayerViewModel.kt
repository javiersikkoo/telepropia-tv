package es.telepropia.tv.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import es.telepropia.tv.AppContainer
import es.telepropia.tv.data.model.Failure
import es.telepropia.tv.data.model.Outcome
import es.telepropia.tv.data.model.PlaybackSpec
import es.telepropia.tv.domain.ResolveMatch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PlayerState {
    data object Resolving : PlayerState
    data class Ready(val spec: PlaybackSpec, val token: Int) : PlayerState
    data class Error(val failure: Failure) : PlayerState
}

class PlayerViewModel(
    private val resolveMatch: ResolveMatch,
    private val matchId: String,
    private val sportId: Int
) : ViewModel() {

    private val _state = MutableStateFlow<PlayerState>(PlayerState.Resolving)
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var token = 0
    private var expiryRetried = false
    private var job: Job? = null

    init {
        load()
    }

    fun retry() {
        expiryRetried = false
        load()
    }

    // F1 solo reintenta el enlace caducado (403/487): una re-resolución y un intento. El resto, en F2.
    fun onPlaybackFailed(failure: Failure) {
        if (failure == Failure.Expired && !expiryRetried) {
            expiryRetried = true
            load()
        } else {
            _state.value = PlayerState.Error(failure)
        }
    }

    fun onStreamEnded() {
        _state.value = PlayerState.Error(Failure.NotFound)
    }

    private fun load() {
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = PlayerState.Resolving
            _state.value = when (val outcome = resolveMatch(matchId, sportId)) {
                is Outcome.Ok -> PlayerState.Ready(outcome.value, ++token)
                is Outcome.Err -> PlayerState.Error(outcome.failure)
            }
        }
    }

    companion object {
        fun factory(container: AppContainer, matchId: String, sportId: Int) = viewModelFactory {
            initializer { PlayerViewModel(container.resolveMatch, matchId, sportId) }
        }
    }
}
