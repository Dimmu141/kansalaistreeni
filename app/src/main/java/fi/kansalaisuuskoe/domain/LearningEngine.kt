package fi.kansalaisuuskoe.domain

import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/** A transparent practice heuristic, not a validated exam-readiness prediction. */
object LearningEngine {
    const val RULES_VERSION = 2
    private const val MINUTE = 60_000L
    private const val DAY = 86_400_000L

    fun answer(question: Question, optionId: String, previous: UserQuestionProgress?, now: Long): UserQuestionProgress {
        require(question.options.any { it.id == optionId }) { "Unknown answer option" }
        val old = previous?.takeIf { it.questionId == question.id && it.revision == question.revision }
            ?: UserQuestionProgress(question.id, question.revision)
        val correct = optionId == question.correctOptionId
        val eligible = canGainMastery(question, old, now)
        val streak = if (!correct) 0 else if (eligible) old.consecutiveCorrect + 1 else old.consecutiveCorrect
        val mastery = if (!correct) (old.mastery - 35).coerceAtLeast(0) else if (eligible) (old.mastery + 25).coerceAtMost(100) else old.mastery
        val delay = if (!correct) 10 * MINUTE else when (streak) {
            1 -> DAY
            2 -> 3 * DAY
            3 -> 7 * DAY
            else -> 14 * DAY
        }
        return old.copy(
            attempts = old.attempts + 1,
            correctAnswers = old.correctAnswers + if (correct) 1 else 0,
            consecutiveCorrect = streak,
            mastery = mastery,
            lastAnswered = now,
            nextReview = if (correct && !eligible) old.nextReview else now + delay,
        )
    }

    /** Extra practice is welcome, but immediate repetition is not evidence of delayed recall. */
    fun canGainMastery(question: Question, previous: UserQuestionProgress?, now: Long): Boolean {
        val current = previous?.takeIf { it.questionId == question.id && it.revision == question.revision }
        return current == null || current.attempts == 0 || current.nextReview == null || current.nextReview <= now
    }

    /** Stable timestamp order preserves insertion order for simultaneous attempts. */
    fun replayProgress(questions: List<Question>, attempts: List<QuestionAttempt>): List<UserQuestionProgress> {
        val current = questions.associateBy { it.id }
        val progress = linkedMapOf<String, UserQuestionProgress>()
        attempts.sortedBy { it.answeredAt }.forEach { attempt ->
            val question = current[attempt.questionId]?.takeIf { it.revision == attempt.revision } ?: return@forEach
            progress[question.id] = answer(question, attempt.selectedOptionId, progress[question.id], attempt.answeredAt)
        }
        return progress.values.toList()
    }

    fun nextReviewAt(snapshot: LearningSnapshot, now: Long): Long? {
        val revisions = snapshot.questions.associate { it.id to it.revision }
        return snapshot.progress.filter { revisions[it.questionId] == it.revision && it.attempts > 0 }
            .mapNotNull { it.nextReview }.filter { it > now }.minOrNull()
    }

    /** Share one policy between Home and its actual short lesson. */
    fun recommendedQueue(snapshot: LearningSnapshot, now: Long): List<Question> =
        DailyLearningPlanner.plan(snapshot, now).items.map { it.question }

    fun recommendation(snapshot: LearningSnapshot, now: Long): StudyRecommendation {
        val dueCount = due(snapshot, now).size
        return when {
            dueCount > 0 -> StudyRecommendation(RecommendationKind.REVIEW, dueCount, nextReviewAt(snapshot, now))
            recommendedQueue(snapshot, now).isNotEmpty() -> StudyRecommendation(RecommendationKind.NEW_QUESTIONS, recommendedQueue(snapshot, now).size, nextReviewAt(snapshot, now))
            else -> StudyRecommendation(RecommendationKind.WAIT_FOR_REVIEW, 0, nextReviewAt(snapshot, now))
        }
    }

    fun due(snapshot: LearningSnapshot, now: Long): List<Question> {
        val progress = snapshot.progress.associateBy { it.questionId }
        return snapshot.questions.filter { q ->
            progress[q.id]?.let { it.revision == q.revision && it.attempts > 0 && it.nextReview?.let { date -> date <= now } == true } == true
        }.sortedBy { progress[it.id]?.nextReview }
    }

    fun summarize(snapshot: LearningSnapshot, now: Long, zone: ZoneId = ZoneId.systemDefault()): LearningSummary {
        val progress = snapshot.progress.associateBy { it.questionId }
        val dueIds = due(snapshot, now).map { it.id }.toSet()
        fun current(q: Question) = progress[q.id]?.takeIf { it.revision == q.revision }
        val topics = snapshot.topics.sortedBy { it.order }.map { topic ->
            val questions = snapshot.questions.filter { it.topicId == topic.id }
            TopicProgress(
                topic, questions.size,
                questions.count { (current(it)?.attempts ?: 0) > 0 },
                if (questions.isEmpty()) 0 else questions.sumOf { current(it)?.mastery ?: 0 }.toDouble().div(questions.size).roundToInt(),
                questions.count { it.id in dueIds },
            )
        }
        val attempts = snapshot.attempts
        val activeTopics = topics.filter { it.studiedCount > 0 }
        return LearningSummary(
            mastery = if (snapshot.questions.isEmpty()) 0 else snapshot.questions.sumOf { current(it)?.mastery ?: 0 }.toDouble().div(snapshot.questions.size).roundToInt(),
            answered = attempts.size,
            accuracy = if (attempts.isEmpty()) null else (100.0 * attempts.count { it.correct } / attempts.size).roundToInt(),
            studiedCount = snapshot.questions.count { (current(it)?.attempts ?: 0) > 0 },
            totalQuestions = snapshot.questions.size,
            dueCount = dueIds.size,
            streak = studyStreak(attempts, now, zone),
            topics = topics,
            weakest = activeTopics.minByOrNull { it.mastery },
            strongest = activeTopics.maxByOrNull { it.mastery },
        )
    }

    fun studyStreak(attempts: List<QuestionAttempt>, now: Long, zone: ZoneId): Int {
        val dates = attempts.map { Instant.ofEpochMilli(it.answeredAt).atZone(zone).toLocalDate() }.toSet()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        var day = if (today in dates) today else today.minusDays(1)
        var count = 0
        while (day in dates) { count++; day = day.minusDays(1) }
        return count
    }

    /** Prioritize due review, then unseen questions, then the least mastered. */
    fun learningQueue(snapshot: LearningSnapshot, now: Long, topicId: String? = null): List<Question> {
        val scores = snapshot.progress.associateBy { it.questionId }
        val dueIds = due(snapshot, now).map { it.id }.toSet()
        return snapshot.questions.filter { topicId == null || it.topicId == topicId }
            .sortedWith(compareBy<Question> {
                when {
                    it.id in dueIds -> 0
                    (scores[it.id]?.takeIf { p -> p.revision == it.revision }?.attempts ?: 0) == 0 -> 1
                    else -> 2
                }
            }.thenBy { if (it.id in dueIds) scores[it.id]?.nextReview ?: 0L else 0L }
                .thenBy { scores[it.id]?.takeIf { p -> p.revision == it.revision }?.mastery ?: 0 })
    }
}
