package fi.kansalaisuuskoe.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class LearningEngineTest {
    private val now = Instant.parse("2026-10-06T12:00:00Z").toEpochMilli()
    private val day = 86_400_000L
    private val question = Question("q1", 1, "society", "Question?", listOf(AnswerOption("a", "Right"), AnswerOption("b", "Wrong")), "a", "Explanation", null, emptyList(), Source("Source", "https://example.org"), "fi", Difficulty.EASY, emptyList(), ContentStatus.DEMO)
    private val topic = Topic("society", "Society", "Description", 0)
    private fun attempt(id: String, correct: Boolean, date: Long = now) = QuestionAttempt(id, "q1", 1, "society", if (correct) "a" else "b", correct, date)

    @Test fun `first correct answer persists counts and schedules tomorrow`() {
        val p = LearningEngine.answer(question, "a", null, now)
        assertEquals(1, p.attempts)
        assertEquals(1, p.correctAnswers)
        assertEquals(25, p.mastery)
        assertEquals(now, p.lastAnswered)
        assertEquals(now + day, p.nextReview)
    }
    @Test fun `wrong answer reduces mastery and returns in ten minutes`() {
        val initial = UserQuestionProgress("q1", 1, mastery = 50, consecutiveCorrect = 2)
        val p = LearningEngine.answer(question, "b", initial, now)
        assertEquals(15, p.mastery)
        assertEquals(0, p.consecutiveCorrect)
        assertEquals(0, p.correctAnswers)
        assertEquals(now + 600_000, p.nextReview)
    }
    @Test fun `mastery cannot go below zero or exceed one hundred`() {
        assertEquals(0, LearningEngine.answer(question, "b", null, now).mastery)
        assertEquals(100, LearningEngine.answer(question, "a", UserQuestionProgress("q1", 1, mastery = 100), now).mastery)
    }
    @Test fun `consecutive correct answers increase review interval`() {
        var p: UserQuestionProgress? = null
        var time = now
        listOf(1, 3, 7, 14, 14).forEach { interval ->
            p = LearningEngine.answer(question, "a", p, time)
            assertEquals(time + interval * day, p.nextReview)
            time = requireNotNull(p.nextReview)
        }
    }
    @Test fun `wrong answer resets the interval`() {
        val initial = UserQuestionProgress("q1", 1, consecutiveCorrect = 5, mastery = 100)
        val wrong = LearningEngine.answer(question, "b", initial, now)
        assertEquals(now + 600_000 + day, LearningEngine.answer(question, "a", wrong, now + 600_000).nextReview)
    }
    @Test fun `immediate correct repeats count without inflating mastery or postponing review`() {
        var p = LearningEngine.answer(question, "a", null, now)
        repeat(8) { p = LearningEngine.answer(question, "a", p, now + it + 1) }
        assertEquals(9, p.attempts)
        assertEquals(9, p.correctAnswers)
        assertEquals(25, p.mastery)
        assertEquals(1, p.consecutiveCorrect)
        assertEquals(now + day, p.nextReview)
        assertEquals(now + 8, p.lastAnswered)
    }
    @Test fun `mastery credit begins exactly at due time`() {
        val initial = LearningEngine.answer(question, "a", null, now)
        assertFalse(LearningEngine.canGainMastery(question, initial, now + day - 1))
        assertEquals(25, LearningEngine.answer(question, "a", initial, now + day - 1).mastery)
        assertTrue(LearningEngine.canGainMastery(question, initial, now + day))
        val reviewed = LearningEngine.answer(question, "a", initial, now + day)
        assertEquals(50, reviewed.mastery)
        assertEquals(now + 4 * day, reviewed.nextReview)
    }
    @Test fun `early correction after mistake preserves ten minute review`() {
        val wrong = LearningEngine.answer(question, "b", null, now)
        val correction = LearningEngine.answer(question, "a", wrong, now + 1)
        assertEquals(0, correction.mastery)
        assertEquals(0, correction.consecutiveCorrect)
        assertEquals(now + 600_000, correction.nextReview)
        assertEquals(2, correction.attempts)
        assertEquals(1, correction.correctAnswers)
    }
    @Test fun `early mistakes still lower mastery and reset interval`() {
        val previous = UserQuestionProgress("q1", 1, attempts = 4, mastery = 100, consecutiveCorrect = 4, nextReview = now + day)
        val wrong = LearningEngine.answer(question, "b", previous, now)
        assertEquals(65, wrong.mastery)
        assertEquals(0, wrong.consecutiveCorrect)
        assertEquals(now + 600_000, wrong.nextReview)
    }
    @Test fun `replay removes immediate inflation and preserves stable equal time ordering`() {
        val history = listOf(attempt("2", false, now + day), attempt("3", true, now + day), attempt("1", true), attempt("4", true, now + day + 600_000))
        // Timestamp sorting must retain insertion order: wrong first, then immediate correction.
        val ordered = LearningEngine.replayProgress(listOf(question), history).single()
        assertEquals(4, ordered.attempts)
        assertEquals(3, ordered.correctAnswers)
        assertEquals(25, ordered.mastery)
        assertEquals(now + 600_000 + 2 * day, ordered.nextReview)
        assertEquals(1, ordered.consecutiveCorrect)
    }
    @Test fun `replay excludes obsolete revisions and removed questions`() {
        val history = listOf(attempt("old", true), attempt("removed", true).copy(questionId = "gone"), attempt("new", true).copy(revision = 2))
        val p = LearningEngine.replayProgress(listOf(question.copy(revision = 2)), history).single()
        assertEquals(2, p.revision)
        assertEquals(1, p.attempts)
        assertEquals(25, p.mastery)
    }
    @Test fun `recommended lesson never fills spare slots with early practice`() {
        val qs = listOf(question, question.copy(id = "q2"))
        val p = LearningEngine.answer(question, "a", null, now)
        val snapshot = LearningSnapshot(questions = qs, progress = listOf(p))
        assertEquals(listOf("q2"), LearningEngine.recommendedQueue(snapshot, now).map { it.id })
        assertEquals(StudyRecommendation(RecommendationKind.NEW_QUESTIONS, 1, now + day), LearningEngine.recommendation(snapshot, now))
    }
    @Test fun `finished material recommends waiting and returns to review at due time`() {
        val p = LearningEngine.answer(question, "a", null, now)
        val snapshot = LearningSnapshot(questions = listOf(question), progress = listOf(p))
        assertTrue(LearningEngine.recommendedQueue(snapshot, now).isEmpty())
        assertEquals(StudyRecommendation(RecommendationKind.WAIT_FOR_REVIEW, 0, now + day), LearningEngine.recommendation(snapshot, now))
        assertEquals(StudyRecommendation(RecommendationKind.REVIEW, 1, null), LearningEngine.recommendation(snapshot, now + day))
    }
    @Test fun `recommended lesson uses oldest due then unseen and has five question limit`() {
        val qs = (1..8).map { question.copy(id = "q$it") }
        val ps = listOf(UserQuestionProgress("q1", 1, attempts = 1, nextReview = now - 1), UserQuestionProgress("q2", 1, attempts = 1, nextReview = now - day))
        val snapshot = LearningSnapshot(questions = qs, progress = ps)
        assertEquals(listOf("q2", "q1", "q3", "q4", "q5"), LearningEngine.recommendedQueue(snapshot, now).map { it.id })
        assertEquals(RecommendationKind.REVIEW, LearningEngine.recommendation(snapshot, now).kind)
    }
    @Test fun `obsolete review dates do not hide newly revised content`() {
        val ps = listOf(UserQuestionProgress("q1", 1, attempts = 1, nextReview = now + day), UserQuestionProgress("gone", 1, attempts = 1, nextReview = now + 1))
        val snapshot = LearningSnapshot(questions = listOf(question.copy(revision = 2)), progress = ps)
        assertNull(LearningEngine.nextReviewAt(snapshot, now))
        assertEquals(RecommendationKind.NEW_QUESTIONS, LearningEngine.recommendation(snapshot, now).kind)
    }
    @Test(expected = IllegalArgumentException::class) fun `unknown option is rejected`() {
        LearningEngine.answer(question, "missing", null, now)
    }
    @Test fun `new revision does not inherit old mastery`() {
        val old = UserQuestionProgress("q1", 1, attempts = 10, mastery = 100)
        val p = LearningEngine.answer(question.copy(revision = 2), "a", old, now)
        assertEquals(25, p.mastery)
        assertEquals(1, p.attempts)
    }
    @Test fun `other question progress cannot be reused`() {
        assertEquals(25, LearningEngine.answer(question, "a", UserQuestionProgress("other", 1, mastery = 100), now).mastery)
    }
    @Test fun `accuracy uses all attempts and rounds`() {
        val s = LearningEngine.summarize(LearningSnapshot(listOf(topic), listOf(question), attempts = listOf(attempt("1", true), attempt("2", true), attempt("3", false))), now)
        assertEquals(67, s.accuracy)
        assertEquals(3, s.answered)
    }
    @Test fun `empty progress has no fabricated accuracy or strongest topic`() {
        val s = LearningEngine.summarize(LearningSnapshot(listOf(topic), listOf(question)), now)
        assertEquals(0, s.mastery)
        assertNull(s.accuracy)
        assertNull(s.weakest)
        assertNull(s.strongest)
        assertEquals(0, s.streak)
    }
    @Test fun `topic and overall scores include unseen questions`() {
        val p = UserQuestionProgress("q1", 1, attempts = 1, mastery = 50)
        val s = LearningEngine.summarize(LearningSnapshot(listOf(topic), listOf(question, question.copy(id = "q2")), listOf(p)), now)
        assertEquals(25, s.mastery)
        assertEquals(25, s.topics.single().mastery)
        assertEquals(1, s.studiedCount)
        assertEquals(2, s.totalQuestions)
    }
    @Test fun `overall mastery is question weighted rather than topic weighted`() {
        val other = topic.copy(id = "history")
        val qs = listOf(question, question.copy(id = "q2"), question.copy(id = "q3", topicId = "history"))
        val s = LearningEngine.summarize(LearningSnapshot(listOf(topic, other), qs, listOf(UserQuestionProgress("q3", 1, attempts = 4, mastery = 100))), now)
        assertEquals(33, s.mastery)
        assertEquals("history", s.strongest!!.topic.id)
    }
    @Test fun `unseen questions are not counted as due reviews`() {
        assertTrue(LearningEngine.due(LearningSnapshot(questions = listOf(question)), now).isEmpty())
    }
    @Test fun `review becomes due exactly at scheduled time`() {
        val p = LearningEngine.answer(question, "b", null, now)
        val snapshot = LearningSnapshot(questions = listOf(question), progress = listOf(p))
        assertTrue(LearningEngine.due(snapshot, now + 599_999).isEmpty())
        assertEquals(1, LearningEngine.due(snapshot, now + 600_000).size)
    }
    @Test fun `removed or revised content is excluded from due and mastery`() {
        val p = UserQuestionProgress("q1", 1, attempts = 4, mastery = 100, nextReview = now)
        val snapshot = LearningSnapshot(listOf(topic), listOf(question.copy(revision = 2)), listOf(p))
        assertTrue(LearningEngine.due(snapshot, now).isEmpty())
        assertEquals(0, LearningEngine.summarize(snapshot, now).mastery)
    }
    @Test fun `queue prioritizes due then unseen then weakest`() {
        val qs = listOf(question, question.copy(id = "q2"), question.copy(id = "q3"))
        val ps = listOf(UserQuestionProgress("q1", 1, attempts = 1, mastery = 25, nextReview = now + day), UserQuestionProgress("q3", 1, attempts = 1, nextReview = now))
        assertEquals(listOf("q3", "q2", "q1"), LearningEngine.learningQueue(LearningSnapshot(questions = qs, progress = ps), now).map { it.id })
    }
    @Test fun `streak uses local calendar days and tolerates yesterday`() {
        val zone = ZoneId.of("Europe/Helsinki")
        val attempts = listOf(attempt("1", true, now - day), attempt("2", false, now - 2 * day), attempt("3", true, now - 2 * day))
        assertEquals(2, LearningEngine.studyStreak(attempts, now, zone))
        assertEquals(0, LearningEngine.studyStreak(attempts, now + day, zone))
    }
    @Test fun `timezone boundary does not split a local study day`() {
        val time = Instant.parse("2026-10-05T22:30:00Z").toEpochMilli()
        val answerTime = Instant.parse("2026-10-05T21:30:00Z").toEpochMilli()
        assertEquals(1, LearningEngine.studyStreak(listOf(attempt("1", true, answerTime)), time, ZoneId.of("Europe/Helsinki")))
    }
}
