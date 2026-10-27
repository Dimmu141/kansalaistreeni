package fi.kansalaisuuskoe.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fi.kansalaisuuskoe.R
import fi.kansalaisuuskoe.data.ExplanationPreference

/** Interface strings that name a support language. Add a branch here with each new [ExplanationPreference]. */
internal val ExplanationPreference.optionLabel get() = when (this) {
    ExplanationPreference.STUDY_LANGUAGE -> R.string.explanation_study_only
    ExplanationPreference.ALSO_ENGLISH -> R.string.explanation_also_english
    ExplanationPreference.ALSO_RUSSIAN -> R.string.explanation_also_russian
}
internal val ExplanationPreference.explanationTitle get() =
    if (this == ExplanationPreference.ALSO_RUSSIAN) R.string.explanation_russian_title else R.string.explanation_english_title
internal val ExplanationPreference.explanationMissing get() =
    if (this == ExplanationPreference.ALSO_RUSSIAN) R.string.explanation_missing_russian else R.string.explanation_missing
internal val ExplanationPreference.lessonMissing get() =
    if (this == ExplanationPreference.ALSO_RUSSIAN) R.string.lesson_russian_missing else R.string.lesson_english_missing
internal val ExplanationPreference.meaningTitle get() =
    if (this == ExplanationPreference.ALSO_RUSSIAN) R.string.vocabulary_meaning_russian else R.string.vocabulary_meaning_english
/** "Russian: %1$s" for screen readers in the compact word list. */
internal val ExplanationPreference.meaningLine get() =
    if (this == ExplanationPreference.ALSO_RUSSIAN) R.string.vocabulary_russian else R.string.vocabulary_english

/** Shown under any support translation a person has not yet checked. */
@Composable
internal fun MachineTranslationNote() {
    Text(stringResource(R.string.machine_translation_note), style = KsType.caption, color = Ks.colors.inkMuted)
}
