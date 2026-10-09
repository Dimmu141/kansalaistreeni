package fi.kansalaisuuskoe.ui

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
import fi.kansalaisuuskoe.R
import fi.kansalaisuuskoe.data.ExplanationPreference
import fi.kansalaisuuskoe.domain.Question

@Composable
internal fun QuestionExplanation(question: Question, easyFinnish: Boolean, preference: ExplanationPreference, title: Int = R.string.why) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Heading(stringResource(title))
            StudyText(question.explanation, question.language, MaterialTheme.typography.bodyLarge)
        }
        if (question.language == "fi" && easyFinnish && question.easyExplanation != null) {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.easy_finnish_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    StudyText(question.easyExplanation, "fi", MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (preference == ExplanationPreference.ALSO_ENGLISH) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.explanation_english_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                question.translations["en"]?.let { StudyText(it.explanation, "en", MaterialTheme.typography.bodyLarge) }
                    ?: Text(stringResource(R.string.explanation_missing), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
internal fun SessionRecapScreen(state: QuestionUiState, easyFinnish: Boolean, explanation: ExplanationPreference, finish: () -> Unit) {
    Page {
        item { Heading(stringResource(if (state.total == 0) R.string.practice_no_questions else R.string.practice_complete)) }
        item { Text(if (state.total == 0) stringResource(R.string.practice_empty_body) else stringResource(R.string.practice_summary, state.correctCount, state.total), style = MaterialTheme.typography.bodyLarge) }
        if (state.total > 0) {
            item {
                Text(stringResource(R.string.recap_next_step), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(stringResource(if (state.correctCount == state.total) R.string.recap_all_correct else R.string.recap_mistakes_note), style = MaterialTheme.typography.bodyMedium)
            }
            state.recap.reviewAt?.let { date -> item { Text(stringResource(R.string.recap_review_at, formatReviewTime(date)), style = MaterialTheme.typography.bodyMedium) } }
        }
        // Keep the onward action reachable before the optional longer explanations.
        item { Button(onClick = finish, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.return_home)) } }
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
                        StudyText(question.text, question.language, MaterialTheme.typography.titleLarge, Modifier.semantics { heading() })
                        Text(stringResource(R.string.your_answer), style = MaterialTheme.typography.labelLarge)
                        AnswerText(question, selected.id, MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.correct_answer_label), style = MaterialTheme.typography.labelLarge)
                        AnswerText(question, correct.id, MaterialTheme.typography.bodyLarge)
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
