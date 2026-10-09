package fi.kansalaisuuskoe.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import fi.kansalaisuuskoe.R

/** A review time in relative words: now, later today, tomorrow or in N days. */
@Composable
fun formatReviewTime(time: Long, now: Long = System.currentTimeMillis()): String =
    when (val days = reviewDays(time, now, ZoneId.systemDefault())) {
        null -> stringResource(R.string.review_now)
        0 -> stringResource(R.string.review_later_today)
        1 -> stringResource(R.string.review_tomorrow)
        else -> pluralStringResource(R.plurals.review_in_days, days, days)
    }

/** Calendar days from [now] until [time] in [zone], or null when the review is already due. */
internal fun reviewDays(time: Long, now: Long, zone: ZoneId): Int? =
    if (time <= now) null
    else ChronoUnit.DAYS.between(Instant.ofEpochMilli(now).atZone(zone).toLocalDate(), Instant.ofEpochMilli(time).atZone(zone).toLocalDate()).toInt()

/** An exact date and time, for events that already happened, such as a finished assessment. */
@Composable
fun formatDateTime(time: Long): String {
    val locale: Locale = LocalConfiguration.current.locales[0]
    val formatter = DateTimeFormatter.ofPattern(stringResource(R.string.date_time_pattern), locale)
    return Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(formatter)
}

/** Formats an ISO calendar date from content metadata, such as a source check date. */
@Composable
fun formatDate(isoDate: String): String =
    LocalDate.parse(isoDate).format(DateTimeFormatter.ofPattern(stringResource(R.string.date_pattern), LocalConfiguration.current.locales[0]))
