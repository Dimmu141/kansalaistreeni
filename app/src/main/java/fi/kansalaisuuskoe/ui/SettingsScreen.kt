package fi.kansalaisuuskoe.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fi.kansalaisuuskoe.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fi.kansalaisuuskoe.data.ThemePreference
import fi.kansalaisuuskoe.data.InterfaceLanguage
import fi.kansalaisuuskoe.data.ExplanationPreference
import fi.kansalaisuuskoe.domain.StudyLanguage
import fi.kansalaisuuskoe.BuildConfig

@Composable
fun SettingsScreen(state: AppUiState, model: AppViewModel, openBackup: () -> Unit, openMaterial: () -> Unit) {
    // The feedback row appears only when a form URL is configured in strings.xml.
    val feedbackUrl = stringResource(R.string.feedback_url).takeIf { it.startsWith("https://") }
    Page {
        item { Heading(stringResource(R.string.settings_title), stringResource(R.string.settings_subtitle)) }
        state.settingsError?.let { item { Text(stringResource(it), color = MaterialTheme.colorScheme.error) } }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.interface_language), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.interface_language_note), style = MaterialTheme.typography.bodyMedium)
                InterfaceLanguageChoices(state.settings.interfaceLanguage, model::interfaceLanguage)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.explanation_language), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.explanation_language_note), style = MaterialTheme.typography.bodyMedium)
                ExplanationChoices(state.settings.explanation, model::explanation)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.study_language), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.study_language_note), style = MaterialTheme.typography.bodyMedium)
                StudyLanguageChoices(state.settings.studyLanguage, model::studyLanguage)
            }
        }
        item {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleMedium)
                ThemePreference.entries.forEach { theme ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = state.settings.theme == theme, role = Role.RadioButton, onClick = { model.theme(theme) }).padding(horizontal = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = state.settings.theme == theme, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(when (theme) { ThemePreference.SYSTEM -> stringResource(R.string.theme_system); ThemePreference.LIGHT -> stringResource(R.string.theme_light); ThemePreference.DARK -> stringResource(R.string.theme_dark) })
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.easy_finnish_setting), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.easy_finnish_note), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.width(8.dp))
                Switch(checked = state.settings.easyFinnish, onCheckedChange = model::easyFinnish, modifier = Modifier.semanticsLabel(stringResource(R.string.easy_finnish_setting)))
            }
        }
        item { HorizontalDivider() }
        item { FutureSetting(stringResource(R.string.sound_title), stringResource(R.string.sound_pending)) }
        item { HorizontalDivider() }
        item { OutlinedButton(onClick = openMaterial, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.material_title)) } }
        item { OutlinedButton(onClick = openBackup, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.backup_title)) } }
        item { HorizontalDivider() }
        // About: the permanent home of the full disclaimer after onboarding.
        item { Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }) }
        item { ContentNotice(state.contentStatus) }
        feedbackUrl?.let { url ->
            item {
                val uri = LocalUriHandler.current
                OutlinedButton(onClick = { uri.openUri(url) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.feedback_send)) }
            }
        }
        item { Text(stringResource(R.string.version_details, BuildConfig.VERSION_NAME, pluralStringResource(R.plurals.question_count, state.snapshot.questions.size, state.snapshot.questions.size)), style = MaterialTheme.typography.bodySmall) }
    }
}
/** One radio row per choice; shared by Settings and first-run onboarding. */
@Composable
private fun <T> RadioChoices(options: List<T>, selected: T, choose: (T) -> Unit, label: @Composable (T) -> String) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(
                selected = selected == option, role = Role.RadioButton, onClick = { choose(option) },
            ).padding(horizontal = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                RadioButton(selected = selected == option, onClick = null)
                Spacer(Modifier.width(12.dp))
                Text(label(option))
            }
        }
    }
}

@Composable
internal fun InterfaceLanguageChoices(selected: InterfaceLanguage, choose: (InterfaceLanguage) -> Unit) =
    RadioChoices(InterfaceLanguage.entries, selected, choose) { language ->
        stringResource(when (language) { InterfaceLanguage.FINNISH -> R.string.language_finnish; InterfaceLanguage.SWEDISH -> R.string.language_swedish
            InterfaceLanguage.ENGLISH -> R.string.language_english; InterfaceLanguage.RUSSIAN -> R.string.language_russian
            InterfaceLanguage.ARABIC -> R.string.language_arabic
            InterfaceLanguage.PERSIAN -> R.string.language_persian
            InterfaceLanguage.SOMALI -> R.string.language_somali
            InterfaceLanguage.UKRAINIAN -> R.string.language_ukrainian
            InterfaceLanguage.TURKISH -> R.string.language_turkish
            InterfaceLanguage.THAI -> R.string.language_thai })
    }

@Composable
internal fun ExplanationChoices(selected: ExplanationPreference, choose: (ExplanationPreference) -> Unit) =
    RadioChoices(ExplanationPreference.entries, selected, choose) { stringResource(it.optionLabel) }

@Composable
internal fun StudyLanguageChoices(selected: StudyLanguage, choose: (StudyLanguage) -> Unit) =
    RadioChoices(StudyLanguage.entries, selected, choose) {
        stringResource(if (it == StudyLanguage.FINNISH) R.string.study_finnish else R.string.study_swedish)
    }

@Composable
private fun FutureSetting(title: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
