package fi.kansalaisuuskoe.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import fi.kansalaisuuskoe.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import fi.kansalaisuuskoe.domain.TopicProgress
import fi.kansalaisuuskoe.domain.ContentStatus

internal fun ContentStatus.labelResource() = when (this) {
    ContentStatus.DEMO -> R.string.status_demo
    ContentStatus.EDITORIAL -> R.string.status_editorial
    ContentStatus.OFFICIALLY_SOURCED -> R.string.status_sourced
    ContentStatus.OFFICIAL -> R.string.status_official
}

@Composable
fun Page(state: LazyListState = rememberLazyListState(), bottomInset: Dp = 0.dp, content: LazyListScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
        LazyColumn(
            state = state,
            modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
            // A docked bottom element already includes any unconsumed navigation bar inset.
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp,
                bottom = 16.dp + if (bottomInset > 0.dp) bottomInset else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            content = content,
        )
    }
}
@Composable
fun Heading(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = KsType.headline, modifier = Modifier.semantics { heading() })
        subtitle?.let { Text(it, style = KsType.body, color = Ks.colors.inkMuted) }
    }
}
@Composable
fun ContentNotice(status: ContentStatus, compact: Boolean = false) {
    Surface(color = Ks.colors.surface2, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
            }, style = MaterialTheme.typography.bodyMedium, color = Ks.colors.inkMuted)
        }
    }
}
/** The Home card: a mastery ring, encouragement that scales with mastery, the studied count and streak/review chips. */
@Composable
fun MasteryHero(percent: Int, studied: Int, total: Int, streak: Int = 0, due: Int = 0) {
    val masteryLabel = pluralStringResource(R.plurals.mastery_accessibility, percent, percent)
    val c = Ks.colors
    Surface(color = c.brandSoft, contentColor = c.onBrandSoft, shape = MaterialTheme.shapes.large) {
        Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            MasteryRing(percent, Modifier.clearAndSetSemantics { contentDescription = masteryLabel })
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(when {
                    percent == 0 -> R.string.mastery_title_start
                    percent < 40 -> R.string.mastery_title_early
                    percent < 80 -> R.string.mastery_title_pace
                    else -> R.string.mastery_title_almost
                }), style = KsType.title)
                Text(pluralStringResource(R.plurals.studied_count, total, studied, total), style = KsType.body)
                if (streak >= 2 || due > 0) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (streak >= 2) KsChip(pluralStringResource(R.plurals.streak_chip, streak, streak), ChipTone.Birch, KsIcons.Flame)
                    if (due > 0) KsChip(pluralStringResource(R.plurals.due_chip, due, due), ChipTone.Brand)
                }
            }
        }
    }
}
/** A tappable topic: title with mastery top-right (or "New"), one-line description, a mastery bar and the count. */
@Composable
fun TopicRow(progress: TopicProgress, onClick: (() -> Unit)? = null) {
    val language = topicLanguage()
    val started = progress.studiedCount > 0
    val label = stringResource(R.string.topic_progress, progress.mastery, progress.studiedCount, progress.questionCount)
    KsCard(onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(progress.topic.titleFor(language), style = KsType.title, modifier = Modifier.weight(1f))
            if (started) Text(stringResource(R.string.percent_value, progress.mastery), style = KsType.caption.copy(fontFeatureSettings = "tnum"), color = Ks.colors.inkMuted)
            else KsChip(stringResource(R.string.topic_new))
        }
        Text(progress.topic.descriptionFor(language), style = KsType.body, color = Ks.colors.inkMuted)
        ProgressBar(progress.mastery / 100f)
        if (started) Text(stringResource(R.string.topic_studied, progress.studiedCount, progress.questionCount), style = KsType.caption, color = Ks.colors.inkMuted,
            modifier = Modifier.semantics { contentDescription = label })
    }
}
@Composable
fun StatRow(value: String, label: String, detail: String? = null) {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(value, style = KsType.verdict.copy(fontFeatureSettings = "tnum"), color = Ks.colors.brand)
        }
        detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable
fun EmptyState(title: String, message: String, actionLabel: String, onAction: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Heading(title, message)
        KsButton(actionLabel, onAction, Modifier.fillMaxWidth())
    }
}

/** The study language on detail screens without the app bar switch, such as the assessment list. */
@Composable
internal fun StudyScope(state: AppUiState) {
    KsChip(stringResource(if (state.settings.studyLanguage == fi.kansalaisuuskoe.domain.StudyLanguage.SWEDISH) R.string.question_language_swedish else R.string.question_language), ChipTone.Brand)
}
