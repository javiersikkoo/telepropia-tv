package es.telepropia.tv.ui

import androidx.annotation.StringRes
import es.telepropia.tv.R
import es.telepropia.tv.data.model.Failure

@StringRes
fun Failure.messageRes(@StringRes otherRes: Int = R.string.error_other): Int = when (this) {
    Failure.NoNetwork -> R.string.error_no_network
    Failure.Timeout -> R.string.error_timeout
    Failure.Expired -> R.string.error_expired
    Failure.NotFound -> R.string.error_not_found
    Failure.Upstream -> R.string.error_upstream
    Failure.NoHost -> R.string.error_no_host
    Failure.Other -> otherRes
}
