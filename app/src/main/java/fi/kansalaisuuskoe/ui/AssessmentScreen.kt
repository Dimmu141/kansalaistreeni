package fi.kansalaisuuskoe.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import fi.kansalaisuuskoe.R
import fi.kansalaisuuskoe.domain.*

@Composable
private fun AssessmentStatus(state: AssessmentUiState, retry: () -> Unit) {
    state.error?.let {
        Text(stringResource(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (!state.busy && it != R.string.assessment_delete_stale) TextButton(onClick = retry) { Text(stringResource(R.string.retry)) }
    }
    if (state.loading || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().semanticsLabel(stringResource(R.string.saving)))
}

@Composable
internal fun AssessmentsScreen(state: AssessmentUiState, app: AppUiState, start: () -> Unit, open: (String) -> Unit, retry: () -> Unit, delete: (AssessmentSession) -> Unit) {
    val sessions = state.sessions.filter { it.language == app.settings.studyLanguage.tag }
    val active = sessions.firstOrNull { !it.complete }
    val count = minOf(Assessments.MAX_QUESTIONS, app.studySnapshot.questions.size)
    Page {
        item { Heading(stringResource(R.string.assessment_title), stringResource(R.string.assessment_intro)) }
        item { StudyScope(app) }
        item { Text(stringResource(R.string.assessment_rules), style = MaterialTheme.typography.bodyMedium) }
        if (active != null) {
            item { Text(stringResource(R.string.assessment_saved_progress, active.answeredCount, active.questions.size)) }
            item { Button({ open(active.id) }, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = !state.busy && !state.loading) { Text(stringResource(R.string.assessment_resume)) } }
            item { AssessmentStatus(state, retry) }
            item { AssessmentDeleteAction(active, state.busy || state.loading, delete) }
        } else if (count > 0) {
            item { AssessmentStatus(state, retry) }
            item { Text(stringResource(R.string.assessment_length, pluralStringResource(R.plurals.question_count, count, count))) }
            item { Button(start, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = !state.busy && !state.loading) { Text(stringResource(R.string.assessment_start)) } }
        } else {
            item { AssessmentStatus(state, retry) }
            item { Text(stringResource(R.string.language_empty_body)) }
        }
        item { ContentNotice(app.contentStatus) }
        item { Heading(stringResource(R.string.assessment_history), stringResource(R.string.assessment_storage_note)) }
        if (sessions.none { it.complete }) item { Text(stringResource(R.string.assessment_history_empty)) }
        items(sessions.filter { it.complete }, key = { it.id }) { session ->
            val result = Assessments.result(session)
            OutlinedCard(onClick = { open(session.id) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(formatReviewTime(requireNotNull(session.finishedAt)), style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(R.string.assessment_score, result.correct, result.total, result.accuracy), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.assessment_view_result), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
internal fun AssessmentScreen(state: AssessmentUiState, sessionId: String, app: AppUiState, model: AssessmentViewModel, practice: (String) -> Unit, leave: () -> Unit) {
    val session = state.sessions.find { it.id == sessionId }
    if (session == null) {
        Page {
            item { AssessmentStatus(state, model::retry) }
            if (!state.loading) item { EmptyState(stringResource(R.string.assessment_title), stringResource(R.string.assessment_unavailable), stringResource(R.string.back), leave) }
        }
        return
    }
    if (session.complete) {
        key(session.id, "result") { AssessmentResultScreen(session, app, state, model::retry, { model.delete(it, leave) }, { practice(session.id) }, leave) }
        return
    }
    val question = session.questions[session.position]
    val selected = session.answers[question.id]
    val language = LocalConfiguration.current.locales[0].language
    val list = key(session.id, question.id) { rememberLazyListState() }
    Page(list) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.question_position, session.position + 1, session.questions.size))
                Text(stringResource(R.string.assessment_saved_progress, session.answeredCount, session.questions.size), style = MaterialTheme.typography.bodySmall)
                Text(session.topics.first { it.id == question.topicId }.titleFor(language), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(question.status.labelResource()), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(if (session.language == "sv") R.string.question_language_swedish else R.string.question_language), style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(progress = { session.answeredCount.toFloat() / session.questions.size }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.assessment_feedback_later), style = MaterialTheme.typography.bodyMedium)
            }
        }
        item { StudyText(question.text, question.language, MaterialTheme.typography.headlineMedium, Modifier.semantics { heading() }) }
        item { AnswerChoices(question, selected, !state.busy, { model.select(session.id, question.id, it) }) }
        item { AssessmentStatus(state, model::retry) }
        if (session.position == session.questions.lastIndex) {
            item { Button({ model.finish(session.id) }, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = !state.busy && session.answeredCount == session.questions.size) { Text(stringResource(R.string.assessment_finish)) } }
        } else {
            item { Button({ model.move(session.id, session.position + 1) }, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = selected != null && !state.busy) { Text(stringResource(R.string.next_question)) } }
        }
        if (session.position > 0) item { OutlinedButton({ model.move(session.id, session.position - 1) }, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = !state.busy) { Text(stringResource(R.string.assessment_previous)) } }
        item { Text(stringResource(R.string.assessment_exit_note), style = MaterialTheme.typography.bodySmall) }
        item { ContentNotice(session.questions.contentStatus(), compact = true) }
    }
}

@Composable
private fun AssessmentResultScreen(session: AssessmentSession, app: AppUiState, state: AssessmentUiState, retry: () -> Unit, delete: (AssessmentSession) -> Unit, practice: () -> Unit, leave: () -> Unit) {
    val result = Assessments.result(session)
    val eligible = Assessments.practiceMistakes(session, app.snapshot.questions)
    val language = LocalConfiguration.current.locales[0].language
    Page {
        item { Heading(stringResource(R.string.assessment_result_title), formatReviewTime(requireNotNull(session.finishedAt))) }
        item { Text(stringResource(if (session.language == "sv") R.string.question_language_swedish else R.string.question_language)) }
        item { Text(stringResource(R.string.assessment_score, result.correct, result.total, result.accuracy), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        item { Text(stringResource(R.string.assessment_score_note), style = MaterialTheme.typography.bodyMedium) }
        if (result.mistakes.isEmpty()) item { Text(stringResource(R.string.assessment_all_correct)) }
        else {
            item { Button(practice, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = eligible.isNotEmpty() && !state.busy) { Text(stringResource(R.string.assessment_practice_mistakes, pluralStringResource(R.plurals.question_count, eligible.size, eligible.size))) } }
            if (eligible.size < result.mistakes.size) item { Text(stringResource(R.string.assessment_changed_items, pluralStringResource(R.plurals.question_count, result.mistakes.size - eligible.size, result.mistakes.size - eligible.size))) }
        }
        item { OutlinedButton(leave, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.assessment_back_to_list)) } }
        item { AssessmentStatus(state, retry) }
        item { AssessmentDeleteAction(session, state.busy || state.loading, delete) }
        item { ContentNotice(session.questions.contentStatus(), compact = true) }
        item { Heading(stringResource(R.string.assessment_topic_results)) }
        items(result.topics, key = { it.topic.id }) { topic ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(topic.topic.titleFor(language), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.assessment_topic_score, topic.correct, topic.total))
                LinearProgressIndicator(progress = { topic.correct.toFloat() / topic.total }, modifier = Modifier.fillMaxWidth())
            }
        }
        item { Heading(stringResource(R.string.assessment_answers)) }
        items(session.questions, key = { it.id }) { question ->
            var expanded by rememberSaveable(session.id, question.id) { mutableStateOf(false) }
            val selected = question.options.first { it.id == session.answers[question.id] }
            val correct = question.options.first { it.id == question.correctOptionId }
            val expandedLabel = stringResource(if (expanded) R.string.recap_expanded else R.string.recap_collapsed)
            val readLabel = stringResource(R.string.recap_read_label, question.text)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider()
                StudyText(question.text, question.language, MaterialTheme.typography.titleLarge, Modifier.semantics { heading() })
                Text(stringResource(if (selected.id == correct.id) R.string.answer_correct else R.string.answer_incorrect), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(R.string.your_answer), style = MaterialTheme.typography.labelLarge)
                StudyText(selected.text, question.language, MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.correct_answer_label), style = MaterialTheme.typography.labelLarge)
                StudyText(correct.text, question.language, MaterialTheme.typography.bodyLarge)
                OutlinedButton({ expanded = !expanded }, Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics {
                    stateDescription = expandedLabel
                    contentDescription = readLabel
                }) { Text(stringResource(if (expanded) R.string.recap_hide else R.string.recap_read)) }
                if (expanded) {
                    QuestionExplanation(question, app.settings.easyFinnish, app.settings.explanation)
                    Text(stringResource(R.string.source_title), style = MaterialTheme.typography.titleMedium)
                    Text(question.source.title)
                    SelectionContainer { Text(question.source.url, style = MaterialTheme.typography.bodySmall) }
                    if (question.status != ContentStatus.OFFICIAL) Text(stringResource(R.string.source_note), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun AssessmentDeleteAction(session: AssessmentSession, busy: Boolean, delete: (AssessmentSession) -> Unit) {
    // Keep the reviewed snapshot in memory only. Recreation requires a fresh confirmation.
    var reviewed by remember(session.id) { mutableStateOf<AssessmentSession?>(null) }
    val label = stringResource(if (session.complete) R.string.assessment_delete_result else R.string.assessment_discard)
    TextButton({ reviewed = session }, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = !busy,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(label) }
    reviewed?.let { target ->
        AlertDialog(
            onDismissRequest = { reviewed = null },
            title = { Text(stringResource(if (target.complete) R.string.assessment_delete_title else R.string.assessment_discard_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(if (target.language == "sv") R.string.question_language_swedish else R.string.question_language))
                    Text(formatReviewTime(target.finishedAt ?: target.startedAt))
                    if (target.complete) {
                        val result = Assessments.result(target)
                        Text(stringResource(R.string.assessment_score, result.correct, result.total, result.accuracy))
                    } else Text(stringResource(R.string.assessment_saved_progress, target.answeredCount, target.questions.size))
                    Text(stringResource(if (target.complete) R.string.assessment_delete_note else R.string.assessment_discard_note))
                    Text(stringResource(R.string.assessment_delete_backup_note), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton({ reviewed = null; delete(target) }, enabled = !busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.assessment_delete_confirm)) }
            },
            dismissButton = { TextButton({ reviewed = null }) { Text(stringResource(R.string.assessment_delete_cancel)) } },
        )
    }
}
