package fi.kansalaisuuskoe.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import fi.kansalaisuuskoe.domain.StudyLanguage
import fi.kansalaisuuskoe.domain.StudyReminder

private val Context.settingsStore by preferencesDataStore("settings")
enum class ThemePreference { SYSTEM, LIGHT, DARK }
enum class InterfaceLanguage(val tag: String) {
    FINNISH("fi"), SWEDISH("sv"), ENGLISH("en"), RUSSIAN("ru"), ARABIC("ar"), PERSIAN("fa"), SOMALI("so"), UKRAINIAN("uk"), TURKISH("tr"), THAI("th");
    companion object {
        /** Without an explicit choice, Swedish study gets a Swedish interface; otherwise Finnish. */
        fun defaultFor(study: StudyLanguage) = if (study == StudyLanguage.SWEDISH) SWEDISH else FINNISH
    }
}
/**
 * Optional support language shown beside the study language: translated question, explanation, lesson
 * and word meanings. Questions are always practised in Finnish or Swedish, as in the exam. Stored by
 * name, so a new language is a new entry plus its strings in SupportLanguageText.
 */
enum class ExplanationPreference(val tag: String?) {
    STUDY_LANGUAGE(null), ALSO_ENGLISH("en"), ALSO_RUSSIAN("ru"), ALSO_ARABIC("ar"), ALSO_PERSIAN("fa"), ALSO_SOMALI("so"), ALSO_UKRAINIAN("uk"), ALSO_TURKISH("tr"), ALSO_THAI("th");
    companion object {
        /** First-run default: support in the chosen interface language; Finnish and Swedish need none. */
        fun matching(language: InterfaceLanguage) = entries.firstOrNull { it.tag == language.tag } ?: STUDY_LANGUAGE
    }
}
data class StudySettings(val easyFinnish: Boolean = true, val theme: ThemePreference = ThemePreference.SYSTEM, val interfaceLanguage: InterfaceLanguage = InterfaceLanguage.FINNISH, val studyLanguage: StudyLanguage = StudyLanguage.FINNISH, val explanation: ExplanationPreference = ExplanationPreference.STUDY_LANGUAGE, val onboardingSeen: Boolean = true,
    val reminder: Boolean = false, val reminderMinuteOfDay: Int = StudyReminder.DEFAULT_MINUTE_OF_DAY,
    val readAloud: Boolean = true, val slowSpeech: Boolean = false)
class SettingsRepository(context: Context) {
    private val store = context.settingsStore
    private val easyKey = booleanPreferencesKey("easy_finnish")
    private val themeKey = stringPreferencesKey("theme")
    private val languageKey = stringPreferencesKey("interface_language")
    private val studyLanguageKey = stringPreferencesKey("study_language")
    private val explanationKey = stringPreferencesKey("explanation")
    private val onboardingKey = booleanPreferencesKey("onboarding_seen")
    private val reminderKey = booleanPreferencesKey("reminder")
    private val reminderTimeKey = intPreferencesKey("reminder_minute_of_day")
    private val readAloudKey = booleanPreferencesKey("read_aloud")
    private val slowSpeechKey = booleanPreferencesKey("slow_speech")
    val settings = store.data.catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }.map { prefs ->
        val study = StudyLanguage.entries.firstOrNull { it.tag == prefs[studyLanguageKey] } ?: StudyLanguage.FINNISH
        StudySettings(prefs[easyKey] ?: true, ThemePreference.entries.firstOrNull { it.name == prefs[themeKey] } ?: ThemePreference.SYSTEM,
            InterfaceLanguage.entries.firstOrNull { it.tag == prefs[languageKey] } ?: InterfaceLanguage.defaultFor(study),
            study,
            ExplanationPreference.entries.firstOrNull { it.name == prefs[explanationKey] } ?: ExplanationPreference.STUDY_LANGUAGE,
            prefs[onboardingKey] ?: false,
            prefs[reminderKey] ?: false,
            prefs[reminderTimeKey]?.takeIf { it in 0 until 24 * 60 } ?: StudyReminder.DEFAULT_MINUTE_OF_DAY,
            prefs[readAloudKey] ?: true,
            prefs[slowSpeechKey] ?: false)
    }
    suspend fun setEasyFinnish(value: Boolean) { store.edit { it[easyKey] = value } }
    suspend fun setTheme(value: ThemePreference) { store.edit { it[themeKey] = value.name } }
    suspend fun setInterfaceLanguage(value: InterfaceLanguage) { store.edit { it[languageKey] = value.tag } }
    suspend fun setStudyLanguage(value: StudyLanguage) { store.edit { it[studyLanguageKey] = value.tag } }
    suspend fun setExplanation(value: ExplanationPreference) { store.edit { it[explanationKey] = value.name } }
    suspend fun setOnboardingSeen(value: Boolean) { store.edit { it[onboardingKey] = value } }
    suspend fun setReminder(value: Boolean) { store.edit { it[reminderKey] = value } }
    suspend fun setReminderTime(minuteOfDay: Int) { store.edit { it[reminderTimeKey] = minuteOfDay } }
    suspend fun setReadAloud(value: Boolean) { store.edit { it[readAloudKey] = value } }
    suspend fun setSlowSpeech(value: Boolean) { store.edit { it[slowSpeechKey] = value } }
}
