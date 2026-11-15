package es.telepropia.tv.data.remote

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FixturesTest {

    private fun fixture(name: String): String {
        val candidates = listOf(File("../fixtures/$name"), File("fixtures/$name"))
        return candidates.first { it.exists() }.readText()
    }

    @Test
    fun `live json se parsea a partidos`() {
        val dto = ResolverJson.decodeFromString(LiveResponseDto.serializer(), fixture("live.json"))
        val matches = dto.matches.map { it.toModel() }
        assertEquals(1, matches.size)
        assertEquals("4331876", matches[0].matchId)
        assertEquals(1, matches[0].sportId)
        assertEquals("Team A vs Team B", matches[0].name)
    }

    @Test
    fun `resolve json se parsea y su playableUrl se reescribe`() {
        val dto = ResolverJson.decodeFromString(ResolveResponseDto.serializer(), fixture("resolve.json"))
        val rewritten = HostUrls.rewritePlayable(dto.playableUrl, "http://192.168.1.10:3000").orEmpty()
        assertTrue(rewritten.startsWith("http://192.168.1.10:3000/api/hls?url=https%3A%2F%2Fcdn.example"))
        assertTrue(rewritten.endsWith("&referer=https%3A%2F%2Fplayer.example%2F"))
    }

    @Test
    fun `campos desconocidos no rompen el parseo`() {
        val body = """{ "sportType": 1, "extra": true, "matches": [ """ +
            """{ "matchId": "1", "sportType": 1, "name": "X", "nuevo": 5 } ] }"""
        val dto = ResolverJson.decodeFromString(LiveResponseDto.serializer(), body)
        assertEquals(1, dto.matches.size)
    }

    @Test
    fun `lista vacia no es fallo`() {
        val dto = ResolverJson.decodeFromString(LiveResponseDto.serializer(), """{ "sportType": 3, "matches": [] }""")
        assertTrue(dto.matches.isEmpty())
    }
}
