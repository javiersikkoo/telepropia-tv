package es.telepropia.tv.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HostUrlsTest {

    @Test
    fun `normaliza con y sin esquema y sin barra final`() {
        assertEquals("http://192.168.1.10:3000", HostUrls.normalize("192.168.1.10:3000"))
        assertEquals("http://192.168.1.10:3000", HostUrls.normalize("http://192.168.1.10:3000/"))
        assertEquals("http://192.168.1.10:3000", HostUrls.normalize("  http://192.168.1.10:3000/api/  "))
        assertEquals("http://192.168.1.10:80", HostUrls.normalize("192.168.1.10"))
    }

    @Test
    fun `rechaza vacio y basura`() {
        assertNull(HostUrls.normalize(""))
        assertNull(HostUrls.normalize("   "))
        assertNull(HostUrls.normalize("ftp://192.168.1.10"))
    }

    @Test
    fun `reescribe localhost al host configurado sin tocar la query codificada`() {
        val playable = "http://localhost:3000/api/hls?url=https%3A%2F%2Fcdn.example%2Ftoken-abc%2Findex.m3u8" +
            "&referer=https%3A%2F%2Fplayer.example%2F"
        val expected = "http://192.168.1.10:3000/api/hls?url=https%3A%2F%2Fcdn.example%2Ftoken-abc%2Findex.m3u8" +
            "&referer=https%3A%2F%2Fplayer.example%2F"
        assertEquals(expected, HostUrls.rewritePlayable(playable, "http://192.168.1.10:3000"))
    }

    @Test
    fun `reescribe tambien el puerto`() {
        val rewritten = HostUrls.rewritePlayable("http://localhost:3000/api/hls?url=a%26b", "http://10.0.0.5:8080")
        assertEquals("http://10.0.0.5:8080/api/hls?url=a%26b", rewritten)
    }

    @Test
    fun `endpoint compone ruta y query`() {
        val url = HostUrls.endpoint("http://192.168.1.10:3000", "api/live", listOf("sportType" to "1"))
        assertEquals("http://192.168.1.10:3000/api/live?sportType=1", url.toString())
    }
}
