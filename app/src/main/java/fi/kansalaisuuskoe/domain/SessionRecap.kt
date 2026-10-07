package fi.kansalaisuuskoe.domain

data class SessionAnswer(val question: Question, val attempt: QuestionAttempt)
data class SessionRecap(val answers: List<SessionAnswer> = emptyList(), val unavailableCount: Int = 0, val reviewAt: Long? = null) {
    val mistakes get() = answers.filter { !it.attempt.correct }
}

/** Use only this session's saved attempt IDs; never infer a lesson from lifetime totals. */
object SessionRecaps {
    fun summarize(questions: List<Question>, attempts: List<QuestionAttempt>, attemptIds: List<String>, progress: List<UserQuestionProgress>): SessionRecap {
        val current = questions.associateBy { it.id }
        val history = attempts.associateBy { it.id }
        val ids = attemptIds.distinct()
        val answers = ids.mapNotNull { id ->
            val attempt = history[id] ?: return@mapNotNull null
            val question = current[attempt.questionId] ?: return@mapNotNull null
            if (question.revision != attempt.revision || question.language != attempt.language ||
                question.options.none { it.id == attempt.selectedOptionId }) return@mapNotNull null
            SessionAnswer(question, attempt)
        }
        val revisions = answers.associate { it.question.id to it.question.revision }
        val reviewAt = progress.filter { revisions[it.questionId] == it.revision }.mapNotNull { it.nextReview }.minOrNull()
        return SessionRecap(answers, ids.size - answers.size, reviewAt)
    }
}
