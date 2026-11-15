package es.telepropia.tv.domain

import es.telepropia.tv.data.SourceRepository
import es.telepropia.tv.data.model.Match
import es.telepropia.tv.data.model.Outcome
import es.telepropia.tv.data.model.PlaybackSpec
import es.telepropia.tv.data.model.SourceHealth
import es.telepropia.tv.data.model.SourceHost

class ListLiveMatches(private val repository: SourceRepository) {
    suspend operator fun invoke(sportId: Int): Outcome<List<Match>> = repository.listLive(sportId)
}

class ResolveMatch(private val repository: SourceRepository) {
    suspend operator fun invoke(matchId: String, sportId: Int): Outcome<PlaybackSpec> =
        repository.resolve(matchId, sportId)
}

class ProbeSource(private val repository: SourceRepository) {
    suspend operator fun invoke(host: SourceHost): SourceHealth = repository.probe(host)
}
