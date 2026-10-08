package fi.kansalaisuuskoe.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fi.kansalaisuuskoe.R
import fi.kansalaisuuskoe.domain.Question
import fi.kansalaisuuskoe.domain.QuestionFormat

/** True/false answers are labelled in the interface language; other answers use the study-language option text. */
@Composable
internal fun answerLabel(question: Question, optionId: String): String =
    if (question.format == QuestionFormat.TRUE_FALSE) {
        stringResource(if (optionId == QuestionFormat.TRUE_ID) R.string.true_false_true else R.string.true_false_false)
    } else question.options.first { it.id == optionId }.text

@Composable
internal fun AnswerText(question: Question, optionId: String, style: TextStyle) {
    if (question.format == QuestionFormat.TRUE_FALSE) Text(answerLabel(question, optionId), style = style)
    else StudyText(answerLabel(question, optionId), question.language, style)
}

/** Shared accessible options; assessments always keep feedback false until completion. */
@Composable
internal fun AnswerChoices(question: Question, selected: String?, enabled: Boolean, select: (String) -> Unit, feedback: Boolean = false) {
    if (question.format == QuestionFormat.TRUE_FALSE) {
        // A statement with two large answers in a fixed order, whatever the file's option order.
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(QuestionFormat.TRUE_ID, QuestionFormat.FALSE_ID).forEach { id ->
                Choice(question, id, selected, enabled, select, feedback, Modifier.weight(1f).heightIn(min = 72.dp)) {
                    Text(answerLabel(question, id), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth())
                }
            }
        }
        return
    }
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        question.options.forEachIndexed { index, option ->
            Choice(question, option.id, selected, enabled, select, feedback, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.option_number, index + 1), style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(32.dp))
                Box(Modifier.weight(1f)) { StudyText(option.text, question.language, MaterialTheme.typography.bodyLarge) }
                if (!feedback) RadioButton(selected = selected == option.id, onClick = null, enabled = enabled)
            }
        }
    }
}

@Composable
private fun Choice(
    question: Question, optionId: String, selected: String?, enabled: Boolean, select: (String) -> Unit,
    feedback: Boolean, modifier: Modifier, content: @Composable RowScope.() -> Unit,
) {
    val isSelected = selected == optionId
    val isAnswer = feedback && optionId == question.correctOptionId
    val isWrong = feedback && isSelected && selected != question.correctOptionId
    Surface(
        modifier = modifier.selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton, onClick = { select(optionId) }),
        color = when {
            isWrong -> MaterialTheme.colorScheme.errorContainer
            isAnswer -> MaterialTheme.colorScheme.secondaryContainer
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surface
        },
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(if (isSelected || isAnswer) 2.dp else 1.dp,
            if (isWrong) MaterialTheme.colorScheme.error else if (isSelected || isAnswer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically, content = content)
            // Text labels keep feedback understandable without colour.
            if (isAnswer) Text(stringResource(R.string.correct_answer_label), style = MaterialTheme.typography.labelLarge, modifier = Modifier.fillMaxWidth())
            if (isWrong) Text(stringResource(R.string.your_answer), style = MaterialTheme.typography.labelLarge, modifier = Modifier.fillMaxWidth())
        }
    }
}
