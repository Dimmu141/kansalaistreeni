package fi.kansalaisuuskoe.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import fi.kansalaisuuskoe.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
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
    val c = Ks.colors
    val language = LocalConfiguration.current.locales[0].language
    val correct = state.selected == q.correctOptionId
    val listState = key(q.id) { rememberLazyListState() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var dock by remember { mutableStateOf(0.dp) }
    val explanationIndex = 3 + (if (state.error != null) 1 else 0)
    Box(Modifier.fillMaxSize()) {
        Page(state = listState, bottomInset = dock) {
            item {
                val segments = if (state.total in 1..10) List(state.total) { i ->
                    when {
                        i < state.position - 1 -> if (state.recap.answers.getOrNull(i)?.attempt?.correct == false) Segment.Miss else Segment.Correct
                        i == state.position - 1 -> if (!state.answered) Segment.Current else if (correct) Segment.Correct else Segment.Miss
                        else -> Segment.Upcoming
                    }
                } else null
                val chips = buildList {
                    state.dailyReason?.let { reason ->
                        add(stringResource(when (reason) {
                            DailyQuestionReason.DUE_REVIEW -> R.string.daily_reason_review
                            DailyQuestionReason.WEAK_TOPIC -> R.string.daily_reason_weak
                            DailyQuestionReason.NEW_TOPIC -> R.string.daily_reason_new_topic
                            DailyQuestionReason.NEW_QUESTION -> R.string.daily_reason_new_question
                        }))
                    }
                    if (language != q.language) add(stringResource(if (q.language == "sv") R.string.question_language_swedish else R.string.question_language))
                }
                QuestionHeader(topics.find { it.id == q.topicId }?.titleFor(topicLanguage(q.language)).orEmpty(), state.position, state.total,
                    segments, (state.position - 1 + if (state.answered) 1 else 0).toFloat() / state.total, chips)
            }
            item { StudyText(q.text, q.language, KsType.question, Modifier.semantics { heading() }) }
            item { AnswerChoices(q, state.selected, !state.answered && !state.saving && !state.retryPending, select, state.answered) }
            state.error?.let { item { Text(stringResource(it), color = c.miss, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) } }
            if (!state.answered) {
                item { Text(stringResource(R.string.choose_answer_note), style = KsType.caption, color = c.inkMuted) }
            } else {
                item { QuestionExplanation(q, easyFinnish, explanation) }
                if (q.vocabulary.isNotEmpty()) item { VocabularyList(q) }
                item { SourceReference(q) }
            }
        }
        // Check and Next share one docked thumb slot, so a session is one rhythm: tap, check, next.
        Box(Modifier.align(Alignment.BottomCenter).widthIn(max = 760.dp).fillMaxWidth()
            .onSizeChanged { dock = with(density) { it.height.toDp() } }) {
            if (!state.answered) {
                Column {
                    Box(Modifier.fillMaxWidth().height(24.dp).background(Brush.verticalGradient(listOf(c.surface0.copy(alpha = 0f), c.surface0))))
                    Box(Modifier.fillMaxWidth().background(c.surface0).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
                        KsButton(if (state.saving) stringResource(R.string.saving) else stringResource(R.string.check_answer), submit,
                            Modifier.fillMaxWidth(), enabled = state.selected != null && !state.saving)
                    }
                }
            } else {
                val visible = remember(q.id) { MutableTransitionState(false).apply { targetState = true } }
                AnimatedVisibility(visible, enter = slideInVertically(tween(KsMotion.SHEET, delayMillis = 60, easing = EmphasizedDecelerate)) { it }) {
                    FeedbackSheet(
                        correct = correct,
                        // A correct true/false answer would only repeat the heading.
                        answer = if (correct && q.format == QuestionFormat.TRUE_FALSE) null else answerLabel(q, q.correctOptionId),
                        extraPractice = state.extraPractice,
                        nextReview = state.nextReview?.let { formatReviewTime(it) },
                        nextLabel = if (state.position == state.total) stringResource(R.string.show_summary) else stringResource(R.string.next_question),
                        next = next,
                        why = { scope.launch { listState.animateScrollToItem(explanationIndex) } },
                    )
                }
            }
        }
    }
}

private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/** One eyebrow, one counter, one track, then quiet chips for the daily reason and a language mismatch. */
@Composable
private fun QuestionHeader(topic: String, position: Int, total: Int, segments: List<Segment>?, fraction: Float, chips: List<String>) {
    val c = Ks.colors
    val counterLabel = stringResource(R.string.question_position, position, total)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(topic.uppercase(LocalConfiguration.current.locales[0]), style = KsType.overline, color = c.brand,
                modifier = Modifier.weight(1f).semantics { contentDescription = topic })
            Text(stringResource(R.string.question_counter, position, total), style = KsType.caption.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                color = c.inkMuted, modifier = Modifier.semantics { contentDescription = counterLabel })
        }
        if (segments != null) ProgressSegments(segments) else ProgressBar(fraction)
        if (chips.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            chips.forEach { KsChip(it) }
        }
    }
}

/** The verdict panel that rises into the Check button's slot: result, the right answer, the next review and Next. */
@Composable
private fun FeedbackSheet(correct: Boolean, answer: String?, extraPractice: Boolean, nextReview: String?, nextLabel: String, next: () -> Unit, why: () -> Unit) {
    val c = Ks.colors
    Surface(color = if (correct) c.correctSoft else c.missSoft, contentColor = if (correct) c.onCorrectSoft else c.onMissSoft,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), shadowElevation = 8.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    VerdictBadge(correct, size = 40.dp)
                    Text(stringResource(if (correct) R.string.answer_correct else R.string.answer_incorrect), style = KsType.verdict, color = if (correct) c.correct else c.miss)
                }
                answer?.let {
                    val line = stringResource(R.string.correct_answer_value, it)
                    val start = line.lastIndexOf(it)
                    Text(buildAnnotatedString {
                        append(line)
                        if (start >= 0) addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, start + it.length)
                    }, style = KsType.body)
                }
                if (extraPractice) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.extra_practice_title), style = KsType.label)
                    Text(stringResource(R.string.extra_practice_note), style = KsType.body)
                }
                nextReview?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(KsIcons.Clock, null, Modifier.size(16.dp))
                        Text(stringResource(R.string.next_review, it), style = KsType.caption)
                    }
                }
            }
            KsButton(nextLabel, next, Modifier.fillMaxWidth(), tone = if (correct) KsTone.Correct else KsTone.Miss)
            TextButton(why, Modifier.fillMaxWidth().heightIn(min = 44.dp), colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current)) {
                Text(stringResource(R.string.why_read), style = KsType.label)
            }
        }
    }
}
