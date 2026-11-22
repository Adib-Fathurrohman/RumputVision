package com.rumput.vision.core.design.component

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.rumput.vision.R
import com.rumput.vision.domain.model.FrigateError
import com.rumput.vision.domain.model.NetworkFailure

@Composable
fun frigateErrorMessage(error: FrigateError): String = when (error) {
    FrigateError.NotConfigured -> stringResource(R.string.error_not_configured)
    FrigateError.Unauthorized -> stringResource(R.string.error_unauthorized)
    FrigateError.Forbidden -> stringResource(R.string.error_forbidden)
    FrigateError.RateLimited -> stringResource(R.string.error_rate_limited)
    FrigateError.InvalidResponse -> stringResource(R.string.error_invalid_response)
    is FrigateError.Http -> stringResource(R.string.error_http, error.code)
    is FrigateError.Unreachable -> stringResource(
        when (error.failure) {
            NetworkFailure.NO_NETWORK -> R.string.error_no_network
            NetworkFailure.HOST_NOT_FOUND -> R.string.error_host_not_found
            NetworkFailure.TIMEOUT -> R.string.error_timeout
            NetworkFailure.CONNECTION_REFUSED -> R.string.error_refused
            NetworkFailure.TLS -> R.string.error_tls
            NetworkFailure.INSECURE_TRANSPORT -> R.string.error_insecure_transport
            NetworkFailure.OTHER -> R.string.error_network_other
        },
    )
}

/** "5 min. ago" style text, or "just now" within the last minute. */
@Composable
fun relativeTime(epochMillis: Long, now: Long = System.currentTimeMillis()): String =
    if (now - epochMillis < DateUtils.MINUTE_IN_MILLIS) {
        stringResource(R.string.time_just_now)
    } else {
        DateUtils.getRelativeTimeSpanString(epochMillis, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
    }

fun formatUptime(seconds: Long): String {
    val days = seconds / 86_400
    val hours = (seconds % 86_400) / 3_600
    val minutes = (seconds % 3_600) / 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

/** Frigate reports storage in MB. */
fun formatStorageMb(mb: Double): String = if (mb >= 1024) "%.1f GB".format(mb / 1024) else "%.0f MB".format(mb)
