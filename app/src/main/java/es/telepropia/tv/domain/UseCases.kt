package es.telepropia.tv.domain

import es.telepropia.tv.data.SourceRepository
import es.telepropia.tv.data.model.LiveSnapshot
import es.telepropia.tv.data.model.Outcome
import es.telepropia.tv.data.model.PlaybackSpec
import es.telepropia.tv.data.model.SourceHealth
import es.telepropia.tv.data.model.SourceHost

class ListLiveEvents(private val repository: SourceRepository) {
    suspend operator fun invoke(sportId: Int): Outcome<LiveSnapshot> =
        when (val fetched = repository.fetchLive(sportId)) {
            is Outcome.Ok -> Outcome.Ok(EventMerger.merge(fetched.value))
            is Outcome.Err -> fetched
        }
}

class ResolveMatch(private val repository: SourceRepository) {
    suspend operator fun invoke(hostId: Long, matchId: String, sportId: Int): Outcome<PlaybackSpec> =
        repository.resolve(hostId, matchId, sportId)
}

class ProbeSource(private val repository: SourceRepository) {
    suspend operator fun invoke(host: SourceHost): SourceHealth = repository.probe(host)
}
