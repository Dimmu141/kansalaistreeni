package fi.kansalaisuuskoe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fi.kansalaisuuskoe.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CitizenshipApplication).container
        setContent {
            val model: AppViewModel = viewModel(factory = viewModelFactory {
                initializer { AppViewModel(container.learning, container.settings) { container.learning.initialize(applicationContext) } }
            })
            val state by model.state.collectAsStateWithLifecycle()
            InterfaceLocale(state.settings.interfaceLanguage) {
                CitizenshipTheme(state.settings.theme) {
                    CompositionLocalProvider(LocalStudyLanguage provides state.settings.studyLanguage) { CitizenshipApp(state, model, container) }
                }
            }
        }
    }
}
