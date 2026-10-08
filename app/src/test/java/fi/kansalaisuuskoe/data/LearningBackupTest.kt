package fi.kansalaisuuskoe.data

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class LearningBackupTest {
    private val q = QuestionDto("q", 1, "topic", "Question?", listOf(OptionDto("a", "Yes"), OptionDto("b", "No")), "a", "Explanation", source = SourceDto("Source", "https://example.org"), language = "fi", difficulty = "EASY", tags = emptyList(), status = "DEMO")
    private val content = ContentPackageDto(1, "demo", 1, "DEMO", listOf(TopicDto("topic", "Topic", "Description", 0)), listOf(q))
    private val attempt = BackupAttempt("one", "q", 1, "topic", "a", true, 1000, "fi")
    private val backup = LearningBackup(BackupCodec.FORMAT, 1, 2000, content, listOf(attempt))
    private fun rejected(value: LearningBackup, problem: BackupProblem = BackupProblem.INVALID) {
        try { BackupCodec.validate(value); fail("Invalid backup accepted") } catch (e: BackupException) { assertEquals(problem, e.problem) }
    }

    @Test fun `roundtrip retains content source vocabulary and both language histories`() {
        val sv = q.copy(id = "sv", language = "sv", vocabulary = listOf(VocabularyDto("sana", "word", "ord")))
        val mixed = backup.copy(content = content.copy(questions = listOf(q, sv)), attempts = listOf(attempt, attempt.copy(id = "two", questionId = "sv", language = "sv")))
        assertEquals(mixed, BackupCodec.parse(BackupCodec.encode(mixed)))
    }
    @Test fun `unsupported schema format and negative creation date are rejected`() {
        rejected(backup.copy(schemaVersion = 3)); rejected(backup.copy(format = "other")); rejected(backup.copy(exportedAt = -1))
    }
    @Test fun `duplicate attempt tokens are rejected rather than merged`() { rejected(backup.copy(attempts = listOf(attempt, attempt))) }
    @Test fun `current answers must match option correctness`() {
        rejected(backup.copy(attempts = listOf(attempt.copy(selectedOptionId = "missing"))))
        rejected(backup.copy(attempts = listOf(attempt.copy(correct = false))))
    }
    @Test fun `an attempt keeps the topic it was answered under after reclassification`() {
        val moved = backup.copy(attempts = listOf(attempt.copy(topicId = "retired-topic")))
        assertEquals(moved, BackupCodec.parse(BackupCodec.encode(moved)))
    }
    @Test fun `language changes and future revisions are rejected`() {
        rejected(backup.copy(attempts = listOf(attempt.copy(language = "sv"))))
        rejected(backup.copy(attempts = listOf(attempt.copy(revision = 2))))
        rejected(backup.copy(attempts = listOf(attempt.copy(language = "en"))))
    }
    @Test fun `historical and retired questions retain structurally valid history`() {
        val history = backup.copy(content = content.copy(questions = listOf(q.copy(revision = 2))), attempts = listOf(attempt.copy(topicId = "old-topic", selectedOptionId = "old-option"), attempt.copy(id = "retired", questionId = "removed")))
        assertEquals(history, BackupCodec.parse(BackupCodec.encode(history)))
    }
    @Test fun `retired IDs cannot mix study languages`() {
        rejected(backup.copy(attempts = listOf(attempt.copy(questionId = "removed"), attempt.copy(id = "other", questionId = "removed", language = "sv"))))
    }
    @Test fun `blank IDs zero revisions negative dates and overflowing schedule dates are rejected`() {
        rejected(backup.copy(attempts = listOf(attempt.copy(id = " "))))
        rejected(backup.copy(attempts = listOf(attempt.copy(revision = 0))))
        rejected(backup.copy(attempts = listOf(attempt.copy(answeredAt = -1))))
        rejected(backup.copy(attempts = listOf(attempt.copy(answeredAt = Long.MAX_VALUE))))
    }
    @Test fun `attempt limit applies before replay or serialization`() {
        rejected(backup.copy(attempts = List(BackupCodec.MAX_ATTEMPTS + 1) { attempt.copy(id = "$it") }), BackupProblem.TOO_LARGE)
    }
    @Test fun `ordinary content files and injected derived scores are rejected`() {
        val json = BackupCodec.encode(backup)
        try { BackupCodec.parse(json.replaceFirst("{", "{\"mastery\":100,")); fail("Imported a score") } catch (e: BackupException) { assertEquals(BackupProblem.INVALID, e.problem) }
        try { BackupCodec.parse("{\"schemaVersion\":1,\"questions\":[]}"); fail("Accepted material as backup") } catch (e: BackupException) { assertEquals(BackupProblem.INVALID, e.problem) }
    }
    @Test fun `file reader accepts UTF8 BOM and rejects malformed UTF8`() {
        val json = BackupCodec.encode(backup)
        assertEquals(json, BackupFile.read(ByteArrayInputStream(("\uFEFF" + json).toByteArray())))
        try { BackupFile.read(ByteArrayInputStream(byteArrayOf(0xc3.toByte(), 0x28))); fail("Malformed UTF8 accepted") } catch (e: BackupException) { assertEquals(BackupProblem.INVALID, e.problem) }
    }
    @Test fun `file reader rejects oversized stream without trusting provider metadata`() {
        try { BackupFile.read(ByteArrayInputStream(ByteArray(BackupFile.MAX_BYTES + 1))); fail("Oversized file accepted") } catch (e: BackupException) { assertEquals(BackupProblem.TOO_LARGE, e.problem) }
    }
}
