package fi.kansalaisuuskoe.ui

import androidx.compose.foundation.layout.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fi.kansalaisuuskoe.R
import fi.kansalaisuuskoe.data.ExplanationPreference
import fi.kansalaisuuskoe.data.InterfaceLanguage
import fi.kansalaisuuskoe.data.StudySettings
import fi.kansalaisuuskoe.domain.ContentStatus
import fi.kansalaisuuskoe.domain.StudyLanguage

/**
 * Shown once on first run, in three short steps: the interface language, then how to study (the exam language and
 * one's own support language), then the full disclaimer — the only place outside Settings → About that shows it.
 */
@Composable
fun OnboardingScreen(
    status: ContentStatus, settings: StudySettings, chooseInterface: (InterfaceLanguage) -> Unit,
    chooseStudy: (StudyLanguage) -> Unit, chooseSupport: (ExplanationPreference) -> Unit, start: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = step > 0) { step -= 1 }
    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            // A fresh list per step, so each step opens at its top.
            key(step) { Page {
                when (step) {
                    0 -> {
                        item { Heading(stringResource(R.string.app_title), stringResource(R.string.onboarding_language_title)) }
                        item { Text(stringResource(R.string.onboarding_language_note), style = MaterialTheme.typography.bodyMedium) }
                        item { InterfaceLanguageChoices(settings.interfaceLanguage, chooseInterface) }
                        item { KsButton(stringResource(R.string.onboarding_next), { step = 1 }, Modifier.fillMaxWidth()) }
                    }
                    1 -> {
                        item { Heading(stringResource(R.string.app_title), stringResource(R.string.onboarding_study_title)) }
                        item { ExamLanguageNote() }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.study_language), style = MaterialTheme.typography.titleMedium)
                                StudyLanguageChoices(settings.studyLanguage, chooseStudy)
                            }
                        }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.explanation_language), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.explanation_language_note), style = MaterialTheme.typography.bodyMedium)
                                ExplanationChoices(settings.explanation, chooseSupport)
                            }
                        }
                        item { KsButton(stringResource(R.string.onboarding_next), { step = 2 }, Modifier.fillMaxWidth()) }
                    }
                    else -> {
                        item { Heading(stringResource(R.string.app_title), stringResource(R.string.onboarding_title)) }
                        item { ContentNotice(status) }
                        item { Text(stringResource(R.string.onboarding_about_note), style = MaterialTheme.typography.bodyMedium) }
                        item { KsButton(stringResource(R.string.onboarding_start), start, Modifier.fillMaxWidth()) }
                    }
                }
            } }
        }
    }
}

/** The one fact every learner needs before choosing languages: the real test is in Finnish or Swedish. */
@Composable
private fun ExamLanguageNote() {
    val c = Ks.colors
    Surface(color = c.brandSoft, contentColor = c.ink, shape = MaterialTheme.shapes.medium) {
        Text(stringResource(R.string.onboarding_exam_note), style = KsType.body, modifier = Modifier.fillMaxWidth().padding(16.dp))
    }
}

/** Quiet grey, so the disclaimer never reads as a success state. */
@Composable
internal fun NotOfficialChip() {
    val c = Ks.colors
    Surface(color = c.surface2, contentColor = c.inkMuted, shape = MaterialTheme.shapes.small) {
        Text(stringResource(R.string.not_official_chip), style = KsType.caption, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

/** Compact FI/SV study-language switch for the top app bar: a raised thumb on a `surface-2` track. The full choice stays in Settings. */
@Composable
internal fun StudyLanguageSwitch(selected: StudyLanguage, choose: (StudyLanguage) -> Unit) {
    val c = Ks.colors
    Row(Modifier.clip(CircleShape).background(c.surface2).padding(3.dp).selectableGroup()) {
        StudyLanguage.entries.forEach { language ->
            val description = stringResource(if (language == StudyLanguage.SWEDISH) R.string.question_language_swedish else R.string.question_language)
            val active = selected == language
            val fill by animateColorAsState(if (active) c.surface1 else c.surface2, tween(KsMotion.SELECT), label = "switch")
            val ink by animateColorAsState(if (active) c.brand else c.inkMuted, tween(KsMotion.SELECT), label = "switchText")
            Surface(color = fill, contentColor = ink, shape = CircleShape, shadowElevation = if (active) 1.dp else 0.dp,
                modifier = Modifier.selectable(active, role = Role.RadioButton, onClick = { choose(language) }).semantics { contentDescription = description }) {
                Box(Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 36.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(if (language == StudyLanguage.SWEDISH) R.string.study_switch_sv else R.string.study_switch_fi), style = KsType.caption.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}
