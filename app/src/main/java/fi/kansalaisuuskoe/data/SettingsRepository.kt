package fi.kansalaisuuskoe.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import fi.kansalaisuuskoe.domain.StudyLanguage

private val Context.settingsStore by preferencesDataStore("settings")
enum class ThemePreference { SYSTEM, LIGHT, DARK }
enum class InterfaceLanguage(val tag: String) {
    FINNISH("fi"), SWEDISH("sv"), ENGLISH("en");
    companion object {
        /** Without an explicit choice, Swedish study gets a Swedish interface; otherwise Finnish. */
        fun defaultFor(study: StudyLanguage) = if (study == StudyLanguage.SWEDISH) SWEDISH else FINNISH
    }
}
enum class ExplanationPreference { STUDY_LANGUAGE, ALSO_ENGLISH }
data class StudySettings(val easyFinnish: Boolean = true, val theme: ThemePreference = ThemePreference.SYSTEM, val interfaceLanguage: InterfaceLanguage = InterfaceLanguage.FINNISH, val studyLanguage: StudyLanguage = StudyLanguage.FINNISH, val explanation: ExplanationPreference = ExplanationPreference.STUDY_LANGUAGE, val onboardingSeen: Boolean = true)
class SettingsRepository(context: Context) {
    private val store = context.settingsStore
    private val easyKey = booleanPreferencesKey("easy_finnish")
    private val themeKey = stringPreferencesKey("theme")
    private val languageKey = stringPreferencesKey("interface_language")
    private val studyLanguageKey = stringPreferencesKey("study_language")
    private val explanationKey = stringPreferencesKey("explanation")
    private val onboardingKey = booleanPreferencesKey("onboarding_seen")
    val settings = store.data.catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }.map { prefs ->
        val study = StudyLanguage.entries.firstOrNull { it.tag == prefs[studyLanguageKey] } ?: StudyLanguage.FINNISH
        StudySettings(prefs[easyKey] ?: true, ThemePreference.entries.firstOrNull { it.name == prefs[themeKey] } ?: ThemePreference.SYSTEM,
            InterfaceLanguage.entries.firstOrNull { it.tag == prefs[languageKey] } ?: InterfaceLanguage.defaultFor(study),
            study,
            ExplanationPreference.entries.firstOrNull { it.name == prefs[explanationKey] } ?: ExplanationPreference.STUDY_LANGUAGE,
            prefs[onboardingKey] ?: false)
    }
    suspend fun setEasyFinnish(value: Boolean) { store.edit { it[easyKey] = value } }
    suspend fun setTheme(value: ThemePreference) { store.edit { it[themeKey] = value.name } }
    suspend fun setInterfaceLanguage(value: InterfaceLanguage) { store.edit { it[languageKey] = value.tag } }
    suspend fun setStudyLanguage(value: StudyLanguage) { store.edit { it[studyLanguageKey] = value.tag } }
    suspend fun setExplanation(value: ExplanationPreference) { store.edit { it[explanationKey] = value.name } }
    suspend fun setOnboardingSeen(value: Boolean) { store.edit { it[onboardingKey] = value } }
}
