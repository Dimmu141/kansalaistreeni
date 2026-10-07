package fi.kansalaisuuskoe.data

import fi.kansalaisuuskoe.domain.*
import org.junit.Assert.*
import org.junit.Test

class AssessmentBackupTest {
    private val q = QuestionDto("q", 1, "topic", "Original question?", listOf(OptionDto("a", "Yes"), OptionDto("b", "No")), "a", "Original explanation", source = SourceDto("Source", "https://example.org"), language = "fi", difficulty = "EASY", tags = emptyList(), status = "DEMO")
    private val topic = TopicDto("topic", "Topic", "Description", 0)
    private val content = ContentPackageDto(1, "demo", 2, "DEMO", listOf(topic), listOf(q.copy(revision = 2, correctOptionId = "b")))
    private val session = BackupAssessment("result", "fi", 1000, 2000, AssessmentPayload(listOf(topic), listOf(q), mapOf("q" to "a")))
    private fun backup(sessions: List<BackupAssessment>? = listOf(session)) = LearningBackup(BackupCodec.FORMAT, 2, 3000, content, emptyList(), sessions)
    private fun rejected(value: LearningBackup, problem: BackupProblem = BackupProblem.INVALID) {
        try { BackupCodec.validate(value); fail("Invalid assessment backup accepted") } catch (e: BackupException) { assertEquals(problem, e.problem) }
    }
    @Test fun `old original snapshots preserve result independently of installed answer key`() {
        val restored = BackupCodec.parse(BackupCodec.encode(backup())).assessments!!.single()
        assertEquals(session, restored)
        val model = AssessmentSession(restored.id, restored.language, restored.startedAt, restored.finishedAt, restored.payload.topics.map { it.model() }, restored.payload.questions.map { it.model() }, restored.payload.answers, restored.payload.position)
        assertEquals(1, Assessments.result(model).correct)
        assertEquals("b", content.questions.single().correctOptionId)
    }
    @Test fun `both language unfinished answers positions and source snapshots roundtrip`() {
        val qs = listOf(q.copy(id = "sv-1", language = "sv"), q.copy(id = "sv-2", language = "sv"))
        val active = BackupAssessment("unfinished", "sv", 2500, null, AssessmentPayload(listOf(topic), qs, mapOf("sv-1" to "b"), 1))
        val value = backup(listOf(session, active))
        assertEquals(value, BackupCodec.parse(BackupCodec.encode(value)))
    }
    @Test fun `legacy absence differs from deliberate empty replacement`() {
        val legacy = backup(null).copy(schemaVersion = 1)
        assertFalse(BackupCodec.encode(legacy).contains("\"assessments\""))
        assertNull(BackupCodec.parse(BackupCodec.encode(legacy)).assessments)
        assertEquals(emptyList<BackupAssessment>(), BackupCodec.parse(BackupCodec.encode(backup(emptyList()))).assessments)
        rejected(backup(null)); rejected(backup(emptyList()).copy(schemaVersion = 1))
    }
    @Test fun `duplicate session IDs and multiple unfinished sessions per language are rejected`() {
        rejected(backup(listOf(session, session)))
        val active = session.copy(finishedAt = null)
        rejected(backup(listOf(active, active.copy(id = "another"))))
    }
    @Test fun `unknown answers invalid choices and incomplete completed results are rejected`() {
        listOf(mapOf("other" to "a"), mapOf("q" to "missing"), emptyMap()).forEach { answers ->
            rejected(backup(listOf(session.copy(payload = session.payload.copy(answers = answers)))))
        }
    }
    @Test fun `language mismatch empty IDs invalid dates and positions are rejected`() {
        listOf(session.copy(id = " "), session.copy(language = "en"), session.copy(language = "sv"),
            session.copy(startedAt = -1), session.copy(finishedAt = 999), session.copy(finishedAt = Long.MAX_VALUE),
            session.copy(payload = session.payload.copy(position = -1)), session.copy(payload = session.payload.copy(position = 1))).forEach { rejected(backup(listOf(it))) }
    }
    @Test fun `malformed question and topic snapshots are rejected before restoration`() {
        listOf(session.payload.copy(topics = emptyList()), session.payload.copy(questions = listOf(q, q)),
            session.payload.copy(questions = listOf(q.copy(source = SourceDto("Source", "http://example.org")))),
            session.payload.copy(questions = listOf(q.copy(correctOptionId = "missing"))),
            session.payload.copy(questions = listOf(q.copy(topicId = "missing")))).forEach { rejected(backup(listOf(session.copy(payload = it)))) }
    }
    @Test fun `assessment and question bounds are enforced before replay`() {
        rejected(backup(List(BackupCodec.MAX_ASSESSMENTS + 1) { session.copy(id = "$it") }), BackupProblem.TOO_LARGE)
        rejected(backup(listOf(session.copy(finishedAt = null, payload = session.payload.copy(questions = emptyList())))))
        rejected(backup(listOf(session.copy(finishedAt = null, payload = session.payload.copy(questions = List(Assessments.MAX_QUESTIONS + 1) { q.copy(id = "q$it") }, answers = emptyMap())))))
    }
    @Test fun `unknown nested fields and injected result percentages are rejected`() {
        val json = BackupCodec.encode(backup()).replace("\"position\":0", "\"accuracy\":100,\"position\":0")
        try { BackupCodec.parse(json); fail("Unrecognised result data accepted") } catch (e: BackupException) { assertEquals(BackupProblem.INVALID, e.problem) }
    }
}
