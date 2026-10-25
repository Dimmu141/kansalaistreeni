package fi.kansalaisuuskoe.domain

import kotlin.math.roundToInt
import kotlin.random.Random

/** Local practice assessment, never an official exam specification or a mastery attempt. */
data class AssessmentSession(
    val id: String,
    val language: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val topics: List<Topic>,
    val questions: List<Question>,
    val answers: Map<String, String>,
    val position: Int,
    /** The learner's own optional practice time limit, never an official exam rule. */
    val timeLimitMinutes: Int? = null,
) {
    val complete get() = finishedAt != null
    val answeredCount get() = questions.count { it.id in answers }
    val deadline get() = timeLimitMinutes?.let { startedAt + it * 60_000L }
    /** Time runs on the wall clock, including while the app is closed, like a sitting exam. */
    fun expired(now: Long) = deadline?.let { now >= it } == true
}

data class AssessmentTopicResult(val topic: Topic, val correct: Int, val total: Int)
data class AssessmentResult(val correct: Int, val total: Int, val topics: List<AssessmentTopicResult>, val mistakes: List<Question>, val unanswered: Int = 0) {
    val accuracy get() = if (total == 0) 0 else correct * 100 / total
}

object Assessments {
    /** Lengths the learner can choose; the profile's own length is always one of them. */
    val LENGTHS get() = (listOf(20, 30, 40) + ExamProfile.CURRENT.questionCount).distinct().sorted()
    const val MINUTES_PER_QUESTION = 1
    // Upper bound for any practice assessment, including restored backups. It never drops below the
    // longest offered length, so saved sessions stay restorable when the profile changes.
    val MAX_QUESTIONS get() = LENGTHS.max()

    /**
     * Round-robin shuffled topics avoid filling a short assessment with one large category. The
     * format share is a target: a missing format is filled from the other rather than shortening it.
     */
    fun queue(questions: List<Question>, language: String, random: Random = Random.Default, profile: ExamProfile = ExamProfile.CURRENT,
              count: Int = profile.questionCount): List<Question> {
        require(count > 0)
        val pool = questions.filter { it.language == language }
        val total = minOf(count, pool.size)
        val trueFalse = pool.filter { it.format == QuestionFormat.TRUE_FALSE }
        val singleChoice = pool.filter { it.format != QuestionFormat.TRUE_FALSE }
        val trueFalseCount = (total * profile.trueFalseShare).roundToInt()
            .coerceAtMost(trueFalse.size).coerceAtLeast(total - singleChoice.size)
        val selected = balanced(trueFalse, trueFalseCount, random) + balanced(singleChoice, total - trueFalseCount, random)
        // Interleave the chosen items by topic again so formats and topics are both spread out.
        return balanced(selected, total, random)
    }

    private fun balanced(questions: List<Question>, limit: Int, random: Random): List<Question> {
        val groups = questions.groupBy { it.topicId }.values.map { it.shuffled(random).toMutableList() }.shuffled(random)
        val selected = mutableListOf<Question>()
        while (selected.size < limit && groups.any { it.isNotEmpty() }) {
            for (group in groups) {
                if (group.isNotEmpty() && selected.size < limit) selected += group.removeAt(0)
            }
        }
        return selected
    }

    fun select(session: AssessmentSession, questionId: String, optionId: String, now: Long? = null): AssessmentSession {
        check(!session.complete) { "Assessment already finished" }
        check(now == null || !session.expired(now)) { "Time limit reached" }
        val question = requireNotNull(session.questions.find { it.id == questionId })
        require(question.options.any { it.id == optionId })
        return session.copy(answers = session.answers + (questionId to optionId))
    }

    fun move(session: AssessmentSession, position: Int): AssessmentSession {
        check(!session.complete)
        require(position in session.questions.indices)
        return session.copy(position = position)
    }

    fun finish(session: AssessmentSession, now: Long): AssessmentSession {
        if (session.complete) return session
        // Running out of time finishes the attempt as it is; unanswered questions then count as wrong.
        val expired = session.expired(now)
        require(session.questions.isNotEmpty() && (expired || session.answeredCount == session.questions.size))
        require(session.questions.all { q -> session.answers[q.id]?.let { a -> q.options.any { it.id == a } } ?: expired })
        return session.copy(finishedAt = now)
    }

    /** Feedback is unavailable before completion, including through the domain API. */
    fun result(session: AssessmentSession): AssessmentResult {
        check(session.complete)
        val correct = session.questions.filter { session.answers[it.id] == it.correctOptionId }
        return AssessmentResult(correct.size, session.questions.size, session.topics.mapNotNull { topic ->
            val questions = session.questions.filter { it.topicId == topic.id }
            if (questions.isEmpty()) null else AssessmentTopicResult(topic, correct.count { it.topicId == topic.id }, questions.size)
        }, session.questions.filter { it !in correct }, session.questions.count { it.id !in session.answers })
    }

    /** A changed or removed item is never silently replaced by a different question. */
    fun practiceMistakes(session: AssessmentSession, current: List<Question>): List<Question> = result(session).mistakes.mapNotNull { old ->
        current.find { it.id == old.id && it.revision == old.revision && it.language == old.language &&
            it.text == old.text && it.options == old.options && it.correctOptionId == old.correctOptionId }
    }
}
