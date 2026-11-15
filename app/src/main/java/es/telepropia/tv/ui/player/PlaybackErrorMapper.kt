package es.telepropia.tv.ui.player

import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import es.telepropia.tv.data.model.Failure

@OptIn(UnstableApi::class)
internal object PlaybackErrorMapper {

    // El código HTTP manda; el texto del error cambia entre versiones de Media3.
    fun map(error: PlaybackException): Failure {
        if (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) {
            when (httpStatus(error)) {
                403, 487 -> return Failure.Expired
                404 -> return Failure.NotFound
                502 -> return Failure.Upstream
            }
        }
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_TIMEOUT -> Failure.Timeout
            else -> Failure.Other
        }
    }

    private fun httpStatus(error: PlaybackException): Int? {
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) return cause.responseCode
            cause = cause.cause
        }
        return null
    }
}
