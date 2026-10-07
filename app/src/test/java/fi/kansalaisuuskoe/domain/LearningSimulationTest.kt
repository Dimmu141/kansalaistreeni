package fi.kansalaisuuskoe.domain

import fi.kansalaisuuskoe.data.ContentCodec
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Rule invariants and counterexamples; these do not validate an exam prediction. */
class LearningSimulationTest {
    private val content = ContentCodec.parse(File("src/main/assets/demo-content.json").readText())
    private val questions = content.questions.filter { it.language == "fi" }.map { it.model() }
    private val topics = content.topics.map { it.model() }
    private val day = 86_400_000L
    private val start = 1_791_280_000_000L

    @Test fun `a perfect first pass is twenty five mastery even with perfect accuracy`() {
        val progress = questions.map { LearningEngine.answer(it, it.correctOptionId, null, start) }
        val attempts = questions.map { QuestionAttempt(it.id, it.id, it.revision, it.topicId, it.correctOptionId, true, start) }
        val summary = LearningEngine.summarize(LearningSnapshot(topics, questions, progress, attempts), start)
        assertEquals(25, summary.mastery)
        assertEquals(100, summary.accuracy)
        assertEquals(0, summary.dueCount)
    }
    @Test fun `one hundred immediate repeats cannot produce more delayed recall credit`() {
        var progress: UserQuestionProgress? = null
        val q = questions.first()
        repeat(100) { progress = LearningEngine.answer(q, q.correctOptionId, progress, start + it) }
        assertEquals(25, progress!!.mastery)
        assertEquals(start + day, progress.nextReview)
    }
    @Test fun `overdue score counterexample keeps material mastery distinct from readiness`() {
        val q = questions.first(); var now = start; var progress: UserQuestionProgress? = null
        repeat(4) { progress = LearningEngine.answer(q, q.correctOptionId, progress, now); now = progress.nextReview!! }
        val snapshot = LearningSnapshot(topics, listOf(q), listOf(progress!!))
        val late = LearningEngine.summarize(snapshot, now + 90 * day)
        assertEquals(100, late.mastery)
        assertEquals(1, late.dueCount)
        assertEquals(RecommendationKind.REVIEW, LearningEngine.recommendation(snapshot, now + 90 * day).kind)
    }
    @Test fun `random histories retain bounds correct counts scheduling and replay equivalence`() {
        val random = Random(20261006)
        repeat(30) { run ->
            val scores = linkedMapOf<String, UserQuestionProgress>()
            val attempts = mutableListOf<QuestionAttempt>()
            var now = start
            repeat(200) { index ->
                val q = questions[random.nextInt(questions.size)]
                val option = q.options[random.nextInt(q.options.size)].id
                val previous = scores[q.id]
                val eligible = LearningEngine.canGainMastery(q, previous, now)
                val updated = LearningEngine.answer(q, option, previous, now)
                assertTrue(updated.mastery in 0..100)
                assertTrue(updated.correctAnswers in 0..updated.attempts)
                if (option == q.correctOptionId && !eligible) {
                    assertEquals(previous!!.mastery, updated.mastery)
                    assertEquals(previous.nextReview, updated.nextReview)
                } else assertTrue(updated.nextReview!! > now)
                scores[q.id] = updated
                attempts += QuestionAttempt("$run-$index", q.id, q.revision, q.topicId, option, option == q.correctOptionId, now)
                now += listOf(0L, 1L, 600_000L, day, 7 * day)[random.nextInt(5)]
            }
            assertEquals(scores, LearningEngine.replayProgress(questions, attempts).associateBy { it.questionId })
            val snapshot = LearningSnapshot(topics, questions, scores.values.toList(), attempts)
            val summary = LearningEngine.summarize(snapshot, now)
            assertTrue(summary.mastery in 0..100)
            assertEquals(attempts.size, summary.answered)
            assertTrue(summary.accuracy!! in 0..100)
            assertEquals(LearningEngine.due(snapshot, now).size, summary.dueCount)
            assertTrue(LearningEngine.recommendedQueue(snapshot, now).distinctBy { it.id }.size <= 5)
        }
    }
}
