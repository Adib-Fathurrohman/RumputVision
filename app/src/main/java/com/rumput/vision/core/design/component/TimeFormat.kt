package com.rumput.vision.core.design.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.rumput.vision.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val timeWithSeconds = DateTimeFormatter.ofPattern("HH:mm:ss")
private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

fun formatTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    timeFormat.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

fun formatTimeWithSeconds(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    timeWithSeconds.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

fun localDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

/** "Today", "Yesterday", or a localized date. */
@Composable
fun dayLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> stringResource(R.string.day_today)
    today.minusDays(1) -> stringResource(R.string.day_yesterday)
    else -> dateFormat.format(date)
}

/** 35 s, 2 min 5 s, 1 h 3 min. */
fun formatDuration(millis: Long): String {
    val seconds = millis / 1000
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
}

/** Playback clock, e.g. 1:05 or 1:02:03. */
fun formatClock(millis: Long): String {
    val s = (millis / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
