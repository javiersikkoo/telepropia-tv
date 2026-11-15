package es.telepropia.tv.data

import es.telepropia.tv.data.model.Failure
import es.telepropia.tv.data.model.HostState
import es.telepropia.tv.data.model.SourceHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class Pause(val untilMs: Long, val failure: Failure)

/**
 * Pausa en memoria de los hosts que fallan. Nunca toca Room: un host caído no se borra ni cambia de estado.
 */
internal class HostPauses(private val cooldownMs: Long = DEFAULT_COOLDOWN_MS) {

    private val pauses = MutableStateFlow<Map<Long, Pause>>(emptyMap())
    val current: StateFlow<Map<Long, Pause>> = pauses.asStateFlow()

    fun pause(hostId: Long, failure: Failure, nowMs: Long) {
        pauses.update { it + (hostId to Pause(nowMs + cooldownMs, failure)) }
    }

    fun resume(hostId: Long) {
        pauses.update { it - hostId }
    }

    fun isPaused(hostId: Long): Boolean = pauses.value.containsKey(hostId)

    fun failureOf(hostId: Long): Failure? = pauses.value[hostId]?.failure

    // Sin la espera, un host caído no volvería nunca a la lista sin pasar por ajustes.
    fun dueForRequest(host: SourceHost, nowMs: Long): Boolean {
        if (host.state != HostState.ACTIVE) return false
        val pause = pauses.value[host.id] ?: return true
        return nowMs >= pause.untilMs
    }

    companion object {
        const val DEFAULT_COOLDOWN_MS = 5 * 60_000L
    }
}
