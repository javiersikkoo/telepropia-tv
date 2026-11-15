package es.telepropia.tv.domain

import es.telepropia.tv.data.model.Failure

object RetryPolicy {

    private val retryable = setOf(Failure.Expired, Failure.NotFound, Failure.Upstream, Failure.Timeout)

    fun shouldRetry(failure: Failure, alreadyRetried: Boolean): Boolean = !alreadyRetried && failure in retryable

    // Con la red cortada en mitad del partido el re-resolve también falla por red,
    // y §8 exige "Sin respuesta del servidor".
    fun visibleFailure(trigger: Failure?, resolveFailure: Failure): Failure =
        if (trigger == Failure.Timeout && (resolveFailure == Failure.NoNetwork || resolveFailure == Failure.Timeout)) {
            Failure.Timeout
        } else {
            resolveFailure
        }
}
