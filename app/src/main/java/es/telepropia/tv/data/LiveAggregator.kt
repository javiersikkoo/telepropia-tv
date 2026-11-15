package es.telepropia.tv.data

import es.telepropia.tv.data.model.Failure
import es.telepropia.tv.data.model.HostFetch
import es.telepropia.tv.data.model.HostState
import es.telepropia.tv.data.model.Match
import es.telepropia.tv.data.model.Outcome
import es.telepropia.tv.data.model.SourceHost
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal class LiveAggregator(
    private val fetchHost: suspend (SourceHost, Int) -> Outcome<List<Match>>,
    private val pauses: HostPauses,
    private val clock: () -> Long
) {
    // Última lista buena de cada host, solo en memoria: con ella se marcan los eventos de un host caído.
    private val lastLive = ConcurrentHashMap<Pair<Long, Int>, List<Match>>()

    suspend fun fetch(hosts: List<SourceHost>, sportId: Int): Outcome<HostFetch> {
        if (hosts.isEmpty()) return Outcome.Err(Failure.NoHost)
        val active = hosts.filter { it.state == HostState.ACTIVE }.primaryFirst()
        if (active.isEmpty()) return Outcome.Err(Failure.NoActiveHost)

        val now = clock()
        val due = active.filter { pauses.dueForRequest(it, now) }
        val results = coroutineScope {
            due.map { host -> async { host to fetchHost(host, sportId) } }.awaitAll()
        }

        val live = mutableListOf<Match>()
        results.forEach { (host, outcome) ->
            when (outcome) {
                is Outcome.Ok -> {
                    pauses.resume(host.id)
                    lastLive[host.id to sportId] = outcome.value
                    live += outcome.value
                }
                is Outcome.Err -> pauses.pause(host.id, outcome.failure, now)
            }
        }

        val failing = active.filter { pauses.isPaused(it.id) }
        val stale = failing.flatMap { lastLive[it.id to sportId].orEmpty() }
        if (failing.size == active.size && stale.isEmpty()) {
            return Outcome.Err(pauses.failureOf(failing.first().id) ?: Failure.Timeout)
        }
        return Outcome.Ok(HostFetch(activeHosts = active, live = live, stale = stale, failingHosts = failing))
    }

    fun forget(hostId: Long) {
        lastLive.keys.removeAll { it.first == hostId }
    }
}
