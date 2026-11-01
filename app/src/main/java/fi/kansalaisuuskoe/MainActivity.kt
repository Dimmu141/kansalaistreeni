package fi.kansalaisuuskoe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fi.kansalaisuuskoe.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CitizenshipApplication).container
        // Realign the reminder from the stored settings, not the UI state, which starts from defaults.
        lifecycleScope.launch {
            container.settings.settings.map { it.reminder to it.reminderMinuteOfDay }.distinctUntilChanged()
                .collect { (enabled, minute) -> ReviewReminder.schedule(applicationContext, enabled, minute) }
        }
        setContent {
            val model: AppViewModel = viewModel(factory = viewModelFactory {
                initializer { AppViewModel(container.learning, container.settings) { container.learning.initialize(applicationContext) } }
            })
            val state by model.state.collectAsStateWithLifecycle()
            // One engine per activity, released with it; switching read-aloud off only hides the buttons.
            val reader = remember { ReadAloud(this) }
            DisposableEffect(reader) { onDispose { reader.shutdown() } }
            reader.slow = state.settings.slowSpeech
            InterfaceLocale(state.settings.interfaceLanguage) {
                CitizenshipTheme(state.settings.theme) {
                    CompositionLocalProvider(LocalStudyLanguage provides state.settings.studyLanguage, LocalSupportLanguage provides state.settings.explanation,
                        LocalReadAloud provides reader.takeIf { state.settings.readAloud }) { CitizenshipApp(state, model, container) }
                }
            }
        }
    }
    override fun onResume() {
        super.onResume()
        ReviewReminder.dismiss(this)
    }
}
