package fi.kansalaisuuskoe.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import fi.kansalaisuuskoe.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import fi.kansalaisuuskoe.domain.TopicProgress
import fi.kansalaisuuskoe.domain.ContentStatus

internal fun ContentStatus.labelResource() = when (this) {
    ContentStatus.DEMO -> R.string.status_demo
    ContentStatus.EDITORIAL -> R.string.status_editorial
    ContentStatus.OFFICIALLY_SOURCED -> R.string.status_sourced
    ContentStatus.OFFICIAL -> R.string.status_official
}

@Composable
fun Page(state: LazyListState = rememberLazyListState(), content: LazyListScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
        LazyColumn(
            state = state,
            modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            content = content,
        )
    }
}
@Composable
fun Heading(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable
fun ContentNotice(status: ContentStatus, compact: Boolean = false) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(when (status) {
                ContentStatus.DEMO -> stringResource(R.string.notice_demo_title)
                ContentStatus.EDITORIAL -> stringResource(R.string.notice_editorial_title)
                ContentStatus.OFFICIALLY_SOURCED -> stringResource(R.string.notice_sourced_title)
                ContentStatus.OFFICIAL -> stringResource(R.string.notice_official_title)
            }, style = MaterialTheme.typography.labelLarge)
            if (!compact) Text(when (status) {
                ContentStatus.DEMO -> stringResource(R.string.notice_demo_body)
                ContentStatus.EDITORIAL -> stringResource(R.string.notice_editorial_body)
                ContentStatus.OFFICIALLY_SOURCED -> stringResource(R.string.notice_sourced_body)
                ContentStatus.OFFICIAL -> stringResource(R.string.notice_official_body)
            }, style = MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable
fun MasteryHero(percent: Int, studied: Int, total: Int) {
    val masteryLabel = pluralStringResource(R.plurals.mastery_accessibility, percent, percent)
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.mastery_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.percent_value, percent), style = MaterialTheme.typography.displayMedium, modifier = Modifier.semantics { contentDescription = masteryLabel })
            LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth().height(8.dp))
            Text(pluralStringResource(R.plurals.studied_count, total, studied, total), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.mastery_note), style = MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable
fun TopicRow(progress: TopicProgress, onClick: (() -> Unit)? = null) {
    val language = LocalConfiguration.current.locales[0].language
    val body: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(progress.topic.titleFor(language), style = MaterialTheme.typography.titleMedium)
            Text(progress.topic.descriptionFor(language), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(progress = { progress.mastery / 100f }, modifier = Modifier.fillMaxWidth().height(6.dp))
            Text(stringResource(R.string.topic_progress, progress.mastery, progress.studiedCount, progress.questionCount), style = MaterialTheme.typography.labelLarge)
        }
    }
    if (onClick != null) OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), content = { body() })
    else OutlinedCard(modifier = Modifier.fillMaxWidth(), content = { body() })
}
@Composable
fun StatRow(value: String, label: String, detail: String? = null) {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
        detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable
fun EmptyState(title: String, message: String, actionLabel: String, onAction: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Heading(title, message)
        Button(onClick = onAction, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(actionLabel) }
    }
}
