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
) {
    val complete get() = finishedAt != null
    val answeredCount get() = questions.count { it.id in answers }
}

data class AssessmentTopicResult(val topic: Topic, val correct: Int, val total: Int)
data class AssessmentResult(val correct: Int, val total: Int, val topics: List<AssessmentTopicResult>, val mistakes: List<Question>) {
    val accuracy get() = if (total == 0) 0 else correct * 100 / total
}

/**
 * The single place that sets practice-assessment length and format mix. These are our practice
 * choices, not the official examination format.
 */
data class AssessmentMix(val questionCount: Int = 20, val trueFalseShare: Double = 0.3) {
    init { require(questionCount in 1..Assessments.MAX_QUESTIONS && trueFalseShare in 0.0..1.0) }
    companion object { val DEFAULT = AssessmentMix() }
}

object Assessments {
    // Upper bound for any practice assessment, including restored backups.
    const val MAX_QUESTIONS = 20

    /**
     * Round-robin shuffled topics avoid filling a short assessment with one large category. The
     * format share is a target: a missing format is filled from the other rather than shortening it.
     */
    fun queue(questions: List<Question>, language: String, random: Random = Random.Default, mix: AssessmentMix = AssessmentMix.DEFAULT): List<Question> {
        val pool = questions.filter { it.language == language }
        val total = minOf(mix.questionCount, pool.size)
        val trueFalse = pool.filter { it.format == QuestionFormat.TRUE_FALSE }
        val singleChoice = pool.filter { it.format != QuestionFormat.TRUE_FALSE }
        val trueFalseCount = (total * mix.trueFalseShare).roundToInt()
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

    fun select(session: AssessmentSession, questionId: String, optionId: String): AssessmentSession {
        check(!session.complete) { "Assessment already finished" }
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
        require(session.questions.isNotEmpty() && session.answeredCount == session.questions.size)
        require(session.questions.all { q -> q.options.any { it.id == session.answers[q.id] } })
        return session.copy(finishedAt = now)
    }

    /** Feedback is unavailable before completion, including through the domain API. */
    fun result(session: AssessmentSession): AssessmentResult {
        check(session.complete)
        val correct = session.questions.filter { session.answers[it.id] == it.correctOptionId }
        return AssessmentResult(correct.size, session.questions.size, session.topics.mapNotNull { topic ->
            val questions = session.questions.filter { it.topicId == topic.id }
            if (questions.isEmpty()) null else AssessmentTopicResult(topic, correct.count { it.topicId == topic.id }, questions.size)
        }, session.questions.filter { it !in correct })
    }

    /** A changed or removed item is never silently replaced by a different question. */
    fun practiceMistakes(session: AssessmentSession, current: List<Question>): List<Question> = result(session).mistakes.mapNotNull { old ->
        current.find { it.id == old.id && it.revision == old.revision && it.language == old.language &&
            it.topicId == old.topicId && it.text == old.text && it.options == old.options && it.correctOptionId == old.correctOptionId }
    }
}
