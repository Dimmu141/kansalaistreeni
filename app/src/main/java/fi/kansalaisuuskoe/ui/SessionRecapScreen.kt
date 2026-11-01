package fi.kansalaisuuskoe.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import fi.kansalaisuuskoe.R
import fi.kansalaisuuskoe.data.ExplanationPreference
import fi.kansalaisuuskoe.domain.Question
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import java.time.LocalDate
import androidx.compose.ui.text.style.TextAlign
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields

/** "Miksi?", the easy-Finnish well and the optional English explanation (design system ExplanationCard). */
@Composable
internal fun QuestionExplanation(question: Question, easyFinnish: Boolean, preference: ExplanationPreference, title: Int = R.string.why) {
    val c = Ks.colors
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(title), style = KsType.title.copy(fontSize = 20.sp, lineHeight = 26.sp), modifier = Modifier.weight(1f).semantics { heading() })
            SpeakButton("explanation-${question.id}", question.explanation, question.language)
        }
        StudyText(question.explanation, question.language, KsType.bodyLarge)
        if (question.language == "fi" && easyFinnish && question.easyExplanation != null) {
            Surface(color = c.surface2, contentColor = c.ink, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.easy_finnish_title), style = KsType.overline, color = c.inkMuted, modifier = Modifier.semantics { heading() })
                    StudyText(question.easyExplanation, "fi", KsType.bodyLarge)
                }
            }
        }
        preference.tag?.let { tag ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(preference.explanationTitle), style = KsType.title, modifier = Modifier.semantics { heading() })
                question.translations[tag]?.let {
                    // The question's meaning first: the learner sees the original above and the exam is in Finnish or Swedish.
                    StudyText(it.text, tag, KsType.body.copy(fontStyle = FontStyle.Italic, color = c.inkMuted))
                    StudyText(it.explanation, tag, KsType.bodyLarge)
                    if (!it.reviewed) MachineTranslationNote()
                } ?: Text(stringResource(preference.explanationMissing), style = KsType.body, color = c.inkMuted)
            }
        }
    }
}

/** Key words as one grouped list: the word in bold, its translations on one muted line. */
@Composable
internal fun VocabularyList(question: Question) {
    val c = Ks.colors
    val swedish = question.language == "sv"
    val support = LocalSupportLanguage.current.tag?.takeIf { it != "en" }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.vocabulary_title), style = KsType.title, modifier = Modifier.semantics { heading() })
        Surface(color = c.surface1, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, c.line)) {
            Column {
                question.vocabulary.forEachIndexed { index, word ->
                    if (index > 0) HorizontalDivider(color = c.line)
                    val other = if (swedish) word.term else word.swedish
                    val supportMeaning = support?.let { question.translations[it]?.vocabulary?.get(word.term) }
                    val description = listOfNotNull(
                        stringResource(R.string.vocabulary_english, word.meaning),
                        supportMeaning?.let { stringResource(LocalSupportLanguage.current.meaningLine, it) },
                        other?.let { stringResource(if (swedish) R.string.vocabulary_finnish else R.string.vocabulary_swedish, it) },
                    ).joinToString(". ")
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StudyText(if (swedish) word.swedish ?: word.term else word.term, if (swedish && word.swedish != null) "sv" else "fi",
                                KsType.title.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold), Modifier.weight(1f))
                            Text(listOfNotNull("EN", supportMeaning?.let { support?.uppercase() }, other?.let { if (swedish) "FI" else "SV" }).joinToString(" · "), style = KsType.overline.copy(fontSize = 11.sp),
                                color = c.inkMuted, modifier = Modifier.clearAndSetSemantics {})
                        }
                        Text(listOfNotNull(word.meaning, supportMeaning, other).joinToString(" · "), style = KsType.body.copy(fontSize = 14.sp, lineHeight = 20.sp),
                            color = c.inkMuted, modifier = Modifier.semantics { contentDescription = description })
                    }
                }
            }
        }
    }
}

@Composable
internal fun SessionRecapScreen(state: QuestionUiState, easyFinnish: Boolean, explanation: ExplanationPreference, finish: () -> Unit, practiseAgain: (List<String>) -> Unit = {},
    streak: Int = 0, practiceDays: Set<LocalDate> = emptySet()) {
    Page {
        if (state.total > 0) item { RecapScore(state, streak, practiceDays) }
        else item { Heading(stringResource(R.string.practice_no_questions)) }
        item { Text(if (state.total == 0) stringResource(R.string.practice_empty_body) else stringResource(R.string.practice_summary, state.correctCount, state.total), style = KsType.bodyLarge) }
        if (state.total > 0) {
            item {
                Text(stringResource(R.string.recap_next_step), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(stringResource(if (state.correctCount == state.total) R.string.recap_all_correct else R.string.recap_mistakes_note), style = MaterialTheme.typography.bodyMedium)
            }
            state.recap.reviewAt?.let { date -> item { Text(stringResource(R.string.recap_review_at, formatReviewTime(date)), style = MaterialTheme.typography.bodyMedium) } }
        }
        // Keep the onward actions reachable before the optional longer explanations. Missed questions can be tried
        // again at once; that records attempts but, by the learning rules, cannot lift mastery before the review is due.
        val missed = state.recap.mistakes.map { it.question.id }.distinct()
        if (missed.isNotEmpty()) item {
            KsButton(stringResource(R.string.recap_practise_again, pluralStringResource(R.plurals.question_count, missed.size, missed.size)), { practiseAgain(missed) }, Modifier.fillMaxWidth())
        }
        item { KsButton(stringResource(R.string.return_home), finish, Modifier.fillMaxWidth(), tone = if (missed.isEmpty()) KsTone.Primary else KsTone.Outline) }
        if (state.recap.unavailableCount > 0 || (state.total > 0 && state.recap.answers.size < state.total)) {
            item { Text(stringResource(R.string.recap_unavailable), style = MaterialTheme.typography.bodyMedium) }
        }
        if (state.recap.mistakes.isNotEmpty()) {
            item { Heading(pluralStringResource(R.plurals.recap_mistakes, state.recap.mistakes.size, state.recap.mistakes.size)) }
            state.recap.mistakes.forEach { answer ->
                item(key = answer.attempt.id) {
                    var expanded by rememberSaveable(answer.attempt.id) { mutableStateOf(false) }
                    val question = answer.question
                    val selected = question.options.first { it.id == answer.attempt.selectedOptionId }
                    val correct = question.options.first { it.id == question.correctOptionId }
                    val read = stringResource(R.string.recap_read)
                    val accessibilityLabel = stringResource(R.string.recap_read_label, question.text)
                    val expandedLabel = stringResource(if (expanded) R.string.recap_expanded else R.string.recap_collapsed)
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HorizontalDivider()
                        StudyText(question.text, question.language, KsType.title, Modifier.semantics { heading() })
                        AnswerOutcome(question, selected.id, correct.id)
                        OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics {
                            contentDescription = accessibilityLabel
                            stateDescription = expandedLabel
                        }) { Text(if (expanded) stringResource(R.string.recap_hide) else read) }
                        if (expanded) {
                            QuestionExplanation(question, easyFinnish, explanation)
                            SourceReference(question)
                        }
                    }
                }
            }
        }
    }
}

/** The celebration: a badge with the score (a birch star for a full score), the session's scoreboard, then accuracy and streak tiles. */
@Composable
private fun RecapScore(state: QuestionUiState, streak: Int, practiceDays: Set<LocalDate>) {
    val full = state.correctCount == state.total
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        ScoreBadge(stringResource(R.string.question_counter, state.correctCount, state.total), full)
        Text(stringResource(R.string.practice_complete), style = KsType.headline.copy(fontWeight = FontWeight.ExtraBold), color = if (full) Ks.colors.onBirchSoft else Ks.colors.ink,
            textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        if (state.total <= 10 && state.recap.answers.isNotEmpty()) ProgressSegments(List(state.total) { i ->
            when (state.recap.answers.getOrNull(i)?.attempt?.correct) { true -> Segment.Correct; false -> Segment.Miss; null -> Segment.Upcoming }
        }, Modifier.clearAndSetSemantics {})
        val c = Ks.colors
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RecapTile(stringResource(R.string.recap_tile_accuracy), stringResource(R.string.percent_value, state.correctCount * 100 / state.total),
                c.correct, c.correctSoft, c.onCorrectSoft, Modifier.weight(1f))
            RecapTile(stringResource(R.string.recap_tile_streak), stringResource(R.string.day_count, streak), c.birch, c.birchSoft, c.onBirchSoft, Modifier.weight(1f))
        }
        WeekStrip(practiceDays)
    }
}

/** A raised disc: brand for a session, birch with confetti for a full score. The score is read from the summary text instead. */
@Composable
private fun ScoreBadge(score: String, full: Boolean) {
    val c = Ks.colors
    val face = if (full) c.birch else c.brand
    val ink = if (full) c.onBirchSoft else c.onBrand
    Box(Modifier.size(156.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val u = size.width / 150f
            if (full) listOf(Triple(30f, 22f, c.brand), Triple(128f, 34f, c.correct), Triple(14f, 96f, c.miss), Triple(138f, 112f, c.birch), Triple(116f, 10f, c.birch), Triple(28f, 128f, c.brand))
                .forEach { (x, y, color) -> drawCircle(color, 4.5f * u, Offset(x * u, y * u)) }
            drawCircle(face.edge(), 54f * u, Offset(75f * u, 81f * u))
            drawCircle(face, 54f * u, Offset(75f * u, 75f * u))
        }
        Column(Modifier.padding(bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (full) Icon(KsIcons.Star, null, Modifier.size(40.dp), tint = ink)
            Text(score, style = KsType.headline.copy(fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"), color = ink)
        }
    }
}

@Composable
private fun RecapTile(label: String, value: String, strong: Color, soft: Color, ink: Color, modifier: Modifier) {
    Surface(color = soft, contentColor = ink, shape = MaterialTheme.shapes.medium, border = BorderStroke(2.dp, strong),
        modifier = modifier.semantics(mergeDescendants = true) {}) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label.uppercase(LocalConfiguration.current.locales[0]), style = KsType.overline)
            Text(value, style = KsType.headline.copy(fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"))
        }
    }
}

/** This week's practice days as filled birch dots, today ringed, so the streak is something to see. */
@Composable
private fun WeekStrip(practiceDays: Set<LocalDate>) {
    val c = Ks.colors
    val locale = LocalConfiguration.current.locales[0]
    val today = LocalDate.now()
    val start = today.with(TemporalAdjusters.previousOrSame(WeekFields.of(locale).firstDayOfWeek))
    val week = List(7) { start.plusDays(it.toLong()) }
    val practised = week.count { it in practiceDays }
    val label = "${stringResource(R.string.recap_week)}: ${stringResource(R.string.day_count, practised)}"
    Surface(color = c.surface1, shape = MaterialTheme.shapes.medium, border = BorderStroke(2.dp, c.line), modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = label }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.recap_week), style = KsType.label)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { day ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT_STANDALONE, locale).take(2), style = KsType.caption, color = c.inkMuted, maxLines = 1)
                        val done = day in practiceDays
                        Box(Modifier.size(32.dp)
                            .then(if (day == today) Modifier.border(2.dp, c.birch.edge(), CircleShape).padding(4.dp) else Modifier)
                            .background(if (done) c.birch else c.surface2, CircleShape))
                    }
                }
            }
        }
    }
}

/** The learner's answer and the right one as labelled rows with the verdict icon, so colour is never the only cue. */
@Composable
internal fun AnswerOutcome(question: Question, selectedId: String?, correctId: String) {
    val c = Ks.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // A null answer is a timed practice question left unanswered when time ran out; only the key is shown.
        listOfNotNull(selectedId?.let { Triple(R.string.your_answer, it, it == correctId) },
            Triple(R.string.correct_answer_label, correctId, true)).forEach { (label, id, right) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VerdictBadge(right, size = 24.dp, animate = false)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(label), style = KsType.overline, color = if (right) c.correct else c.miss)
                    AnswerText(question, id, KsType.option)
                }
            }
        }
    }
}
