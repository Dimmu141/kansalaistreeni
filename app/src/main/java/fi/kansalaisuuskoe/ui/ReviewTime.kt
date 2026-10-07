package fi.kansalaisuuskoe.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import fi.kansalaisuuskoe.R

@Composable
fun formatReviewTime(time: Long): String {
    val locale: Locale = LocalConfiguration.current.locales[0]
    val formatter = DateTimeFormatter.ofPattern(stringResource(R.string.date_time_pattern), locale)
    return Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(formatter)
}
