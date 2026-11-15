package es.telepropia.tv.data.model

data class Sport(val id: Int, val label: String)

data class Match(
    val matchId: String,
    val sportId: Int,
    val name: String,
    val sourceHostId: Long,
    val sourceName: String
)

data class PlaybackSpec(val url: String, val referer: String?)

enum class HostState { ACTIVE, PAUSED }

data class SourceHost(
    val id: Long,
    val baseUrl: String,
    val label: String? = null,
    val state: HostState = HostState.ACTIVE
)

data class SourceHealth(val ok: Boolean, val latencyMs: Long, val failure: Failure?)

/** `failure` no nulo: el host está pausado en memoria por haber fallado. */
data class HostStatus(val host: SourceHost, val failure: Failure?)

data class HostFetch(
    val activeHosts: List<SourceHost>,
    val live: List<Match>,
    val stale: List<Match>,
    val failingHosts: List<SourceHost>
)

data class EventSource(val match: Match, val live: Boolean)

data class LiveEvent(
    val key: String,
    val name: String,
    val sportId: Int,
    val sources: List<EventSource>
) {
    val liveSources: List<Match> get() = sources.filter { it.live }.map { it.match }
    val hasLiveSource: Boolean get() = sources.any { it.live }
}

data class LiveSnapshot(
    val events: List<LiveEvent>,
    val failingHosts: List<SourceHost>,
    val activeHostCount: Int
) {
    val degraded: Boolean get() = failingHosts.isNotEmpty()
}

object Sports {
    const val DEFAULT_ID = 1
    const val PROBE_ID = 1

    val all: List<Sport> = listOf(
        Sport(1, "Fútbol"),
        Sport(2, "Baloncesto"),
        Sport(3, "Tenis"),
        Sport(4, "Béisbol"),
        Sport(6, "Críquet"),
        Sport(7, "Motor"),
        Sport(8, "Rugby"),
        Sport(9, "Fútbol americano"),
        Sport(11, "Hockey"),
        Sport(90, "Otros")
    )
}
