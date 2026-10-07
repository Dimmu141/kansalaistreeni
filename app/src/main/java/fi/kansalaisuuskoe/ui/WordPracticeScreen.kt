package fi.kansalaisuuskoe.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import fi.kansalaisuuskoe.R
import fi.kansalaisuuskoe.domain.contentStatus

@Composable
internal fun WordPracticeScreen(state: WordPracticeState, reveal: () -> Unit, next: () -> Unit, finish: () -> Unit) {
    if (state.loading) { CircularProgressIndicator(); return }
    key(state.index, state.finished) {
        Page {
            if (state.failed || state.finished) {
                item { Heading(stringResource(if (state.failed) R.string.words_changed else if (state.total == 0) R.string.vocabulary_empty else R.string.words_finished)) }
                item { Text(stringResource(R.string.words_no_score)) }
                item { Button(finish, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.words_return)) } }
            } else state.word?.let { word ->
                item { Heading(stringResource(R.string.words_title), stringResource(R.string.words_instruction)) }
                item { Text(stringResource(R.string.words_position, state.index + 1, state.total)) }
                item { StudyText(word.term, word.language, MaterialTheme.typography.headlineLarge, Modifier.semantics { heading() }) }
                if (!state.revealed) item { Button(reveal, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.words_reveal)) } }
                else {
                    item { Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) { VocabularyMeaning(word) } }
                    item { Button(next, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(if (state.index + 1 == state.total) R.string.words_finish else R.string.words_next)) } }
                }
                item { Text(stringResource(R.string.words_no_score), style = MaterialTheme.typography.bodySmall) }
                item { ContentNotice(word.contexts.contentStatus(), compact = true) }
            }
        }
    }
}
