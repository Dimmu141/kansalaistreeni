package fi.kansalaisuuskoe.domain

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import fi.kansalaisuuskoe.data.ContentCodec

class StudyLanguageTest {
    private val content = ContentCodec.parse(File("src/main/assets/demo-content.json").readText())
    private val fi = content.questions.first().model()
    private val sv = content.questions.first { it.language == "sv" }.model()
    private val now = 1_000_000L
    private fun attempt(q: Question, correct: Boolean) = QuestionAttempt(q.id, q.id, q.revision, q.topicId,
        if (correct) q.correctOptionId else q.options.first { it.id != q.correctOptionId }.id, correct, now - 660_000, q.language)
    private fun snapshot() = LearningSnapshot(content.topics.map { it.model() }, content.questions.map { it.model() },
        listOf(LearningEngine.answer(fi, fi.correctOptionId, null, now - 660_000), LearningEngine.answer(sv, sv.options.first { it.id != sv.correctOptionId }.id, null, now - 660_000)),
        listOf(attempt(fi, true), attempt(sv, false)))

    @Test fun `each language has its own questions in every topic`() {
        StudyLanguage.entries.forEach {
            val scoped = snapshot().forLanguage(it)
            assertEquals(200, scoped.questions.size)
            assertEquals(scoped.questions.map { q -> q.topicId }.toSet(), scoped.topics.map { t -> t.id }.toSet())
            assertEquals(8, scoped.topics.size)
            assertTrue(scoped.questions.all { q -> q.language == it.tag })
        }
    }
    @Test fun `accuracy and mastery do not leak between languages`() {
        // Mastery averages over every question in scope, so keep only the two answered questions.
        val pair = snapshot().copy(questions = listOf(fi, sv))
        val finnish = LearningEngine.summarize(pair.forLanguage(StudyLanguage.FINNISH), now)
        val swedish = LearningEngine.summarize(pair.forLanguage(StudyLanguage.SWEDISH), now)
        assertEquals(1, finnish.answered); assertEquals(100, finnish.accuracy); assertEquals(25, finnish.mastery)
        assertEquals(1, swedish.answered); assertEquals(0, swedish.accuracy); assertEquals(0, swedish.mastery)
        assertEquals(1, finnish.studiedCount); assertEquals(1, swedish.studiedCount)
    }
    @Test fun `only the selected language enters due review`() {
        assertTrue(LearningEngine.due(snapshot().forLanguage(StudyLanguage.FINNISH), now).isEmpty())
        assertEquals(listOf(sv.id), LearningEngine.due(snapshot().forLanguage(StudyLanguage.SWEDISH), now).map { it.id })
    }
    @Test fun `recommendations and topic practice never fall back to the other language`() {
        val scoped = snapshot().forLanguage(StudyLanguage.SWEDISH)
        assertTrue(LearningEngine.recommendedQueue(scoped, now).all { it.language == "sv" })
        assertTrue(LearningEngine.learningQueue(scoped, now, "democracy").all { it.language == "sv" })
        assertEquals(25, LearningEngine.summarize(scoped, now).topics.first().questionCount)
    }
    @Test fun `removed question history retains its language and accuracy`() {
        val scoped = snapshot().copy(questions = listOf(fi)).forLanguage(StudyLanguage.SWEDISH)
        val summary = LearningEngine.summarize(scoped, now)
        assertEquals(1, summary.answered); assertEquals(0, summary.accuracy)
        assertEquals(0, summary.totalQuestions); assertEquals(0, summary.mastery)
        assertTrue(scoped.topics.isEmpty()); assertTrue(scoped.progress.isEmpty())
    }
    @Test fun `revised questions lose current mastery but retain language history`() {
        val scoped = snapshot().copy(questions = listOf(sv.copy(revision = sv.revision + 1))).forLanguage(StudyLanguage.SWEDISH)
        val summary = LearningEngine.summarize(scoped, now)
        assertEquals(0, summary.studiedCount); assertEquals(1, summary.answered)
        assertTrue(LearningEngine.due(scoped, now).isEmpty())
        assertEquals(listOf(sv.id), LearningEngine.recommendedQueue(scoped, now).map { it.id })
    }
}
