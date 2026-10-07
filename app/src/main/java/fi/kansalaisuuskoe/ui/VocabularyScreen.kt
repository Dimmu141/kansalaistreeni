package fi.kansalaisuuskoe.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import fi.kansalaisuuskoe.R
import fi.kansalaisuuskoe.domain.*

@Composable
internal fun VocabularyScreen(state: AppUiState, query: String, topicId: String?, search: (String) -> Unit, filter: (String?) -> Unit, practise: () -> Unit) {
    val entries = remember(state.snapshot.questions, state.settings.studyLanguage) { LearningContent.vocabulary(state.snapshot, state.settings.studyLanguage) }
    val results = remember(entries, query, topicId) { LearningContent.search(entries, query, topicId) }
    val topics = state.studySnapshot.topics.sortedBy { it.order }
    val locale = LocalConfiguration.current.locales[0].language
    var menu by remember { mutableStateOf(false) }
    Page {
        item { Heading(stringResource(R.string.vocabulary_browser_title), stringResource(R.string.vocabulary_intro)) }
        item { StudyScope(state) }
        item { ContentNotice(state.studySnapshot.questions.contentStatus(), compact = true) }
        item { OutlinedTextField(query, search, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.vocabulary_search)) }, singleLine = true) }
        item {
            Box {
                OutlinedButton({ menu = true }, Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.vocabulary_filter, if (topicId == null) stringResource(R.string.vocabulary_all_topics) else topics.find { it.id == topicId }?.titleFor(locale) ?: stringResource(R.string.lesson_unavailable)))
                }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.vocabulary_all_topics)) }, onClick = { filter(null); menu = false })
                    topics.forEach { topic -> DropdownMenuItem(text = { Text(topic.titleFor(locale)) }, onClick = { filter(topic.id); menu = false }) }
                }
            }
        }
        if (query.isNotEmpty() || topicId != null) item { TextButton({ search(""); filter(null) }) { Text(stringResource(R.string.vocabulary_clear)) } }
        item { Text(pluralStringResource(R.plurals.vocabulary_results, results.size, results.size), style = MaterialTheme.typography.labelLarge) }
        if (results.isNotEmpty()) item { Button(practise, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.vocabulary_practise)) } }
        if (results.isEmpty()) item { Text(stringResource(if (entries.isEmpty()) R.string.vocabulary_empty else R.string.vocabulary_no_results)) }
        items(results, key = { it.id }) { word ->
            var expanded by rememberSaveable(word.id) { mutableStateOf(false) }
            val accessibilityLabel = stringResource(R.string.vocabulary_context_label, word.term)
            val expandedLabel = stringResource(if (expanded) R.string.recap_expanded else R.string.recap_collapsed)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider()
                StudyText(word.term, word.language, MaterialTheme.typography.titleLarge, Modifier.semantics { heading() })
                VocabularyMeaning(word)
                OutlinedButton({ expanded = !expanded }, Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics {
                    contentDescription = accessibilityLabel
                    stateDescription = expandedLabel
                }) { Text(stringResource(if (expanded) R.string.vocabulary_hide_context else R.string.vocabulary_show_context)) }
                if (expanded) word.contexts.distinctBy { it.explanation to it.source }.forEach { question ->
                    Text(topics.find { it.id == question.topicId }?.titleFor(locale).orEmpty(), style = MaterialTheme.typography.labelLarge)
                    QuestionExplanation(question, state.settings.easyFinnish, state.settings.explanation, R.string.vocabulary_context)
                    SourceReference(question)
                }
            }
        }
        item { ContentNotice(state.contentStatus) }
    }
}

@Composable
internal fun VocabularyMeaning(word: VocabularyEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.vocabulary_meaning_english), style = MaterialTheme.typography.labelLarge)
        StudyText(word.meaning, "en", MaterialTheme.typography.bodyLarge)
        word.otherTerm?.let {
            Text(stringResource(if (word.language == "fi") R.string.vocabulary_swedish_term else R.string.vocabulary_finnish_term), style = MaterialTheme.typography.labelLarge)
            StudyText(it, if (word.language == "fi") "sv" else "fi", MaterialTheme.typography.bodyLarge)
        }
    }
}
