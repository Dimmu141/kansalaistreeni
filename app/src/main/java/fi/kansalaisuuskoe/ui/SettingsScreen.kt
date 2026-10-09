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
    Page {
        item { Heading(stringResource(R.string.settings_title), stringResource(R.string.settings_subtitle)) }
        state.settingsError?.let { item { Text(stringResource(it), color = MaterialTheme.colorScheme.error) } }
        item {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.interface_language), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.interface_language_note), style = MaterialTheme.typography.bodyMedium)
                InterfaceLanguage.entries.forEach { language ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(
                        selected = state.settings.interfaceLanguage == language, role = Role.RadioButton,
                        onClick = { model.interfaceLanguage(language) },
                    ).padding(horizontal = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = state.settings.interfaceLanguage == language, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(if (language == InterfaceLanguage.FINNISH) R.string.language_finnish else R.string.language_english))
                    }
                }
            }
        }
        item {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.explanation_language), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.explanation_language_note), style = MaterialTheme.typography.bodyMedium)
                ExplanationPreference.entries.forEach { preference ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(
                        selected = state.settings.explanation == preference, role = Role.RadioButton,
                        onClick = { model.explanation(preference) },
                    ).padding(horizontal = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = state.settings.explanation == preference, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(if (preference == ExplanationPreference.STUDY_LANGUAGE) R.string.explanation_study_only else R.string.explanation_also_english))
                    }
                }
            }
        }
        item {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.study_language), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.study_language_note), style = MaterialTheme.typography.bodyMedium)
                StudyLanguage.entries.forEach { language ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(
                        selected = state.settings.studyLanguage == language, role = Role.RadioButton,
                        onClick = { model.studyLanguage(language) },
                    ).padding(horizontal = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = state.settings.studyLanguage == language, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(if (language == StudyLanguage.FINNISH) R.string.study_finnish else R.string.study_swedish))
                    }
                }
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
        item { Text(stringResource(R.string.version_details, BuildConfig.VERSION_NAME, pluralStringResource(R.plurals.question_count, state.snapshot.questions.size, state.snapshot.questions.size)), style = MaterialTheme.typography.bodySmall) }
    }
}
@Composable
private fun FutureSetting(title: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
