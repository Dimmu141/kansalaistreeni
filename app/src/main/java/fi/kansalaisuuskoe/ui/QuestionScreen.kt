package fi.kansalaisuuskoe.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import fi.kansalaisuuskoe.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import fi.kansalaisuuskoe.domain.*
import fi.kansalaisuuskoe.data.ExplanationPreference

fun Modifier.semanticsLabel(label: String) = semantics { contentDescription = label }

@Composable
fun QuestionScreen(state: QuestionUiState, topics: List<Topic>, easyFinnish: Boolean, select: (String) -> Unit, submit: () -> Unit, next: () -> Unit, finish: () -> Unit, retry: () -> Unit, explanation: ExplanationPreference = ExplanationPreference.STUDY_LANGUAGE) {
    if (state.loading) { Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator(Modifier.semanticsLabel(stringResource(R.string.loading_practice))) }; return }
    if (state.question == null && !state.finished) {
        Page { item { EmptyState(stringResource(R.string.practice_failed), state.error?.let { stringResource(it) }.orEmpty(), stringResource(R.string.retry), retry) } }; return
    }
    if (state.finished) {
        key("session-recap") { SessionRecapScreen(state, easyFinnish, explanation, finish) }
        return
    }
    val q = requireNotNull(state.question)
    val language = LocalConfiguration.current.locales[0].language
    val correct = state.selected == q.correctOptionId
    val listState = key(q.id) { rememberLazyListState() }
    LaunchedEffect(q.id, state.answered) {
        if (state.answered) listState.animateScrollToItem(3)
    }
    Page(state = listState) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.question_context, topics.find { it.id == q.topicId }?.titleFor(topicLanguage(q.language)).orEmpty(), when (q.status) {
                    ContentStatus.DEMO -> stringResource(R.string.question_demo)
                    ContentStatus.EDITORIAL -> stringResource(R.string.question_editorial)
                    ContentStatus.OFFICIALLY_SOURCED -> stringResource(R.string.question_sourced)
                    ContentStatus.OFFICIAL -> stringResource(R.string.question_official)
                }), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.question_position, state.position, state.total), style = MaterialTheme.typography.bodyMedium)
                if (q.format == QuestionFormat.TRUE_FALSE) Text(stringResource(R.string.true_false_prompt), style = MaterialTheme.typography.bodyMedium)
                state.dailyReason?.let { reason ->
                    Text(stringResource(when (reason) {
                        DailyQuestionReason.DUE_REVIEW -> R.string.daily_reason_review
                        DailyQuestionReason.WEAK_TOPIC -> R.string.daily_reason_weak
                        DailyQuestionReason.NEW_TOPIC -> R.string.daily_reason_new_topic
                        DailyQuestionReason.NEW_QUESTION -> R.string.daily_reason_new_question
                    }), style = MaterialTheme.typography.bodySmall)
                }
                if (language != q.language) Text(stringResource(if (q.language == "sv") R.string.question_language_swedish else R.string.question_language), style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(progress = { (state.position - 1).toFloat() / state.total }, modifier = Modifier.fillMaxWidth())
            }
        }
        item { StudyText(q.text, q.language, MaterialTheme.typography.headlineMedium, Modifier.semantics { heading() }) }
        item { AnswerChoices(q, state.selected, !state.answered && !state.saving && !state.retryPending, select, state.answered) }
        state.error?.let { item { Text(stringResource(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) } }
        if (!state.answered) {
            item {
                Button(onClick = submit, enabled = state.selected != null && !state.saving, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(if (state.saving) stringResource(R.string.saving) else stringResource(R.string.check_answer)) }
            }
            item { Text(stringResource(R.string.choose_answer_note), style = MaterialTheme.typography.bodySmall) }
        } else {
            item {
                Surface(color = if (correct) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (correct) stringResource(R.string.answer_correct) else stringResource(R.string.answer_incorrect), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.correct_answer_value, answerLabel(q, q.correctOptionId)), style = MaterialTheme.typography.bodyLarge)
                        if (state.extraPractice) {
                            Text(stringResource(R.string.extra_practice_title), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.extra_practice_note), style = MaterialTheme.typography.bodyMedium)
                        }
                        state.nextReview?.let { Text(stringResource(R.string.next_review, formatReviewTime(it)), style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            item { QuestionExplanation(q, easyFinnish, explanation) }
            if (q.vocabulary.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.vocabulary_title), style = MaterialTheme.typography.titleMedium)
                    q.vocabulary.forEach { word ->
                        StudyText(if (q.language == "sv") word.swedish ?: word.term else word.term, if (q.language == "sv" && word.swedish != null) "sv" else "fi", MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.vocabulary_english, word.meaning), style = MaterialTheme.typography.bodyMedium)
                        if (q.language == "sv") Text(stringResource(R.string.vocabulary_finnish, word.term), style = MaterialTheme.typography.bodyMedium)
                        else word.swedish?.let { Text(stringResource(R.string.vocabulary_swedish, it), style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            item { SourceReference(q) }
            item { Button(onClick = next, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(if (state.position == state.total) stringResource(R.string.show_summary) else stringResource(R.string.next_question)) } }
        }
    }
}
