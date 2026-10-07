package fi.kansalaisuuskoe.domain

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class AssessmentTest {
    private val q = Question("q", 1, "one", "Question?", listOf(AnswerOption("yes", "Yes"), AnswerOption("no", "No")),
        "yes", "Explanation", null, emptyList(), Source("Source", "https://example.org"), "fi", Difficulty.EASY, emptyList(), ContentStatus.DEMO)
    private val topics = listOf(Topic("one", "One", "First", 0), Topic("two", "Two", "Second", 1))
    private val questions = listOf(q, q.copy(id = "second", topicId = "two"), q.copy(id = "third", topicId = "two"))
    private fun session() = AssessmentSession("s", "fi", 1000, null, topics, questions, emptyMap(), 0)
    private fun finished() = Assessments.finish(session().copy(answers = mapOf("q" to "yes", "second" to "no", "third" to "yes")), 2000)

    @Test fun `queue balances topics before taking more from a large topic`() {
        val pool = (0..25).map { q.copy(id = "large-$it") } + q.copy(id = "small", topicId = "two")
        val queue = Assessments.queue(pool, "fi", Random(123))
        assertEquals(20, queue.size)
        assertEquals(setOf("one", "two"), queue.take(2).map { it.topicId }.toSet())
        assertEquals(queue.size, queue.map { it.id }.distinct().size)
        assertEquals(queue, Assessments.queue(pool, "fi", Random(123)))
    }
    @Test fun `queue uses only requested language and handles empty and small material`() {
        val mixed = questions + q.copy(id = "swedish", language = "sv")
        assertEquals(3, Assessments.queue(mixed, "fi", Random(1)).size)
        assertEquals(listOf("swedish"), Assessments.queue(mixed, "sv").map { it.id })
        assertTrue(Assessments.queue(emptyList(), "fi").isEmpty())
    }
    @Test fun `answer can be changed without modifying the input session`() {
        val initial = session()
        val selected = Assessments.select(initial, "q", "no")
        val changed = Assessments.select(selected, "q", "yes")
        assertTrue(initial.answers.isEmpty())
        assertEquals(1, changed.answeredCount)
        assertEquals("yes", changed.answers["q"])
    }
    @Test(expected = IllegalArgumentException::class) fun `unknown option cannot be selected`() { Assessments.select(session(), "q", "invalid") }
    @Test(expected = IllegalArgumentException::class) fun `unanswered assessment cannot finish`() { Assessments.finish(session(), 2000) }
    @Test(expected = IllegalStateException::class) fun `results cannot be obtained before finishing`() { Assessments.result(session()) }
    @Test fun `results derive accuracy and topic counts from final choices`() {
        val result = Assessments.result(finished())
        assertEquals(2, result.correct); assertEquals(3, result.total); assertEquals(66, result.accuracy)
        assertEquals(listOf(1, 1), result.topics.map { it.correct })
        assertEquals(listOf(1, 2), result.topics.map { it.total })
        assertEquals(listOf("second"), result.mistakes.map { it.id })
    }
    @Test fun `finishing twice retains original result and timestamp`() {
        val session = finished()
        assertEquals(session, Assessments.finish(session, 9000))
    }
    @Test(expected = IllegalStateException::class) fun `finished choices are immutable`() { Assessments.select(finished(), "q", "no") }
    @Test fun `position can move in either direction`() {
        assertEquals(0, Assessments.move(Assessments.move(session(), 2), 0).position)
    }
    @Test(expected = IllegalArgumentException::class) fun `invalid position is rejected`() { Assessments.move(session(), 3) }
    @Test fun `follow up excludes removed revised and silently changed facts`() {
        val old = finished()
        assertTrue(Assessments.practiceMistakes(old, listOf(q)).isEmpty())
        assertTrue(Assessments.practiceMistakes(old, questions.map { it.copy(revision = 2) }).isEmpty())
        assertTrue(Assessments.practiceMistakes(old, questions.map { it.copy(text = "Changed?") }).isEmpty())
        assertEquals(listOf("second"), Assessments.practiceMistakes(old, questions).map { it.id })
    }
    @Test fun `supporting explanation update keeps native follow up eligible and uses current content`() {
        val updated = questions.map { it.copy(explanation = "New explanation", translations = mapOf("en" to QuestionTranslation("Question?", q.options, "English"))) }
        assertEquals(listOf(updated[1]), Assessments.practiceMistakes(finished(), updated))
        assertEquals("Explanation", finished().questions[1].explanation)
    }
}
