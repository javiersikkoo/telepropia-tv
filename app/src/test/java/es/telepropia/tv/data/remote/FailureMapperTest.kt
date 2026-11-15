package es.telepropia.tv.data.remote

import es.telepropia.tv.data.model.Failure
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class FailureMapperTest {

    @Test
    fun `codigos http`() {
        assertEquals(Failure.Expired, FailureMapper.fromStatus(403))
        assertEquals(Failure.Expired, FailureMapper.fromStatus(487))
        assertEquals(Failure.NotFound, FailureMapper.fromStatus(404))
        assertEquals(Failure.Upstream, FailureMapper.fromStatus(502))
        assertEquals(Failure.Other, FailureMapper.fromStatus(400))
        assertEquals(Failure.Other, FailureMapper.fromStatus(500))
    }

    @Test
    fun `excepciones de red`() {
        assertEquals(Failure.Timeout, FailureMapper.fromThrowable(SocketTimeoutException()))
        assertEquals(Failure.NoNetwork, FailureMapper.fromThrowable(UnknownHostException()))
        assertEquals(
            Failure.NoNetwork,
            FailureMapper.fromThrowable(ConnectException("connect failed: ENETUNREACH (Network is unreachable)"))
        )
        assertEquals(
            Failure.Timeout,
            FailureMapper.fromThrowable(ConnectException("connect failed: ECONNREFUSED (Connection refused)"))
        )
    }
}
