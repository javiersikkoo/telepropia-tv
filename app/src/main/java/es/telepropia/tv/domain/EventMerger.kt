package es.telepropia.tv.domain

import es.telepropia.tv.data.model.EventSource
import es.telepropia.tv.data.model.HostFetch
import es.telepropia.tv.data.model.LiveEvent
import es.telepropia.tv.data.model.LiveSnapshot
import es.telepropia.tv.data.model.Match
import java.text.Normalizer
import java.util.Locale

object EventMerger {

    private val marks = Regex("\\p{M}+")
    private val separators = Regex("[^a-z0-9]+")

    // Cada resolver escribe el cruce a su manera ("vs", "v", "-", tildes); sin quitar eso no se juntan nunca.
    private val connectors = setOf("vs", "v", "versus", "contra")

    fun normalize(name: String): String {
        val plain = Normalizer.normalize(name, Normalizer.Form.NFD).replace(marks, "")
        return plain.lowercase(Locale.ROOT)
            .replace(separators, " ")
            .split(' ')
            .filter { it.isNotEmpty() && it !in connectors }
            .joinToString(" ")
    }

    fun key(match: Match): String = "${match.sportId}|${normalize(match.name)}"

    /** El orden de los eventos y de las fuentes sigue al primario y luego al orden de alta de los hosts. */
    fun merge(fetch: HostFetch): LiveSnapshot {
        val rank = fetch.activeHosts.withIndex().associate { (index, host) -> host.id to index }
        val all = fetch.live.map { EventSource(it, live = true) } + fetch.stale.map { EventSource(it, live = false) }
        val sources = all.sortedBy { rank[it.match.sourceHostId] ?: Int.MAX_VALUE }

        val groups = LinkedHashMap<String, MutableList<EventSource>>()
        sources.forEach { source -> groups.getOrPut(key(source.match)) { mutableListOf() }.add(source) }

        val events = groups.map { (key, group) ->
            val ordered = group.sortedBy { !it.live }
            LiveEvent(
                key = key,
                name = ordered.first().match.name,
                sportId = ordered.first().match.sportId,
                sources = ordered
            )
        }
        return LiveSnapshot(
            events = events,
            failingHosts = fetch.failingHosts,
            activeHostCount = fetch.activeHosts.size
        )
    }
}
