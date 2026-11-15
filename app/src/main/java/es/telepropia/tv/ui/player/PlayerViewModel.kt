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
import es.telepropia.tv.domain.RetryPolicy
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PlayerState {
    data class Resolving(val retrying: Boolean) : PlayerState
    data class Ready(val spec: PlaybackSpec, val token: Int) : PlayerState
    data class Error(val failure: Failure) : PlayerState
}

class PlayerViewModel(
    private val resolveMatch: ResolveMatch,
    private val hostId: Long,
    private val matchId: String,
    private val sportId: Int
) : ViewModel() {

    private val _state = MutableStateFlow<PlayerState>(PlayerState.Resolving(retrying = false))
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var token = 0
    private var retried = false
    private var job: Job? = null

    init {
        load(trigger = null)
    }

    fun retry() {
        retried = false
        load(trigger = null)
    }

    fun onPlaybackFailed(failure: Failure) {
        if (RetryPolicy.shouldRetry(failure, retried)) {
            retried = true
            load(trigger = failure)
        } else {
            _state.value = PlayerState.Error(failure)
        }
    }

    fun onStreamEnded() {
        _state.value = PlayerState.Error(Failure.NotFound)
    }

    private fun load(trigger: Failure?) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = PlayerState.Resolving(retrying = trigger != null)
            _state.value = when (val outcome = resolveMatch(hostId, matchId, sportId)) {
                is Outcome.Ok -> PlayerState.Ready(outcome.value, ++token)
                is Outcome.Err -> PlayerState.Error(RetryPolicy.visibleFailure(trigger, outcome.failure))
            }
        }
    }

    companion object {
        fun factory(container: AppContainer, hostId: Long, matchId: String, sportId: Int) = viewModelFactory {
            initializer { PlayerViewModel(container.resolveMatch, hostId, matchId, sportId) }
        }
    }
}
