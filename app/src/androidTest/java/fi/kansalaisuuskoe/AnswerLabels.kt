package fi.kansalaisuuskoe

import android.app.Activity
import android.content.res.Configuration
import fi.kansalaisuuskoe.domain.Question
import fi.kansalaisuuskoe.domain.QuestionFormat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Locale

/**
 * The visible answer label, matching the UI: true/false in the interface language, other options as
 * written. The app localises inside Compose from its own setting, so the activity's resources cannot be used.
 */
internal fun Activity.answerLabel(question: Question, optionId: String): String {
    if (question.format != QuestionFormat.TRUE_FALSE) return question.options.first { it.id == optionId }.text
    val app = application as CitizenshipApplication
    val tag = runBlocking { app.container.settings.settings.first().interfaceLanguage.tag }
    val localized = createConfigurationContext(Configuration(resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) })
    return localized.getString(if (optionId == QuestionFormat.TRUE_ID) R.string.true_false_true else R.string.true_false_false)
}
