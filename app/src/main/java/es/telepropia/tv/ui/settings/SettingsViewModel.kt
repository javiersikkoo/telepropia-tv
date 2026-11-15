package es.telepropia.tv.ui.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import es.telepropia.tv.AppContainer
import es.telepropia.tv.R
import es.telepropia.tv.data.AddHostResult
import es.telepropia.tv.data.SourceRepository
import es.telepropia.tv.data.model.HostState
import es.telepropia.tv.data.model.HostStatus
import es.telepropia.tv.data.model.SourceHealth
import es.telepropia.tv.data.model.SourceHost
import es.telepropia.tv.domain.ProbeSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ProbeUi {
    data object Idle : ProbeUi
    data object Running : ProbeUi
    data class Done(val health: SourceHealth) : ProbeUi
}

data class HostRow(val status: HostStatus, val probe: ProbeUi) {
    val host: SourceHost get() = status.host
}

data class Feedback(@StringRes val textRes: Int, val clearInput: Boolean)

class SettingsViewModel(
    private val repository: SourceRepository,
    private val probeSource: ProbeSource
) : ViewModel() {

    private val probes = MutableStateFlow<Map<Long, ProbeUi>>(emptyMap())

    val rows: StateFlow<List<HostRow>> = combine(repository.hostStatuses, probes) { statuses, results ->
        statuses.map { HostRow(it, results[it.host.id] ?: ProbeUi.Idle) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val _feedback = MutableStateFlow<Feedback?>(null)
    val feedback: StateFlow<Feedback?> = _feedback.asStateFlow()

    fun add(raw: String) {
        viewModelScope.launch {
            when (val result = repository.addHost(raw)) {
                is AddHostResult.Added -> {
                    _feedback.value = Feedback(R.string.settings_added, clearInput = true)
                    probe(result.host)
                }
                AddHostResult.Duplicate -> _feedback.value = Feedback(R.string.settings_duplicate, clearInput = false)
                AddHostResult.Invalid -> _feedback.value = Feedback(R.string.settings_invalid, clearInput = false)
            }
        }
    }

    fun onFeedbackShown() {
        _feedback.update { it?.copy(clearInput = false) }
    }

    fun setPrimary(host: SourceHost) {
        viewModelScope.launch { repository.setPrimary(host.id) }
    }

    fun toggleState(host: SourceHost) {
        val next = if (host.state == HostState.ACTIVE) HostState.PAUSED else HostState.ACTIVE
        probes.update { it - host.id }
        viewModelScope.launch { repository.setState(host.id, next) }
    }

    fun delete(host: SourceHost) {
        probes.update { it - host.id }
        viewModelScope.launch { repository.deleteHost(host.id) }
    }

    fun probe(host: SourceHost) {
        if (probes.value[host.id] == ProbeUi.Running) return
        probes.update { it + (host.id to ProbeUi.Running) }
        viewModelScope.launch {
            val health = probeSource(host)
            probes.update { it + (host.id to ProbeUi.Done(health)) }
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(container: AppContainer) = viewModelFactory {
            initializer { SettingsViewModel(container.repository, container.probeSource) }
        }
    }
}
