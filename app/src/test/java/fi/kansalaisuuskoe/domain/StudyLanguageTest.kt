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
        listOf(LearningEngine.answer(fi, fi.correctOptionId, null, now - 660_000), LearningEngine.answer(sv, sv.options.first().id, null, now - 660_000)),
        listOf(attempt(fi, true), attempt(sv, false)))

    @Test fun `each language has twelve demo questions and all six topics`() {
        StudyLanguage.entries.forEach {
            val scoped = snapshot().forLanguage(it)
            assertEquals(12, scoped.questions.size)
            assertEquals(6, scoped.topics.size)
            assertTrue(scoped.questions.all { q -> q.language == it.tag })
        }
    }
    @Test fun `accuracy and mastery do not leak between languages`() {
        val finnish = LearningEngine.summarize(snapshot().forLanguage(StudyLanguage.FINNISH), now)
        val swedish = LearningEngine.summarize(snapshot().forLanguage(StudyLanguage.SWEDISH), now)
        assertEquals(1, finnish.answered); assertEquals(100, finnish.accuracy); assertEquals(2, finnish.mastery)
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
        assertEquals(2, LearningEngine.summarize(scoped, now).topics.first().questionCount)
    }
    @Test fun `removed question history retains its language and accuracy`() {
        val scoped = snapshot().copy(questions = listOf(fi)).forLanguage(StudyLanguage.SWEDISH)
        val summary = LearningEngine.summarize(scoped, now)
        assertEquals(1, summary.answered); assertEquals(0, summary.accuracy)
        assertEquals(0, summary.totalQuestions); assertEquals(0, summary.mastery)
        assertTrue(scoped.topics.isEmpty()); assertTrue(scoped.progress.isEmpty())
    }
    @Test fun `revised questions lose current mastery but retain language history`() {
        val scoped = snapshot().copy(questions = listOf(sv.copy(revision = 2))).forLanguage(StudyLanguage.SWEDISH)
        val summary = LearningEngine.summarize(scoped, now)
        assertEquals(0, summary.studiedCount); assertEquals(1, summary.answered)
        assertTrue(LearningEngine.due(scoped, now).isEmpty())
        assertEquals(listOf(sv.id), LearningEngine.recommendedQueue(scoped, now).map { it.id })
    }
}
