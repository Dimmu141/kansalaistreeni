package fi.kansalaisuuskoe.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fi.kansalaisuuskoe.R
import fi.kansalaisuuskoe.domain.*
import fi.kansalaisuuskoe.data.ExplanationPreference

@Composable
internal fun TopicLessonScreen(state: AppUiState, topicId: String, practise: () -> Unit, vocabulary: () -> Unit) {
    val lesson = LearningContent.lesson(state.snapshot, topicId, state.settings.studyLanguage)
    val locale = LocalConfiguration.current.locales[0].language
    Page {
        if (lesson == null) {
            item { Heading(stringResource(R.string.lesson_unavailable), stringResource(R.string.language_empty_body)) }
        } else {
            item { Heading(lesson.topic.titleFor(locale), lesson.topic.descriptionFor(locale)) }
            item { StudyScope(state) }
            item { ContentNotice(lesson.contentStatus(), compact = true) }
            item {
                lesson.authored?.let { StudyText(it.introduction, state.settings.studyLanguage.tag, MaterialTheme.typography.bodyLarge) }
                    ?: Text(stringResource(R.string.lesson_intro), style = MaterialTheme.typography.bodyLarge)
            }
            item { Button(practise, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.lesson_practise)) } }
            item { OutlinedButton(vocabulary, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.lesson_vocabulary)) } }
            if (lesson.authored != null) itemsIndexed(lesson.authored.sections, key = { _, section -> "section:${section.id}" }) { index, section ->
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    HorizontalDivider()
                    Text(stringResource(R.string.lesson_idea_position, index + 1, lesson.authored.sections.size), style = MaterialTheme.typography.labelLarge)
                    AuthoredLessonSection(section, lesson.english?.sections?.find { it.id == section.id }, state.settings.studyLanguage.tag,
                        state.settings.easyFinnish, state.settings.explanation)
                    section.sources.forEach { SourceReference(it, lesson.authored.status) }
                }
            } else itemsIndexed(lesson.ideas, key = { _, question -> question.id }) { index, question ->
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    HorizontalDivider()
                    Text(stringResource(R.string.lesson_idea_position, index + 1, lesson.ideas.size), style = MaterialTheme.typography.labelLarge)
                    QuestionExplanation(question, state.settings.easyFinnish, state.settings.explanation, R.string.lesson_key_idea)
                    SourceReference(question)
                }
            }
            if (lesson.authored != null) item {
                Button(practise, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.lesson_practise)) }
            }
        }
    }
}

@Composable
internal fun SourceReference(question: Question) {
    SourceReference(question.source, question.status)
}

@Composable
internal fun SourceReference(source: Source, status: ContentStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.source_title), style = MaterialTheme.typography.titleMedium)
        Text(source.title, style = MaterialTheme.typography.bodyMedium)
        SelectionContainer { Text(source.url, style = MaterialTheme.typography.bodySmall) }
        if (status != ContentStatus.OFFICIAL) Text(stringResource(R.string.source_note), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun AuthoredLessonSection(section: LessonSection, english: LessonSection?, language: String, easyFinnish: Boolean, preference: ExplanationPreference) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StudyText(section.title, language, MaterialTheme.typography.titleLarge, Modifier.semantics { heading() })
        StudyText(section.body, language, MaterialTheme.typography.bodyLarge)
        if (language == "fi" && easyFinnish && section.easyBody != null) {
            Text(stringResource(R.string.easy_finnish_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            StudyText(section.easyBody, "fi", MaterialTheme.typography.bodyLarge)
        }
        if (preference == ExplanationPreference.ALSO_ENGLISH) {
            Text(stringResource(R.string.explanation_english_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            english?.let { StudyText(it.body, "en", MaterialTheme.typography.bodyLarge) }
                ?: Text(stringResource(R.string.lesson_english_missing), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
