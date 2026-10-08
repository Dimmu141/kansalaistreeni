package fi.kansalaisuuskoe.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fi.kansalaisuuskoe.R
import fi.kansalaisuuskoe.data.*
import fi.kansalaisuuskoe.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AppUiState(
    val loading: Boolean = true,
    val error: Int? = null,
    val snapshot: LearningSnapshot = LearningSnapshot(),
    val settings: StudySettings = StudySettings(),
    val now: Long = System.currentTimeMillis(),
    val settingsError: Int? = null,
) {
    val studySnapshot get() = snapshot.forLanguage(settings.studyLanguage)
    val summary get() = LearningEngine.summarize(studySnapshot, now)
    val contentStatus get() = snapshot.questions.contentStatus()
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(
    private val learning: LearningRepository,
    private val preferences: SettingsRepository,
    private val initializeContent: suspend () -> Unit,
) : ViewModel() {
    private val load = MutableStateFlow(true)
    private val error = MutableStateFlow<Int?>(null)
    private val settingsError = MutableStateFlow<Int?>(null)
    private val reload = MutableStateFlow(0)
    private val ticks = flow { while (true) { emit(System.currentTimeMillis()); delay(30_000) } }
    private val content = reload.flatMapLatest {
        combine(learning.snapshot, preferences.settings, ticks) { snapshot, settings, now ->
            AppUiState(loading = false, snapshot = snapshot, settings = settings, now = now)
        }.catch { e ->
            if (e is CancellationException) throw e
            Diagnostics.error("Reading learning data", e)
            error.value = R.string.error_read_data
            emit(AppUiState())
        }
    }
    val state = combine(content, load, error, settingsError) { data, loading, failure, settingsFailure ->
        data.copy(loading = loading, error = failure, settingsError = settingsFailure)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState())

    init { initialize() }
    fun initialize() {
        viewModelScope.launch {
            load.value = true
            error.value = null
            reload.value += 1
            try { initializeContent() }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                Diagnostics.error("Loading study material", e)
                error.value = R.string.error_load_content
            }
            finally { load.value = false }
        }
    }
    fun easyFinnish(value: Boolean) = save { preferences.setEasyFinnish(value) }
    fun theme(value: ThemePreference) = save { preferences.setTheme(value) }
    fun interfaceLanguage(value: InterfaceLanguage) = save { preferences.setInterfaceLanguage(value) }
    fun studyLanguage(value: StudyLanguage) = save { preferences.setStudyLanguage(value) }
    fun explanation(value: ExplanationPreference) = save { preferences.setExplanation(value) }
    private fun save(action: suspend () -> Unit) {
        viewModelScope.launch {
            try { action(); settingsError.value = null }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                Diagnostics.error("Saving a setting", e)
                settingsError.value = R.string.error_save_setting
            }
        }
    }
}
