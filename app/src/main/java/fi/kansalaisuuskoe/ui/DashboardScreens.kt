package fi.kansalaisuuskoe.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fi.kansalaisuuskoe.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import fi.kansalaisuuskoe.domain.*

@Composable
fun HomeScreen(state: AppUiState, onStudy: () -> Unit, onReview: () -> Unit, onTopics: () -> Unit, onTopic: (String) -> Unit, openSettings: () -> Unit, onAssessment: () -> Unit) {
    val summary = state.summary
    val plan = DailyLearningPlanner.plan(state.studySnapshot, state.now)
    val upToDate = plan.items.isEmpty()
    val language = topicLanguage()
    Page {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Heading(stringResource(R.string.home_title), stringResource(R.string.home_subtitle))
                Text(stringResource(state.contentStatus.labelResource()), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { TextButton(onClick = openSettings) { StudyScope(state) } }
        if (summary.totalQuestions == 0) {
            item { EmptyState(stringResource(R.string.language_empty_title), stringResource(R.string.language_empty_body), stringResource(R.string.settings_title), openSettings) }
            item { OutlinedButton(onAssessment, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.assessment_title)) } }
            item { ContentNotice(state.contentStatus) }
            return@Page
        }
        item { MasteryHero(summary.mastery, summary.studiedCount, summary.totalQuestions) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (upToDate) Text(stringResource(R.string.up_to_date), style = MaterialTheme.typography.titleMedium)
                Button(onClick = if (upToDate) onTopics else onStudy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(if (upToDate) stringResource(R.string.practice_topics) else if (summary.answered > 0) stringResource(R.string.continue_practice) else stringResource(R.string.start_practice))
                }
                Text(when {
                    upToDate -> plan.nextReviewAt?.let { stringResource(R.string.next_review_practice, formatReviewTime(it)) } ?: stringResource(R.string.practice_again)
                    plan.reviewCount == 0 -> stringResource(R.string.new_learning_count, pluralStringResource(R.plurals.question_count, plan.newCount, plan.newCount))
                    plan.newCount == 0 -> stringResource(R.string.daily_review_only, pluralStringResource(R.plurals.question_count, plan.reviewCount, plan.reviewCount))
                    else -> stringResource(R.string.daily_mix, pluralStringResource(R.plurals.daily_review_questions, plan.reviewCount, plan.reviewCount), pluralStringResource(R.plurals.daily_new_questions, plan.newCount, plan.newCount))
                }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                plan.focusTopicId?.let { id ->
                    state.studySnapshot.topics.find { it.id == id }?.let { topic ->
                        Text(stringResource(R.string.daily_focus_topic, topic.titleFor(language)), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (plan.remainingDue > 0) {
                    Text(stringResource(R.string.daily_reviews_remaining, pluralStringResource(R.plurals.question_count, plan.remainingDue, plan.remainingDue)), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onReview, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.daily_all_reviews, pluralStringResource(R.plurals.question_count, plan.totalDue, plan.totalDue))) }
                }
            }
        }
        item { OutlinedButton(onAssessment, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.assessment_title)) } }
        item { ContentNotice(state.contentStatus) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                StatRow(summary.dueCount.toString(), stringResource(R.string.reviews_due))
                StatRow(stringResource(R.string.day_count, summary.streak), stringResource(R.string.study_streak), stringResource(R.string.streak_note))
                StatRow(summary.answered.toString(), stringResource(R.string.answers_total))
            }
        }
        item { Heading(stringResource(R.string.home_topics), stringResource(R.string.topics_provisional)) }
        items(summary.topics, key = { it.topic.id }) { TopicRow(it) { onTopic(it.topic.id) } }
    }
}
@Composable
fun TopicsScreen(state: AppUiState, onVocabulary: () -> Unit, onAssessment: () -> Unit, onTopic: (String) -> Unit) {
    Page {
        item { Heading(stringResource(R.string.topics_title), stringResource(R.string.topics_subtitle)) }
        item { StudyScope(state) }
        if (state.summary.totalQuestions == 0) item { Text(stringResource(R.string.language_empty_body)) }
        item { OutlinedButton(onVocabulary, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.vocabulary_browser_title)) } }
        item { OutlinedButton(onAssessment, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.assessment_title)) } }
        item { ContentNotice(state.contentStatus) }
        item { Text(stringResource(R.string.topics_list_title), style = MaterialTheme.typography.titleMedium) }
        items(state.summary.topics, key = { it.topic.id }) { TopicRow(it) { onTopic(it.topic.id) } }
    }
}
@Composable
fun ReviewScreen(state: AppUiState, onReview: () -> Unit, onLearn: () -> Unit) {
    val language = topicLanguage()
    val due = LearningEngine.due(state.studySnapshot, state.now)
    val next = LearningEngine.nextReviewAt(state.studySnapshot, state.now)
    Page {
        item { Heading(stringResource(R.string.review_title), stringResource(R.string.review_subtitle)) }
        item { StudyScope(state) }
        item { ContentNotice(state.contentStatus, compact = true) }
        if (state.summary.totalQuestions == 0) {
            item { Text(stringResource(R.string.language_empty_body)) }
        } else if (due.isEmpty()) {
            item { EmptyState(stringResource(R.string.review_empty_title), if (next == null) stringResource(R.string.review_empty_body) else stringResource(R.string.next_review, formatReviewTime(next)), stringResource(R.string.practice_topics), onLearn) }
        } else {
            item { Button(onClick = onReview, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.start_review_count, pluralStringResource(R.plurals.question_count, due.size, due.size))) } }
            items(due, key = { it.id }) { q ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(state.snapshot.topics.find { it.id == q.topicId }?.titleFor(language).orEmpty(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(q.text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        item {
            Text(stringResource(R.string.review_rules), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable
fun ProgressScreen(state: AppUiState) {
    val s = state.summary
    val language = topicLanguage()
    Page {
        item { Heading(stringResource(R.string.progress_title), stringResource(R.string.progress_subtitle)) }
        item { StudyScope(state) }
        if (s.totalQuestions == 0) {
            item { Text(stringResource(R.string.language_empty_body)) }
            item { ContentNotice(state.contentStatus) }
            return@Page
        }
        item { MasteryHero(s.mastery, s.studiedCount, s.totalQuestions) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                StatRow(s.answered.toString(), stringResource(R.string.answers_total))
                StatRow(s.accuracy?.let { stringResource(R.string.percent_value, it) } ?: "–", stringResource(R.string.accuracy_title), if (s.accuracy == null) stringResource(R.string.accuracy_empty) else stringResource(R.string.accuracy_note))
                StatRow(s.dueCount.toString(), stringResource(R.string.reviews_due))
            }
        }
        if (s.weakest != null) {
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.strongest_topic), style = MaterialTheme.typography.labelLarge)
                        Text(s.strongest?.topic?.titleFor(language).orEmpty(), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.weakest_topic), style = MaterialTheme.typography.labelLarge)
                        Text(s.weakest.topic.titleFor(language), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.topic_comparison_note), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else item { Text(stringResource(R.string.topic_comparison_empty)) }
        item { Heading(stringResource(R.string.topic_mastery_title)) }
        items(s.topics, key = { it.topic.id }) { TopicRow(it) }
        item {
            Text(stringResource(R.string.mastery_rules), style = MaterialTheme.typography.bodySmall)
        }
        item { ContentNotice(state.contentStatus) }
    }
}

@Composable
internal fun StudyScope(state: AppUiState) {
    Text(stringResource(if (state.settings.studyLanguage == StudyLanguage.SWEDISH) R.string.question_language_swedish else R.string.question_language), style = MaterialTheme.typography.labelLarge)
}
