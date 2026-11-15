package es.telepropia.tv.data.remote

import es.telepropia.tv.data.model.Failure
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.UnknownHostException

internal object FailureMapper {

    fun fromStatus(code: Int): Failure = when (code) {
        403, 487 -> Failure.Expired
        404 -> Failure.NotFound
        502 -> Failure.Upstream
        else -> Failure.Other
    }

    // Sin ACCESS_NETWORK_STATE (§2) la falta de red solo se ve en la excepción.
    fun fromThrowable(error: Throwable): Failure = when (error) {
        is UnknownHostException, is NoRouteToHostException -> Failure.NoNetwork
        is ConnectException -> if (error.isUnreachable()) Failure.NoNetwork else Failure.Timeout
        is SocketException -> if (error.isUnreachable()) Failure.NoNetwork else Failure.Other
        is InterruptedIOException -> Failure.Timeout
        else -> Failure.Other
    }

    private fun Throwable.isUnreachable(): Boolean {
        val text = message.orEmpty()
        return text.contains("ENETUNREACH") || text.contains("Network is unreachable", ignoreCase = true)
    }
}
