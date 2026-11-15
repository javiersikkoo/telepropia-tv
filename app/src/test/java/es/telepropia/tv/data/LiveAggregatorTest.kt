package es.telepropia.tv.data

import es.telepropia.tv.data.model.Failure
import es.telepropia.tv.data.model.HostState
import es.telepropia.tv.data.model.LiveSnapshot
import es.telepropia.tv.data.model.Match
import es.telepropia.tv.data.model.Outcome
import es.telepropia.tv.data.model.SourceHost
import es.telepropia.tv.domain.EventMerger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveAggregatorTest {

    private val first = SourceHost(1, "http://192.168.1.10:3001", label = "Sitio A")
    private val second = SourceHost(2, "http://192.168.1.10:3002")

    private var now = 0L
    private val responses = mutableMapOf<Long, Outcome<List<Match>>>()
    private val requested = mutableListOf<Long>()

    private val aggregator = LiveAggregator(
        fetchHost = { host, _ ->
            requested += host.id
            responses.getValue(host.id)
        },
        pauses = HostPauses(cooldownMs = 1_000),
        clock = { now }
    )

    private fun match(host: SourceHost, id: String, name: String) =
        Match(id, 1, name, host.id, host.baseUrl.substringAfter("://"))

    private fun snapshot(vararg hosts: SourceHost): LiveSnapshot = runBlocking {
        val fetched = aggregator.fetch(hosts.toList(), 1)
        EventMerger.merge((fetched as Outcome.Ok).value)
    }

    @Test
    fun `cierre F2 - dos hosts, un evento con dos fuentes, pausar el primero de la lista deja la segunda`() {
        responses[first.id] = Outcome.Ok(listOf(match(first, "100", "Real Madrid vs Barcelona")))
        responses[second.id] = Outcome.Ok(listOf(match(second, "9", "Real Madrid - Barcelona")))

        val both = snapshot(first, second)
        assertEquals(1, both.events.size)
        val event = both.events.single()
        assertEquals(listOf(first.id, second.id), event.liveSources.map { it.sourceHostId })
        assertEquals(listOf("100", "9"), event.liveSources.map { it.matchId })

        requested.clear()
        val paused = snapshot(first.copy(state = HostState.PAUSED), second)
        assertEquals(listOf(second.id), requested)
        val remaining = paused.events.single()
        assertEquals(listOf(second.id), remaining.liveSources.map { it.sourceHostId })
        assertTrue(remaining.hasLiveSource)
        assertFalse(paused.degraded)
    }

    @Test
    fun `host que falla se pausa en memoria, sus eventos quedan marcados y no se le vuelve a pedir`() {
        responses[first.id] = Outcome.Ok(listOf(match(first, "1", "A vs B"), match(first, "2", "C vs D")))
        responses[second.id] = Outcome.Ok(listOf(match(second, "5", "A v B")))
        snapshot(first, second)

        responses[first.id] = Outcome.Err(Failure.Timeout)
        val degraded = snapshot(first, second)
        assertTrue(degraded.degraded)
        assertEquals(listOf(first), degraded.failingHosts)

        val ab = degraded.events.first { EventMerger.normalize(it.name) == "a b" }
        assertEquals(listOf(second.id), ab.liveSources.map { it.sourceHostId })
        val cd = degraded.events.first { EventMerger.normalize(it.name) == "c d" }
        assertFalse(cd.hasLiveSource)

        requested.clear()
        snapshot(first, second)
        assertEquals(listOf(second.id), requested)

        now += 1_000
        requested.clear()
        responses[first.id] = Outcome.Ok(listOf(match(first, "1", "A vs B")))
        val back = snapshot(first, second)
        assertEquals(listOf(first.id, second.id), requested)
        assertFalse(back.degraded)
    }

    @Test
    fun `todos caidos y sin lista previa es un error con el fallo real`() = runBlocking {
        responses[first.id] = Outcome.Err(Failure.Upstream)
        val result = aggregator.fetch(listOf(first), 1)
        assertEquals(Outcome.Err(Failure.Upstream), result)
    }

    @Test
    fun `sin hosts activos`() = runBlocking {
        val result = aggregator.fetch(listOf(first.copy(state = HostState.PAUSED)), 1)
        assertEquals(Outcome.Err(Failure.NoActiveHost), result)
    }

    @Test
    fun `el orden de la lista en ajustes manda dentro de cada evento`() {
        responses[first.id] = Outcome.Ok(listOf(match(first, "100", "X vs Y")))
        responses[second.id] = Outcome.Ok(listOf(match(second, "9", "X - Y")))

        val reordered = snapshot(second, first)
        assertEquals(listOf(second.id, first.id), reordered.events.single().liveSources.map { it.sourceHostId })
    }
}
