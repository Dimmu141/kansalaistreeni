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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** A topic counts as done on the path from the "almost there" mastery band. */
private const val PATH_MASTERED = 80

/** Home: streak and due chips, the "up next" card with the one primary action, then the topics as a path. */
@Composable
fun HomeScreen(state: AppUiState, onStudy: () -> Unit, onTopics: () -> Unit, openSettings: () -> Unit, onAssessment: () -> Unit, onTopic: (String) -> Unit = {}) {
    val summary = state.summary
    val plan = DailyLearningPlanner.plan(state.studySnapshot, state.now)
    val upToDate = plan.items.isEmpty()
    val language = topicLanguage()
    val c = Ks.colors
    Page {
        item { Heading(stringResource(R.string.home_title)) }
        if (summary.totalQuestions == 0) {
            item { EmptyState(stringResource(R.string.language_empty_title), stringResource(R.string.language_empty_body), stringResource(R.string.settings_title), openSettings) }
            return@Page
        }
        item {
            val masteryLabel = pluralStringResource(R.plurals.mastery_accessibility, summary.mastery, summary.mastery)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KsChip("${summary.mastery} %", ChipTone.Quiet, KsIcons.Chart, Modifier.clearAndSetSemantics { contentDescription = masteryLabel })
                if (summary.streak >= 1) KsChip(pluralStringResource(R.plurals.streak_chip, summary.streak, summary.streak), ChipTone.Birch, KsIcons.Flame)
                if (summary.dueCount > 0) KsChip(pluralStringResource(R.plurals.due_chip, summary.dueCount, summary.dueCount), ChipTone.Brand)
            }
        }
        // The path's current step: the first topic not yet mastered. Nothing is locked; every step opens its lesson.
        val current = summary.topics.indexOfFirst { it.mastery < PATH_MASTERED }
        val nextTopic = (plan.focusTopicId ?: plan.items.firstOrNull()?.question?.topicId)?.let { id -> summary.topics.find { it.topic.id == id } }
            ?: summary.topics.getOrNull(current)
        item {
            Surface(color = c.brand, contentColor = c.onBrand, shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().drawBehind { translate(top = 5.dp.toPx()) { drawRoundRect(c.brand.edge(), cornerRadius = CornerRadius(28.dp.toPx())) } }) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.home_next_up).uppercase(LocalConfiguration.current.locales[0]), style = KsType.overline, color = c.brandSoft)
                    nextTopic?.let { Text(it.topic.titleFor(language), style = KsType.headline.copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold)) }
                    Text(when {
                        upToDate -> plan.nextReviewAt?.let { stringResource(R.string.next_review_practice, formatReviewTime(it, state.now)) } ?: stringResource(R.string.practice_again)
                        plan.reviewCount == 0 -> stringResource(R.string.new_learning_count, pluralStringResource(R.plurals.question_count, plan.newCount, plan.newCount))
                        plan.newCount == 0 -> stringResource(R.string.daily_review_only, pluralStringResource(R.plurals.question_count, plan.reviewCount, plan.reviewCount))
                        else -> stringResource(R.string.daily_mix, pluralStringResource(R.plurals.daily_review_questions, plan.reviewCount, plan.reviewCount), pluralStringResource(R.plurals.daily_new_questions, plan.newCount, plan.newCount))
                    }, style = KsType.body, color = c.brandSoft)
                    plan.focusTopicId?.let { id ->
                        state.studySnapshot.topics.find { it.id == id }?.let { topic ->
                            Text(stringResource(R.string.daily_focus_topic, topic.titleFor(language)), style = KsType.body, color = c.brandSoft)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    KsButton(if (upToDate) stringResource(R.string.practice_topics) else if (summary.answered > 0) stringResource(R.string.continue_practice) else stringResource(R.string.start_practice),
                        if (upToDate) onTopics else onStudy, Modifier.fillMaxWidth(), tone = KsTone.Inverse)
                }
            }
        }
        // Above the path, so it stays near the top however long the path grows.
        item { KsButton(stringResource(R.string.assessment_title), onAssessment, Modifier.fillMaxWidth(), tone = KsTone.Outline) }
        item { Text(stringResource(R.string.home_path), style = KsType.title, modifier = Modifier.padding(top = 8.dp).semantics { heading() }) }
        itemsIndexed(summary.topics, key = { _, it -> "path-" + it.topic.id }) { index, progress ->
            PathStep(progress, index, index == current) { onTopic(progress.topic.id) }
        }
    }
}

/** One step of the home path, swinging left and right: a raised disc (check when mastered, play when current, else its number) and the topic beside it. */
@Composable
private fun PathStep(progress: TopicProgress, index: Int, current: Boolean, onClick: () -> Unit) {
    val c = Ks.colors
    val view = LocalView.current
    val mastered = progress.mastery >= PATH_MASTERED
    val interaction = remember { MutableInteractionSource() }
    val swing = listOf(8, 56, 96, 56)[index % 4].dp
    val size = if (current) 80.dp else 64.dp
    val (face, ink) = when {
        mastered -> c.correct to c.onCorrect
        current -> c.brand to c.onBrand
        else -> c.surface2 to c.inkMuted
    }
    Row(Modifier.fillMaxWidth().clickable(interaction, indication = null, role = Role.Button) { view.tapHaptic(); onClick() },
        verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(swing + if (current) 0.dp else 8.dp))
        // The disc reserves its edge below the face, so the face stays round.
        Box(Modifier.width(size).height(size + 6.dp)
            .then(if (current) Modifier.drawBehind { drawCircle(c.brandSoft, size.toPx() / 2 + 8.dp.toPx(), center.copy(y = size.toPx() / 2 + 3.dp.toPx())) } else Modifier)
            .pressEdge(interaction, if (mastered || current) face.edge() else c.line, CircleShape, depth = 6.dp)
            .background(face, CircleShape), contentAlignment = Alignment.Center) {
            when {
                mastered -> Icon(KsIcons.Check, null, Modifier.size(30.dp), tint = ink)
                current -> Icon(KsIcons.Play, null, Modifier.size(32.dp), tint = ink)
                else -> Text("${index + 1}", style = KsType.title.copy(fontWeight = FontWeight.ExtraBold), color = ink)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(progress.topic.titleFor(topicLanguage()), style = KsType.label.copy(fontWeight = FontWeight.ExtraBold))
            Text(when {
                // "Up next" belongs to the card above, which follows the daily plan rather than path order.
                mastered -> stringResource(R.string.path_mastered)
                progress.studiedCount > 0 -> stringResource(R.string.percent_value, progress.mastery)
                else -> stringResource(R.string.topic_new)
            }, style = KsType.caption, color = if (current) c.brand else c.inkMuted)
        }
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
            KsCard(onClick = onVocabulary) {
                Text(stringResource(R.string.vocabulary_browser_title), style = KsType.title)
                Text(stringResource(R.string.vocabulary_row_note), style = KsType.body, color = Ks.colors.inkMuted)
            }
        }
    }
}
@Composable
fun ReviewScreen(state: AppUiState, onReview: () -> Unit, onLearn: () -> Unit, onHard: () -> Unit) {
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
            item { KsButton(stringResource(R.string.start_review_count, pluralStringResource(R.plurals.question_count, due.size, due.size)), onReview, Modifier.fillMaxWidth()) }
            items(due, key = { it.id }) { q ->
                KsCard {
                    Text(state.snapshot.topics.find { it.id == q.topicId }?.titleFor(language).orEmpty(), style = KsType.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = Ks.colors.brand)
                    Text(q.text, style = KsType.option)
                }
            }
        }
        if (state.summary.totalQuestions > 0) item { HardQuestionsCard(state.studySnapshot.questions.count { it.id in state.marked }, onHard) }
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
        item { MasteryHero(s.mastery, s.studiedCount, s.totalQuestions, s.streak, s.dueCount) }
        item {
            KsCard {
                StatRow(s.answered.toString(), stringResource(R.string.answers_total))
                StatRow(s.accuracy?.let { stringResource(R.string.percent_value, it) } ?: "–", stringResource(R.string.accuracy_title), if (s.accuracy == null) stringResource(R.string.accuracy_empty) else stringResource(R.string.accuracy_note))
                StatRow(s.dueCount.toString(), stringResource(R.string.reviews_due))
                StatRow(stringResource(R.string.day_count, s.streak), stringResource(R.string.study_streak), stringResource(R.string.streak_note))
            }
        }
        if (compared && s.weakest != null) {
            item {
                KsCard {
                    Text(stringResource(R.string.strongest_topic), style = KsType.caption, color = Ks.colors.inkMuted)
                    Text(s.strongest?.topic?.titleFor(language).orEmpty(), style = KsType.title)
                    Text(stringResource(R.string.weakest_topic), style = KsType.caption, color = Ks.colors.inkMuted)
                    Text(s.weakest.topic.titleFor(language), style = KsType.title)
                    Text(stringResource(R.string.topic_comparison_note), style = MaterialTheme.typography.bodySmall, color = Ks.colors.inkMuted)
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

/** The learner's own list, separate from scheduled reviews: questions they chose to keep practising. */
@Composable
private fun HardQuestionsCard(count: Int, onPractice: () -> Unit) {
    KsCard {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(KsIcons.Bookmark, null, Modifier.size(20.dp), tint = Ks.colors.brand)
            Text(stringResource(R.string.hard_title), style = KsType.title)
        }
        Text(if (count == 0) stringResource(R.string.hard_empty) else stringResource(R.string.hard_note), style = KsType.body, color = Ks.colors.inkMuted)
        if (count > 0) KsButton(stringResource(R.string.hard_practice, pluralStringResource(R.plurals.question_count, count, count)), onPractice, Modifier.fillMaxWidth(), tone = KsTone.Tonal)
    }
}
