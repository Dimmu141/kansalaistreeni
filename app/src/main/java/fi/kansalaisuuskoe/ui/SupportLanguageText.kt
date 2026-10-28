package fi.kansalaisuuskoe.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fi.kansalaisuuskoe.R
import fi.kansalaisuuskoe.data.ExplanationPreference

/** Interface strings that name one support language. Add a row here with each new [ExplanationPreference]. */
private class SupportStrings(
    val option: Int, val title: Int, val missing: Int, val lessonMissing: Int, val meaningTitle: Int, val meaningLine: Int,
)

private val ExplanationPreference.strings get() = when (this) {
    ExplanationPreference.STUDY_LANGUAGE, ExplanationPreference.ALSO_ENGLISH -> SupportStrings(
        if (this == ExplanationPreference.STUDY_LANGUAGE) R.string.explanation_study_only else R.string.explanation_also_english,
        R.string.explanation_english_title, R.string.explanation_missing, R.string.lesson_english_missing,
        R.string.vocabulary_meaning_english, R.string.vocabulary_english)
    ExplanationPreference.ALSO_RUSSIAN -> SupportStrings(R.string.explanation_also_russian, R.string.explanation_russian_title,
        R.string.explanation_missing_russian, R.string.lesson_russian_missing, R.string.vocabulary_meaning_russian, R.string.vocabulary_russian)
    ExplanationPreference.ALSO_ARABIC -> SupportStrings(R.string.explanation_also_arabic, R.string.explanation_arabic_title,
        R.string.explanation_missing_arabic, R.string.lesson_arabic_missing, R.string.vocabulary_meaning_arabic, R.string.vocabulary_arabic)
    ExplanationPreference.ALSO_PERSIAN -> SupportStrings(R.string.explanation_also_persian, R.string.explanation_persian_title,
        R.string.explanation_missing_persian, R.string.lesson_persian_missing, R.string.vocabulary_meaning_persian, R.string.vocabulary_persian)
}

internal val ExplanationPreference.optionLabel get() = strings.option
internal val ExplanationPreference.explanationTitle get() = strings.title
internal val ExplanationPreference.explanationMissing get() = strings.missing
internal val ExplanationPreference.lessonMissing get() = strings.lessonMissing
internal val ExplanationPreference.meaningTitle get() = strings.meaningTitle
/** "Russian: %1$s" for screen readers in the compact word list. */
internal val ExplanationPreference.meaningLine get() = strings.meaningLine

/** Shown under any support translation a person has not yet checked. */
@Composable
internal fun MachineTranslationNote() {
    Text(stringResource(R.string.machine_translation_note), style = KsType.caption, color = Ks.colors.inkMuted)
}
