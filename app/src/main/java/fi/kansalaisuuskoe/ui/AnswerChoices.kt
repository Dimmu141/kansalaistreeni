package fi.kansalaisuuskoe.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import fi.kansalaisuuskoe.R
import fi.kansalaisuuskoe.domain.Question

/** Shared accessible options; assessments always keep feedback false until completion. */
@Composable
internal fun AnswerChoices(question: Question, selected: String?, enabled: Boolean, select: (String) -> Unit, feedback: Boolean = false) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        question.options.forEachIndexed { index, option ->
            val isSelected = selected == option.id
            val isAnswer = feedback && option.id == question.correctOptionId
            val isWrong = feedback && isSelected && selected != question.correctOptionId
            Surface(
                modifier = Modifier.fillMaxWidth().selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton, onClick = { select(option.id) }),
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
                Row(Modifier.padding(16.dp).heightIn(min = 24.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(stringResource(R.string.option_number, index + 1), style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(32.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        StudyText(option.text, question.language, MaterialTheme.typography.bodyLarge)
                        if (isAnswer) Text(stringResource(R.string.correct_answer_label), style = MaterialTheme.typography.labelLarge)
                        if (isWrong) Text(stringResource(R.string.your_answer), style = MaterialTheme.typography.labelLarge)
                    }
                    if (!feedback) RadioButton(selected = isSelected, onClick = null, enabled = enabled)
                }
            }
        }
    }
}
