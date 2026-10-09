package fi.kansalaisuuskoe.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fi.kansalaisuuskoe.R
import fi.kansalaisuuskoe.domain.ContentStatus
import fi.kansalaisuuskoe.domain.StudyLanguage

/** Shown once on first run: the only place outside Settings → About with the full disclaimer. */
@Composable
fun OnboardingScreen(status: ContentStatus, start: () -> Unit) {
    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            Page {
                item { Heading(stringResource(R.string.app_title), stringResource(R.string.onboarding_title)) }
                item { ContentNotice(status) }
                item { Text(stringResource(R.string.onboarding_about_note), style = MaterialTheme.typography.bodyMedium) }
                item { Button(start, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.onboarding_start)) } }
            }
        }
    }
}

@Composable
internal fun NotOfficialChip() {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp)) {
        Text(stringResource(R.string.not_official_chip), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

/** Compact FI/SV study-language switch for the top app bar; the full choice stays in Settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StudyLanguageSwitch(selected: StudyLanguage, choose: (StudyLanguage) -> Unit) {
    SingleChoiceSegmentedButtonRow {
        StudyLanguage.entries.forEachIndexed { index, language ->
            val description = stringResource(if (language == StudyLanguage.SWEDISH) R.string.question_language_swedish else R.string.question_language)
            SegmentedButton(selected = selected == language, onClick = { choose(language) },
                shape = SegmentedButtonDefaults.itemShape(index, StudyLanguage.entries.size), icon = {},
                modifier = Modifier.semantics { contentDescription = description }) {
                Text(stringResource(if (language == StudyLanguage.SWEDISH) R.string.study_switch_sv else R.string.study_switch_fi))
            }
        }
    }
}
