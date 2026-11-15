package es.telepropia.tv.domain

import es.telepropia.tv.data.model.Failure
import es.telepropia.tv.data.model.Match
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventMergerTest {

    @Test
    fun `normaliza tildes, conectores y separadores`() {
        assertEquals("atletico madrid sevilla", EventMerger.normalize("Atlético Madrid vs Sevilla"))
        assertEquals("atletico madrid sevilla", EventMerger.normalize("ATLETICO MADRID - SEVILLA"))
        assertEquals("atletico madrid sevilla", EventMerger.normalize("Atletico  Madrid v. Sevilla"))
    }

    @Test
    fun `mismo nombre en deportes distintos no se fusiona`() {
        val football = Match("1", 1, "Spain vs Italy", 1, "a")
        val basket = Match("2", 2, "Spain vs Italy", 2, "b")
        assertNotEquals(EventMerger.key(football), EventMerger.key(basket))
    }

    @Test
    fun `reintento una sola vez para caducado, 404, 502 y timeout`() {
        listOf(Failure.Expired, Failure.NotFound, Failure.Upstream, Failure.Timeout).forEach {
            assertTrue(RetryPolicy.shouldRetry(it, alreadyRetried = false))
            assertFalse(RetryPolicy.shouldRetry(it, alreadyRetried = true))
        }
        assertFalse(RetryPolicy.shouldRetry(Failure.Other, alreadyRetried = false))
        assertFalse(RetryPolicy.shouldRetry(Failure.NoNetwork, alreadyRetried = false))
    }

    @Test
    fun `red cortada en reproduccion se pinta como sin respuesta del servidor`() {
        assertEquals(Failure.Timeout, RetryPolicy.visibleFailure(Failure.Timeout, Failure.NoNetwork))
        assertEquals(Failure.Upstream, RetryPolicy.visibleFailure(Failure.Expired, Failure.Upstream))
        assertEquals(Failure.NoNetwork, RetryPolicy.visibleFailure(null, Failure.NoNetwork))
    }
}
