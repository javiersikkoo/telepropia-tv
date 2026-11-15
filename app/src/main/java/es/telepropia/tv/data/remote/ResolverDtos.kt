package es.telepropia.tv.data.remote

import es.telepropia.tv.data.model.Match
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ignoreUnknownKeys: el resolver devuelve campos fuera de §5 y no deben tumbar el parseo.
internal val ResolverJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

@Serializable
internal data class LiveResponseDto(
    val sportType: Int = 0,
    val matches: List<MatchDto> = emptyList()
)

@Serializable
internal data class MatchDto(
    val matchId: String,
    val sportType: Int,
    val name: String
)

@Serializable
internal data class ResolveResponseDto(
    val name: String? = null,
    val matchId: String? = null,
    val sportType: Int? = null,
    val streamUrl: String? = null,
    val referer: String? = null,
    val playableUrl: String
)

internal fun MatchDto.toModel(): Match = Match(matchId = matchId, sportId = sportType, name = name)
