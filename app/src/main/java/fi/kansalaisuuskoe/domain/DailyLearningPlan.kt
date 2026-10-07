package fi.kansalaisuuskoe.domain

enum class DailyQuestionReason { DUE_REVIEW, WEAK_TOPIC, NEW_TOPIC, NEW_QUESTION }
data class DailyPlanItem(val question: Question, val reason: DailyQuestionReason)
data class DailyLearningPlan(val items: List<DailyPlanItem>, val totalDue: Int, val nextReviewAt: Long?) {
    val reviewCount get() = items.count { it.reason == DailyQuestionReason.DUE_REVIEW }
    val newCount get() = items.size - reviewCount
    val remainingDue get() = totalDue - reviewCount
    val focusTopicId get() = items.firstOrNull { it.reason == DailyQuestionReason.WEAK_TOPIC }?.question?.topicId
}

/** A short eligible session, not a daily quota or validated learning-outcome prediction. */
object DailyLearningPlanner {
    const val SESSION_SIZE = 5
    private const val REVIEW_SLOTS = 3

    fun plan(snapshot: LearningSnapshot, now: Long): DailyLearningPlan {
        val progress = snapshot.progress.associateBy { it.questionId }
        fun current(q: Question) = progress[q.id]?.takeIf { it.revision == q.revision && it.attempts > 0 }
        // Deadlines come first. At the same deadline, give the less-mastered question priority.
        val due = LearningEngine.due(snapshot, now).sortedWith(compareBy<Question> { current(it)?.nextReview }
            .thenBy { current(it)?.mastery ?: 0 })
        val groups = snapshot.questions.groupBy { it.topicId }
        val topicOrder = snapshot.topics.associate { it.id to it.order }
        val scores = groups.mapValues { (_, qs) -> qs.mapNotNull(::current).map { it.mastery }.takeIf { it.isNotEmpty() }?.average() }
        val lastStudy = groups.mapValues { (_, qs) -> qs.mapNotNull { current(it)?.lastAnswered }.maxOrNull() ?: Long.MIN_VALUE }
        val unseen = groups.mapValues { (_, qs) -> qs.filter { current(it) == null }.toMutableList() }.filterValues { it.isNotEmpty() }
        // Unseen items do not masquerade as proof of weakness. Compare only practised current revisions.
        val weak = unseen.keys.filter { scores[it]?.let { score -> score < 100.0 } == true }
            .sortedWith(compareBy<String> { scores[it] }.thenBy { lastStudy[it] }.thenBy { topicOrder[it] ?: Int.MAX_VALUE })
        val unexplored = unseen.keys.filter { scores[it] == null }.sortedBy { topicOrder[it] ?: Int.MAX_VALUE }
        val strong = unseen.keys.filter { scores[it] == 100.0 }.sortedWith(compareBy<String> { lastStudy[it] }.thenBy { topicOrder[it] ?: Int.MAX_VALUE })
        val topicSequence = mutableListOf<String>()
        // Reserve exposure to unexplored topics even when several familiar topics are weak.
        for (index in 0 until maxOf(weak.size, unexplored.size)) {
            weak.getOrNull(index)?.let(topicSequence::add)
            unexplored.getOrNull(index)?.let(topicSequence::add)
        }
        topicSequence += strong
        val newLimit = SESSION_SIZE - minOf(REVIEW_SLOTS, due.size)
        val newItems = mutableListOf<DailyPlanItem>()
        while (newItems.size < newLimit && unseen.values.any { it.isNotEmpty() }) {
            for (topic in topicSequence) {
                val candidates = unseen.getValue(topic)
                if (candidates.isNotEmpty() && newItems.size < newLimit) {
                    val reason = when {
                        scores[topic] == null -> DailyQuestionReason.NEW_TOPIC
                        topic in weak -> DailyQuestionReason.WEAK_TOPIC
                        else -> DailyQuestionReason.NEW_QUESTION
                    }
                    newItems += DailyPlanItem(candidates.removeAt(0), reason)
                }
            }
        }
        // Spare new-question slots go back to reviews; never fill them with early repetition.
        val reviews = due.take(SESSION_SIZE - newItems.size).map { DailyPlanItem(it, DailyQuestionReason.DUE_REVIEW) }
        return DailyLearningPlan(reviews + newItems, due.size, LearningEngine.nextReviewAt(snapshot, now))
    }
}
