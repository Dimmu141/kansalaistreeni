package fi.kansalaisuuskoe.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fi.kansalaisuuskoe.R
import fi.kansalaisuuskoe.domain.*

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
            item { ContentNotice(lesson.ideas.contentStatus(), compact = true) }
            item { Text(stringResource(R.string.lesson_intro), style = MaterialTheme.typography.bodyLarge) }
            item { Button(practise, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.lesson_practise)) } }
            item { OutlinedButton(vocabulary, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.lesson_vocabulary)) } }
            itemsIndexed(lesson.ideas, key = { _, question -> question.id }) { index, question ->
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    HorizontalDivider()
                    Text(stringResource(R.string.lesson_idea_position, index + 1, lesson.ideas.size), style = MaterialTheme.typography.labelLarge)
                    QuestionExplanation(question, state.settings.easyFinnish, state.settings.explanation, R.string.lesson_key_idea)
                    SourceReference(question)
                }
            }
        }
    }
}

@Composable
internal fun SourceReference(question: Question) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.source_title), style = MaterialTheme.typography.titleMedium)
        Text(question.source.title, style = MaterialTheme.typography.bodyMedium)
        SelectionContainer { Text(question.source.url, style = MaterialTheme.typography.bodySmall) }
        if (question.status != ContentStatus.OFFICIAL) Text(stringResource(R.string.source_note), style = MaterialTheme.typography.bodySmall)
    }
}
