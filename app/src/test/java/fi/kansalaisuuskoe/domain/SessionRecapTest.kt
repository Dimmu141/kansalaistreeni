package fi.kansalaisuuskoe.domain

import org.junit.Assert.*
import org.junit.Test

class SessionRecapTest {
    private val q = Question("q", 1, "t", "Question", listOf(AnswerOption("a", "Yes"), AnswerOption("b", "No")), "a", "Explanation", null, emptyList(), Source("Source", "https://example.org"), "fi", Difficulty.EASY, emptyList(), ContentStatus.DEMO)
    private fun attempt(id: String, correct: Boolean = true) = QuestionAttempt(id, q.id, q.revision, q.topicId, if (correct) "a" else "b", correct, 1000)

    @Test fun `recap follows session order and excludes lifetime attempts`() {
        val history = listOf(attempt("unrelated", false), attempt("second", false), attempt("first"))
        val recap = SessionRecaps.summarize(listOf(q), history, listOf("first", "second"), emptyList())
        assertEquals(listOf("first", "second"), recap.answers.map { it.attempt.id })
        assertEquals(listOf("second"), recap.mistakes.map { it.attempt.id })
        assertEquals(0, recap.unavailableCount)
    }
    @Test fun `retry token is shown only once`() {
        val recap = SessionRecaps.summarize(listOf(q), listOf(attempt("one", false)), listOf("one", "one"), emptyList())
        assertEquals(1, recap.answers.size)
        assertEquals(1, recap.mistakes.size)
    }
    @Test fun `changed removed or missing answers are counted as unavailable`() {
        val history = listOf(attempt("changed").copy(revision = 2), attempt("removed").copy(questionId = "gone"), attempt("option").copy(selectedOptionId = "gone"))
        val recap = SessionRecaps.summarize(listOf(q), history, listOf("changed", "removed", "option", "missing"), emptyList())
        assertTrue(recap.answers.isEmpty())
        assertEquals(4, recap.unavailableCount)
    }
    @Test fun `other study language cannot leak into recap`() {
        val recap = SessionRecaps.summarize(listOf(q), listOf(attempt("sv").copy(language = "sv")), listOf("sv"), emptyList())
        assertTrue(recap.answers.isEmpty())
        assertEquals(1, recap.unavailableCount)
    }
    @Test fun `review time considers only matching session question revisions`() {
        val progress = listOf(UserQuestionProgress("other", 1, nextReview = 1), UserQuestionProgress(q.id, 2, nextReview = 2), UserQuestionProgress(q.id, 1, nextReview = 600_000))
        val recap = SessionRecaps.summarize(listOf(q), listOf(attempt("one")), listOf("one"), progress)
        assertEquals(600_000L, recap.reviewAt)
    }
    @Test fun `empty lesson has no invented results or review date`() {
        val recap = SessionRecaps.summarize(listOf(q), listOf(attempt("one")), emptyList(), listOf(UserQuestionProgress(q.id, 1, nextReview = 10)))
        assertEquals(SessionRecap(), recap)
    }
}
