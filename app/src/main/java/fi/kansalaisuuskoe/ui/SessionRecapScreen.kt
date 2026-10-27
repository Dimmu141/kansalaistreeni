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

/** "Miksi?", the easy-Finnish well and the optional English explanation (design system ExplanationCard). */
@Composable
internal fun QuestionExplanation(question: Question, easyFinnish: Boolean, preference: ExplanationPreference, title: Int = R.string.why) {
    val c = Ks.colors
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(title), style = KsType.title.copy(fontSize = 20.sp, lineHeight = 26.sp), modifier = Modifier.semantics { heading() })
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
internal fun SessionRecapScreen(state: QuestionUiState, easyFinnish: Boolean, explanation: ExplanationPreference, finish: () -> Unit) {
    Page {
        item { Heading(stringResource(if (state.total == 0) R.string.practice_no_questions else R.string.practice_complete)) }
        if (state.total > 0) item { RecapScore(state) }
        item { Text(if (state.total == 0) stringResource(R.string.practice_empty_body) else stringResource(R.string.practice_summary, state.correctCount, state.total), style = KsType.bodyLarge) }
        if (state.total > 0) {
            item {
                Text(stringResource(R.string.recap_next_step), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(stringResource(if (state.correctCount == state.total) R.string.recap_all_correct else R.string.recap_mistakes_note), style = MaterialTheme.typography.bodyMedium)
            }
            state.recap.reviewAt?.let { date -> item { Text(stringResource(R.string.recap_review_at, formatReviewTime(date)), style = MaterialTheme.typography.bodyMedium) } }
        }
        // Keep the onward action reachable before the optional longer explanations.
        item { KsButton(stringResource(R.string.return_home), finish, Modifier.fillMaxWidth()) }
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

/** The score as one big number, the session's segments as a scoreboard, and a birch star for a full score. */
@Composable
private fun RecapScore(state: QuestionUiState) {
    val c = Ks.colors
    val full = state.correctCount == state.total
    Surface(color = if (full) c.birchSoft else c.brandSoft, contentColor = if (full) c.onBirchSoft else c.onBrandSoft, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.question_counter, state.correctCount, state.total), style = KsType.display.copy(fontFeatureSettings = "tnum"),
                    modifier = Modifier.weight(1f).clearAndSetSemantics {})
                if (full) Icon(KsIcons.Star, null, Modifier.size(44.dp), tint = c.birch)
            }
            if (state.total <= 10 && state.recap.answers.isNotEmpty()) ProgressSegments(List(state.total) { i ->
                when (state.recap.answers.getOrNull(i)?.attempt?.correct) { true -> Segment.Correct; false -> Segment.Miss; null -> Segment.Upcoming }
            }, Modifier.clearAndSetSemantics {})
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
