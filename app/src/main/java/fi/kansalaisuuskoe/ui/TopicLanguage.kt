package fi.kansalaisuuskoe.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import fi.kansalaisuuskoe.domain.StudyLanguage

val LocalStudyLanguage = compositionLocalOf { StudyLanguage.FINNISH }

/**
 * Topic names are study material. Swedish study shows the Swedish names; otherwise they follow the
 * interface language, which is Finnish or English. Pass a language to pin it, e.g. a saved assessment's.
 */
@Composable
internal fun topicLanguage(study: String = LocalStudyLanguage.current.tag): String =
    if (study == StudyLanguage.SWEDISH.tag) study else LocalConfiguration.current.locales[0].language
