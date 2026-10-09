package fi.kansalaisuuskoe.ui

import androidx.compose.foundation.clickable
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

/** Home keeps one heading, the mastery card, one primary and one secondary action. */
@Composable
fun HomeScreen(state: AppUiState, onStudy: () -> Unit, onTopics: () -> Unit, openSettings: () -> Unit, onAssessment: () -> Unit) {
    val summary = state.summary
    val plan = DailyLearningPlanner.plan(state.studySnapshot, state.now)
    val upToDate = plan.items.isEmpty()
    val language = topicLanguage()
    Page {
        item { Heading(stringResource(R.string.home_title)) }
        if (summary.totalQuestions == 0) {
            item { EmptyState(stringResource(R.string.language_empty_title), stringResource(R.string.language_empty_body), stringResource(R.string.settings_title), openSettings) }
            return@Page
        }
        item { MasteryHero(summary.mastery, summary.studiedCount, summary.totalQuestions) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = if (upToDate) onTopics else onStudy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(if (upToDate) stringResource(R.string.practice_topics) else if (summary.answered > 0) stringResource(R.string.continue_practice) else stringResource(R.string.start_practice))
                }
                Text(when {
                    upToDate -> plan.nextReviewAt?.let { stringResource(R.string.next_review_practice, formatReviewTime(it, state.now)) } ?: stringResource(R.string.practice_again)
                    plan.reviewCount == 0 -> stringResource(R.string.new_learning_count, pluralStringResource(R.plurals.question_count, plan.newCount, plan.newCount))
                    plan.newCount == 0 -> stringResource(R.string.daily_review_only, pluralStringResource(R.plurals.question_count, plan.reviewCount, plan.reviewCount))
                    else -> stringResource(R.string.daily_mix, pluralStringResource(R.plurals.daily_review_questions, plan.reviewCount, plan.reviewCount), pluralStringResource(R.plurals.daily_new_questions, plan.newCount, plan.newCount))
                }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                plan.focusTopicId?.let { id ->
                    state.studySnapshot.topics.find { it.id == id }?.let { topic ->
                        Text(stringResource(R.string.daily_focus_topic, topic.titleFor(language)), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        item { OutlinedButton(onAssessment, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.assessment_title)) } }
    }
}

/** The topic list comes first; vocabulary is one row at the end. Practice assessments start from Home. */
@Composable
fun TopicsScreen(state: AppUiState, onVocabulary: () -> Unit, onTopic: (String) -> Unit) {
    Page {
        item { Heading(stringResource(R.string.topics_title)) }
        if (state.summary.totalQuestions == 0) item { Text(stringResource(R.string.language_empty_body)) }
        items(state.summary.topics, key = { it.topic.id }) { TopicRow(it) { onTopic(it.topic.id) } }
        item {
            ListItem(headlineContent = { Text(stringResource(R.string.vocabulary_browser_title)) },
                supportingContent = { Text(stringResource(R.string.vocabulary_row_note)) },
                modifier = Modifier.fillMaxWidth().clickable(onClick = onVocabulary))
        }
    }
}
@Composable
fun ReviewScreen(state: AppUiState, onReview: () -> Unit, onLearn: () -> Unit) {
    val language = topicLanguage()
    val due = LearningEngine.due(state.studySnapshot, state.now)
    val next = LearningEngine.nextReviewAt(state.studySnapshot, state.now)
    Page {
        item { Heading(stringResource(R.string.review_title), stringResource(R.string.review_subtitle)) }
        if (state.summary.totalQuestions == 0) {
            item { Text(stringResource(R.string.language_empty_body)) }
        } else if (due.isEmpty()) {
            item { EmptyState(stringResource(R.string.review_empty_title), if (next == null) stringResource(R.string.review_empty_body) else stringResource(R.string.next_review, formatReviewTime(next, state.now)), stringResource(R.string.practice_topics), onLearn) }
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
    // A strongest/weakest comparison means nothing until two topics have been practised.
    val compared = s.topics.count { it.studiedCount > 0 } >= 2
    Page {
        item { Heading(stringResource(R.string.progress_title), stringResource(R.string.progress_subtitle)) }
        if (s.totalQuestions == 0) {
            item { Text(stringResource(R.string.language_empty_body)) }
            return@Page
        }
        item { MasteryHero(s.mastery, s.studiedCount, s.totalQuestions) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                StatRow(s.answered.toString(), stringResource(R.string.answers_total))
                StatRow(s.accuracy?.let { stringResource(R.string.percent_value, it) } ?: "–", stringResource(R.string.accuracy_title), if (s.accuracy == null) stringResource(R.string.accuracy_empty) else stringResource(R.string.accuracy_note))
                StatRow(s.dueCount.toString(), stringResource(R.string.reviews_due))
                StatRow(stringResource(R.string.day_count, s.streak), stringResource(R.string.study_streak), stringResource(R.string.streak_note))
            }
        }
        if (compared && s.weakest != null) {
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
        }
        item { Heading(stringResource(R.string.topic_mastery_title)) }
        items(s.topics, key = { it.topic.id }) { TopicRow(it) }
        item {
            Text(stringResource(R.string.mastery_rules), style = MaterialTheme.typography.bodySmall)
        }
    }
}
