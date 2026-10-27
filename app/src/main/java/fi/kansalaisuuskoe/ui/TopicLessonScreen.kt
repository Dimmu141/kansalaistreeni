package fi.kansalaisuuskoe.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
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
    val locale = topicLanguage()
    Page {
        if (lesson == null) {
            item { Heading(stringResource(R.string.lesson_unavailable), stringResource(R.string.language_empty_body)) }
        } else {
            item { Heading(lesson.topic.titleFor(locale), lesson.topic.descriptionFor(locale)) }
            item {
                lesson.authored?.let { StudyText(it.introduction, state.settings.studyLanguage.tag, KsType.bodyLarge) }
                    ?: Text(stringResource(R.string.lesson_intro), style = MaterialTheme.typography.bodyLarge)
            }
            item { KsButton(stringResource(R.string.lesson_practise), practise, Modifier.fillMaxWidth()) }
            item { OutlinedButton(vocabulary, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.lesson_vocabulary)) } }
            if (lesson.authored != null) itemsIndexed(lesson.authored.sections, key = { _, section -> "section:${section.id}" }) { index, section ->
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    HorizontalDivider(color = Ks.colors.line)
                    Text(stringResource(R.string.lesson_idea_position, index + 1, lesson.authored.sections.size), style = KsType.caption.copy(fontFeatureSettings = "tnum"), color = Ks.colors.brand)
                    val support = state.settings.explanation.tag?.let { lesson.topic.lessons[it] }
                    AuthoredLessonSection(section, support?.sections?.find { it.id == section.id }, state.settings.studyLanguage.tag,
                        state.settings.easyFinnish, state.settings.explanation, support?.reviewed == true)
                    section.sources.forEach { SourceReference(it) }
                }
            } else itemsIndexed(lesson.ideas, key = { _, question -> question.id }) { index, question ->
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    HorizontalDivider(color = Ks.colors.line)
                    Text(stringResource(R.string.lesson_idea_position, index + 1, lesson.ideas.size), style = KsType.caption.copy(fontFeatureSettings = "tnum"), color = Ks.colors.brand)
                    QuestionExplanation(question, state.settings.easyFinnish, state.settings.explanation, R.string.lesson_key_idea)
                    SourceReference(question)
                }
            }
            if (lesson.authored != null) item {
                KsButton(stringResource(R.string.lesson_practise), practise, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
internal fun SourceReference(question: Question) {
    SourceReference(question.source, question.reviewOverdue(java.time.LocalDate.now()))
}

/** One source line for questions and lessons: title, selectable link, editorial check date and an overdue warning. */
@Composable
internal fun SourceReference(source: Source, reviewOverdue: Boolean = false) {
    val c = Ks.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(KsIcons.Document, null, Modifier.padding(top = 1.dp).size(16.dp), tint = c.inkMuted)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.source_title), style = KsType.overline, color = c.inkMuted, modifier = Modifier.semantics { heading() })
            Text(source.title, style = KsType.caption, color = c.ink)
            SelectionContainer { Text(source.url, style = KsType.caption, color = c.inkMuted) }
            source.checkedAt?.let { Text(stringResource(R.string.source_checked, formatDate(it)), style = KsType.caption, color = c.inkMuted) }
            if (reviewOverdue) Text(stringResource(R.string.review_overdue), style = KsType.body, color = c.miss)
        }
    }
}

@Composable
internal fun AuthoredLessonSection(section: LessonSection, support: LessonSection?, language: String, easyFinnish: Boolean, preference: ExplanationPreference, reviewed: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StudyText(section.title, language, MaterialTheme.typography.titleLarge, Modifier.semantics { heading() })
        StudyText(section.body, language, MaterialTheme.typography.bodyLarge)
        if (language == "fi" && easyFinnish && section.easyBody != null) {
            Text(stringResource(R.string.easy_finnish_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            StudyText(section.easyBody, "fi", MaterialTheme.typography.bodyLarge)
        }
        preference.tag?.let { tag ->
            Text(stringResource(preference.explanationTitle), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            support?.let {
                StudyText(it.title, tag, MaterialTheme.typography.titleSmall)
                StudyText(it.body, tag, MaterialTheme.typography.bodyLarge)
                if (!reviewed) MachineTranslationNote()
            } ?: Text(stringResource(preference.lessonMissing), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
