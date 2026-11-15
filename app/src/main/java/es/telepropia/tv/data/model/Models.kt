package es.telepropia.tv.data.model

data class Sport(val id: Int, val label: String)

data class Match(val matchId: String, val sportId: Int, val name: String)

data class PlaybackSpec(val url: String, val referer: String?)

data class SourceHost(val id: Long, val baseUrl: String, val isPrimary: Boolean)

data class SourceHealth(val ok: Boolean, val latencyMs: Long, val failure: Failure?)

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
