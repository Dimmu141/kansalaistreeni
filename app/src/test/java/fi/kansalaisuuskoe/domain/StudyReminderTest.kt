package fi.kansalaisuuskoe.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class StudyReminderTest {
    private val zone = ZoneId.of("Europe/Helsinki")
    private fun at(month: Int, day: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()
    private val now = at(11, 2, 18)
    private val q = Question("q", 1, "t", "Question?", listOf(AnswerOption("yes", "Yes"), AnswerOption("no", "No")), "yes",
        "Explanation", null, emptyList(), Source("Source", "https://example.org"), "fi", Difficulty.EASY, emptyList(), ContentStatus.DEMO)
    private val topics = listOf(Topic("t", "Topic", "Description", 1))
    private fun attempt(question: Question, at: Long) = QuestionAttempt("a-${question.id}-$at", question.id, 1, "t", "yes", true, at, question.language)
    private fun progress(question: Question, next: Long) =
        UserQuestionProgress(question.id, 1, attempts = 1, mastery = 25, lastAnswered = next - 86_400_000, nextReview = next)
    private fun message(snapshot: LearningSnapshot, study: StudyLanguage = StudyLanguage.FINNISH) = StudyReminder.message(snapshot, study, now, zone)

    @Test fun `due reviews are counted in the selected study language`() {
        val qs = (1..3).map { q.copy(id = "q$it") } + q.copy(id = "sv", language = "sv")
        val snapshot = LearningSnapshot(topics, qs, qs.map { progress(it, now - 1000) }, qs.map { attempt(it, at(11, 1, 12)) })
        assertEquals(ReminderMessage.Reviews(3), message(snapshot))
        assertEquals(ReminderMessage.Reviews(1), message(snapshot, StudyLanguage.SWEDISH))
    }
    @Test fun `nothing due offers a new lesson while unseen questions remain`() {
        val qs = listOf(q.copy(id = "seen"), q.copy(id = "new"))
        val snapshot = LearningSnapshot(topics, qs, listOf(progress(qs[0], now + 86_400_000)), listOf(attempt(qs[0], at(11, 1, 9))))
        assertEquals(ReminderMessage.NewLesson, message(snapshot))
    }
    @Test fun `practice earlier today in either language silences the reminder`() {
        val qs = listOf(q, q.copy(id = "sv", language = "sv"))
        val due = qs.map { progress(it, now - 1000) }
        assertNull(message(LearningSnapshot(topics, qs, due, listOf(attempt(qs[1], at(11, 2, 0, 5))))))
        // Just before midnight yesterday is not today.
        assertNotNull(message(LearningSnapshot(topics, qs, due, listOf(attempt(qs[1], at(11, 1, 23, 59))))))
    }
    @Test fun `no material and nothing left to learn mean no reminder`() {
        assertNull(message(LearningSnapshot()))
        assertNull(message(LearningSnapshot(topics, listOf(q), listOf(progress(q, now + 86_400_000)), listOf(attempt(q, at(10, 30, 9))))))
    }
    @Test fun `next reminder is later today or else tomorrow`() {
        assertEquals(at(11, 2, 19, 30), StudyReminder.nextAt(19 * 60 + 30, now, zone))
        assertEquals(at(11, 3, 18), StudyReminder.nextAt(18 * 60, now, zone))
        assertEquals(at(11, 3, 8), StudyReminder.nextAt(8 * 60, now, zone))
    }
    @Test fun `next reminder keeps wall-clock time across daylight saving changes`() {
        // Clocks go back on 25 October 2026 and forward on 28 March 2027 in Helsinki.
        assertEquals(at(10, 25, 18), StudyReminder.nextAt(18 * 60, at(10, 24, 20), zone))
        assertEquals(ZonedDateTime.of(2027, 3, 28, 18, 0, 0, 0, zone).toInstant().toEpochMilli(),
            StudyReminder.nextAt(18 * 60, ZonedDateTime.of(2027, 3, 27, 20, 0, 0, 0, zone).toInstant().toEpochMilli(), zone))
    }
}
