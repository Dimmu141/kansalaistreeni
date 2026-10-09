package fi.kansalaisuuskoe.domain

import org.junit.Assert.*
import org.junit.Test

class ExamProfileTest {
    private fun result(correct: Int, total: Int) = AssessmentResult(correct, total, emptyList(), emptyList())

    @Test fun `the app uses the unknown profile with no pass mark or time limit`() {
        val profile = ExamProfile.CURRENT
        assertEquals(ExamProfile.UNKNOWN, profile)
        assertFalse(profile.known)
        assertNull(profile.passThresholdPercent); assertNull(profile.timeLimitMinutes)
        assertNull(profile.passed(result(20, 20)))
        assertEquals(20, profile.questionCount); assertEquals(0.3, profile.trueFalseShare, 0.0)
    }
    @Test fun `a known pass mark gives a verdict at the threshold`() {
        val profile = ExamProfile("example", questionCount = 40, trueFalseShare = 0.25, passThresholdPercent = 60, timeLimitMinutes = 45)
        assertTrue(profile.known)
        assertEquals(true, profile.passed(result(24, 40)))
        assertEquals(false, profile.passed(result(23, 40)))
        assertEquals(false, profile.passed(result(0, 0)))
    }
    @Test fun `invalid profiles are rejected`() {
        listOf({ ExamProfile("", 20, 0.3) }, { ExamProfile("x", 0, 0.3) }, { ExamProfile("x", 20, 1.5) },
            { ExamProfile("x", 20, 0.3, passThresholdPercent = 0) }, { ExamProfile("x", 20, 0.3, timeLimitMinutes = 0) })
            .forEach { make -> try { make(); fail("Invalid profile accepted") } catch (_: IllegalArgumentException) { } }
    }
    @Test fun `the assessment queue follows the profile length`() {
        val q = Question("q", 1, "t", "Kysymys?", listOf(AnswerOption("a", "A"), AnswerOption("b", "B")), "a", "Selitys.",
            null, emptyList(), Source("Lähde", "https://example.org"), "fi", Difficulty.EASY, emptyList(), ContentStatus.EDITORIAL)
        val pool = (0..29).map { q.copy(id = "q$it") }
        assertEquals(ExamProfile.CURRENT.questionCount, Assessments.queue(pool, "fi").size)
        assertEquals(5, Assessments.queue(pool, "fi", profile = ExamProfile("short", 5, 0.0)).size)
    }
}
