package es.telepropia.tv.data.model

sealed interface Failure {
    data object NoNetwork : Failure
    data object Timeout : Failure
    data object Expired : Failure
    data object NotFound : Failure
    data object Upstream : Failure
    data object NoHost : Failure
    data object Other : Failure
}

sealed interface Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>
    data class Err(val failure: Failure) : Outcome<Nothing>
}
