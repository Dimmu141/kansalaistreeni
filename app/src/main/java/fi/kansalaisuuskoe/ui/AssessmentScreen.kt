package fi.kansalaisuuskoe.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import fi.kansalaisuuskoe.R
import fi.kansalaisuuskoe.domain.*
import kotlinx.coroutines.delay

@Composable
private fun AssessmentStatus(state: AssessmentUiState, retry: () -> Unit) {
    state.error?.let {
        Text(stringResource(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (!state.busy && it != R.string.assessment_delete_stale) TextButton(onClick = retry) { Text(stringResource(R.string.retry)) }
    }
    if (state.loading || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().semanticsLabel(stringResource(R.string.saving)))
}

@Composable
internal fun AssessmentsScreen(state: AssessmentUiState, app: AppUiState, start: (Int, Boolean) -> Unit, open: (String) -> Unit, retry: () -> Unit, delete: (AssessmentSession) -> Unit) {
    val sessions = state.sessions.filter { it.language == app.settings.studyLanguage.tag }
    val active = sessions.firstOrNull { !it.complete }
    val available = app.studySnapshot.questions.size
    // Offer only lengths the installed material can fill; small material gets one length of everything.
    val lengths = Assessments.LENGTHS.filter { it <= available }.ifEmpty { listOf(available) }
    var wanted by rememberSaveable { mutableIntStateOf(ExamProfile.CURRENT.questionCount) }
    var timed by rememberSaveable { mutableStateOf(false) }
    val count = lengths.lastOrNull { it <= wanted } ?: lengths.first()
    Page {
        item { Heading(stringResource(R.string.assessment_title), stringResource(R.string.assessment_intro)) }
        item { StudyScope(app) }
        item { Text(stringResource(R.string.assessment_rules), style = MaterialTheme.typography.bodyMedium) }
        if (active != null) {
            item { Text(stringResource(R.string.assessment_saved_progress, active.answeredCount, active.questions.size)) }
            item { KsButton(stringResource(R.string.assessment_resume), { open(active.id) }, Modifier.fillMaxWidth(), enabled = !state.busy && !state.loading) }
            item { AssessmentStatus(state, retry) }
            item { AssessmentDeleteAction(active, state.busy || state.loading, delete) }
        } else if (count > 0) {
            if (lengths.size > 1) item { LengthChoice(lengths, count) { wanted = it } }
            item { TimeLimitChoice(timed, count * Assessments.MINUTES_PER_QUESTION) { timed = it } }
            item { AssessmentStatus(state, retry) }
            item { KsButton(stringResource(R.string.assessment_start), { start(count, timed) },
                Modifier.fillMaxWidth(), enabled = !state.busy && !state.loading) }
        } else {
            item { AssessmentStatus(state, retry) }
            item { Text(stringResource(R.string.language_empty_body)) }
        }
        item { Heading(stringResource(R.string.assessment_history), stringResource(R.string.assessment_storage_note)) }
        if (sessions.none { it.complete }) item { Text(stringResource(R.string.assessment_history_empty)) }
        items(sessions.filter { it.complete }, key = { it.id }) { session ->
            val result = Assessments.result(session)
            KsCard(onClick = { open(session.id) }) {
                Text(formatDateTime(requireNotNull(session.finishedAt)), style = KsType.caption, color = Ks.colors.inkMuted)
                Text(stringResource(R.string.assessment_score, result.correct, result.total, result.accuracy), style = KsType.title.copy(fontFeatureSettings = "tnum"))
                session.timeLimitMinutes?.let { Text(stringResource(R.string.assessment_time_limit_used, it), style = KsType.caption, color = Ks.colors.inkMuted) }
                Text(stringResource(R.string.assessment_view_result), style = KsType.body, color = Ks.colors.brand)
            }
        }
    }
}

/** Question-count choice styled like the study-language switch: a raised thumb on a `surface-2` track. */
@Composable
private fun LengthChoice(lengths: List<Int>, selected: Int, choose: (Int) -> Unit) {
    val c = Ks.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.assessment_length_title), style = KsType.title, modifier = Modifier.semantics { heading() })
        Row(Modifier.fillMaxWidth().clip(CircleShape).background(c.surface2).padding(3.dp).selectableGroup()) {
            lengths.forEach { length ->
                val active = selected == length
                val description = pluralStringResource(R.plurals.question_count, length, length)
                val fill by animateColorAsState(if (active) c.surface1 else c.surface2, tween(KsMotion.SELECT), label = "length")
                val ink by animateColorAsState(if (active) c.brand else c.inkMuted, tween(KsMotion.SELECT), label = "lengthText")
                Surface(color = fill, contentColor = ink, shape = CircleShape, shadowElevation = if (active) 1.dp else 0.dp,
                    modifier = Modifier.weight(1f).selectable(active, role = Role.RadioButton, onClick = { choose(length) }).semantics { contentDescription = description }) {
                    Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                        Text(length.toString(), style = KsType.label.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"))
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeLimitChoice(timed: Boolean, minutes: Int, choose: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.assessment_timer_title), style = KsType.title)
            Text(stringResource(R.string.assessment_timer_note, minutes), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = timed, onCheckedChange = choose, modifier = Modifier.semanticsLabel(stringResource(R.string.assessment_timer_title)))
    }
}

/** Remaining time of a timed practice; when it reaches zero the attempt is finished as it stands. */
@Composable
private fun TimeLeftChip(session: AssessmentSession, deadline: Long, finish: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session.id) {
        while (true) {
            now = System.currentTimeMillis()
            // The view model ignores a call while another write is running, so keep asking until the result opens.
            if (now >= deadline) finish()
            delay(1000)
        }
    }
    val seconds = ((deadline - now).coerceAtLeast(0) + 999) / 1000
    val description = stringResource(R.string.assessment_time_left_description, (seconds + 59) / 60)
    KsChip(stringResource(R.string.assessment_time_left, "%d:%02d".format(seconds / 60, seconds % 60)), if (seconds <= 300) ChipTone.Birch else ChipTone.Brand,
        modifier = Modifier.clearAndSetSemantics { contentDescription = description })
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
    val language = topicLanguage(session.language)
    val list = key(session.id, question.id) { rememberLazyListState() }
    Page(list) {
        item {
            val counterLabel = stringResource(R.string.question_position, session.position + 1, session.questions.size)
            val topic = session.topics.first { it.id == question.topicId }.titleFor(language)
            val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(topic.uppercase(locale), style = KsType.overline, color = Ks.colors.brand, modifier = Modifier.weight(1f).semantics { contentDescription = topic })
                    Text(stringResource(R.string.question_counter, session.position + 1, session.questions.size), style = KsType.caption.copy(fontFeatureSettings = "tnum"),
                        color = Ks.colors.inkMuted, modifier = Modifier.semantics { contentDescription = counterLabel })
                }
                ProgressBar(session.answeredCount.toFloat() / session.questions.size)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    session.deadline?.let { TimeLeftChip(session, it) { model.finish(session.id) } }
                    KsChip(stringResource(R.string.assessment_saved_progress, session.answeredCount, session.questions.size))
                    if (locale.language != session.language) KsChip(stringResource(if (session.language == "sv") R.string.question_language_swedish else R.string.question_language))
                }
                Text(stringResource(R.string.assessment_feedback_later), style = KsType.caption, color = Ks.colors.inkMuted)
            }
        }
        item { StudyText(question.text, question.language, KsType.question, Modifier.semantics { heading() }) }
        item { AnswerChoices(question, selected, !state.busy, { model.select(session.id, question.id, it) }) }
        item { AssessmentStatus(state, model::retry) }
        if (session.position == session.questions.lastIndex) {
            item { KsButton(stringResource(R.string.assessment_finish), { model.finish(session.id) }, Modifier.fillMaxWidth(), enabled = !state.busy && session.answeredCount == session.questions.size) }
        } else {
            item { KsButton(stringResource(R.string.next_question), { model.move(session.id, session.position + 1) }, Modifier.fillMaxWidth(), enabled = selected != null && !state.busy) }
        }
        if (session.position > 0) item { OutlinedButton({ model.move(session.id, session.position - 1) }, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = !state.busy) { Text(stringResource(R.string.assessment_previous)) } }
        item { Text(stringResource(if (session.timeLimitMinutes != null) R.string.assessment_exit_note_timed else R.string.assessment_exit_note), style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun AssessmentResultScreen(session: AssessmentSession, app: AppUiState, state: AssessmentUiState, retry: () -> Unit, delete: (AssessmentSession) -> Unit, practice: () -> Unit, leave: () -> Unit) {
    val result = Assessments.result(session)
    val eligible = Assessments.practiceMistakes(session, app.snapshot.questions)
    val language = topicLanguage(session.language)
    Page {
        item { Heading(stringResource(R.string.assessment_result_title), formatDateTime(requireNotNull(session.finishedAt))) }
        item { Text(stringResource(if (session.language == "sv") R.string.question_language_swedish else R.string.question_language)) }
        item { Text(stringResource(R.string.assessment_score, result.correct, result.total, result.accuracy), style = KsType.headline.copy(fontFeatureSettings = "tnum"), color = Ks.colors.brand, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        session.timeLimitMinutes?.let { item { Text(stringResource(R.string.assessment_time_limit_used, it), style = KsType.caption, color = Ks.colors.inkMuted) } }
        if (result.unanswered > 0) item { Text(pluralStringResource(R.plurals.assessment_unanswered, result.unanswered, result.unanswered)) }
        item { Text(stringResource(R.string.assessment_score_note), style = MaterialTheme.typography.bodyMedium) }
        if (result.mistakes.isEmpty()) item { Text(stringResource(R.string.assessment_all_correct)) }
        else {
            item { KsButton(stringResource(R.string.assessment_practice_mistakes, pluralStringResource(R.plurals.question_count, eligible.size, eligible.size)), practice, Modifier.fillMaxWidth(), enabled = eligible.isNotEmpty() && !state.busy) }
            if (eligible.size < result.mistakes.size) item { Text(stringResource(R.string.assessment_changed_items, pluralStringResource(R.plurals.question_count, result.mistakes.size - eligible.size, result.mistakes.size - eligible.size))) }
        }
        item { OutlinedButton(leave, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.assessment_back_to_list)) } }
        item { AssessmentStatus(state, retry) }
        item { AssessmentDeleteAction(session, state.busy || state.loading, delete) }
        item { Heading(stringResource(R.string.assessment_topic_results)) }
        items(result.topics, key = { it.topic.id }) { topic ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(topic.topic.titleFor(language), style = KsType.title, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.assessment_topic_score, topic.correct, topic.total), style = KsType.caption.copy(fontFeatureSettings = "tnum"), color = Ks.colors.inkMuted)
                }
                ProgressBar(topic.correct.toFloat() / topic.total)
            }
        }
        item { Heading(stringResource(R.string.assessment_answers)) }
        items(session.questions, key = { it.id }) { question ->
            var expanded by rememberSaveable(session.id, question.id) { mutableStateOf(false) }
            val selected = session.answers[question.id]
            val correct = question.options.first { it.id == question.correctOptionId }
            val expandedLabel = stringResource(if (expanded) R.string.recap_expanded else R.string.recap_collapsed)
            val readLabel = stringResource(R.string.recap_read_label, question.text)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider(color = Ks.colors.line)
                StudyText(question.text, question.language, KsType.title, Modifier.semantics { heading() })
                Text(stringResource(when (selected) { correct.id -> R.string.answer_correct; null -> R.string.assessment_no_answer; else -> R.string.answer_incorrect }), style = KsType.label,
                    color = if (selected == correct.id) Ks.colors.correct else Ks.colors.miss)
                AnswerOutcome(question, selected, correct.id)
                OutlinedButton({ expanded = !expanded }, Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics {
                    stateDescription = expandedLabel
                    contentDescription = readLabel
                }) { Text(stringResource(if (expanded) R.string.recap_hide else R.string.recap_read)) }
                if (expanded) {
                    QuestionExplanation(question, app.settings.easyFinnish, app.settings.explanation)
                    SourceReference(question)
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
                    Text(formatDateTime(target.finishedAt ?: target.startedAt))
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
