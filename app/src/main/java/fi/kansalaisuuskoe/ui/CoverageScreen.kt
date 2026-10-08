package fi.kansalaisuuskoe.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fi.kansalaisuuskoe.R
import fi.kansalaisuuskoe.data.InstalledContent
import fi.kansalaisuuskoe.domain.Coverage
import fi.kansalaisuuskoe.domain.ObjectiveCoverage
import fi.kansalaisuuskoe.domain.TopicCoverage

internal fun InstalledContent.coverage(): List<TopicCoverage> =
    Coverage.report(topics.map { it.model() }, objectives.map { it.model() }, questions.map { it.model() })

@Composable
internal fun thinSummary(count: Int) =
    pluralStringResource(R.plurals.coverage_thin_count, Coverage.MIN_QUESTIONS, Coverage.MIN_QUESTIONS, count)

/** An editor's map from objectives to installed questions; it records no learning activity. */
@Composable
internal fun CoverageScreen(state: ContentImportState, retry: () -> Unit) {
    val installed = state.installed
    val language = topicLanguage()
    Page {
        item { Heading(stringResource(R.string.coverage_title), stringResource(R.string.coverage_intro)) }
        if (installed == null) {
            item {
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().semanticsLabel(stringResource(R.string.loading_content)))
                else EmptyState(stringResource(R.string.coverage_title), state.error?.let { stringResource(it) }.orEmpty(), stringResource(R.string.retry), retry)
            }
            return@Page
        }
        val report = installed.coverage()
        if (report.isEmpty()) {
            item { Text(stringResource(R.string.coverage_empty)) }
            return@Page
        }
        val objectives = report.flatMap { it.objectives }
        item {
            Text(stringResource(R.string.coverage_summary, pluralStringResource(R.plurals.objective_count, objectives.size, objectives.size),
                thinSummary(objectives.count { it.thin })), style = MaterialTheme.typography.titleMedium)
        }
        report.forEach { topic ->
            item(key = "topic:${topic.topic.id}") {
                Text(topic.topic.titleFor(language), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            }
            items(topic.objectives, key = { "objective:${it.objective.id}" }) { ObjectiveRow(it, language) }
        }
    }
}

@Composable
private fun ObjectiveRow(item: ObjectiveCoverage, language: String) {
    val colour = if (item.thin) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(item.objective.statementFor(language), style = MaterialTheme.typography.bodyLarge)
        Text(item.questionCounts.entries.joinToString(" · ") { (code, count) -> "${code.uppercase()} $count" },
            style = MaterialTheme.typography.labelLarge, color = colour)
        // The words carry the warning; colour only repeats it.
        if (item.thin) Text(pluralStringResource(R.plurals.coverage_thin, Coverage.MIN_QUESTIONS, Coverage.MIN_QUESTIONS), style = MaterialTheme.typography.labelLarge, color = colour)
        Text(listOfNotNull(item.objective.id, item.objective.sourceRef).joinToString(" · "), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
