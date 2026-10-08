package fi.kansalaisuuskoe.data

import fi.kansalaisuuskoe.domain.*
import org.junit.Assert.*
import org.junit.Test

class QuestionFormatTest {
    private val topic = TopicDto("rights", "Oikeudet", "Kuvaus", 0)
    private val choice = QuestionDto("choice", 1, "rights", "Kysymys?", listOf(OptionDto("a", "A"), OptionDto("b", "B")), "a", "Selitys",
        source = SourceDto("Finlex", "https://www.finlex.fi/"), language = "fi", difficulty = "EASY", tags = emptyList(), status = "EDITORIAL")
    private val statement = choice.copy(id = "statement", text = "Suomessa on yleinen äänioikeus.", format = "TRUE_FALSE",
        options = listOf(OptionDto("true", "Totta"), OptionDto("false", "Tarua")), correctOptionId = "true")
    private fun pack(vararg questions: QuestionDto, schema: Int = 2) = ContentPackageDto(schema, "formats", 1, "EDITORIAL", listOf(topic), questions.toList())
    private fun rejected(content: ContentPackageDto) {
        try { ContentCodec.validate(content); fail("Invalid format accepted") } catch (_: IllegalArgumentException) {}
    }

    @Test fun `true false statement is valid in schema 2 and maps to its format`() {
        ContentCodec.validate(pack(choice, statement))
        assertEquals(QuestionFormat.TRUE_FALSE, statement.model().format)
        assertEquals(QuestionFormat.SINGLE_CHOICE, choice.model().format)
    }
    @Test fun `option order is free but ids are fixed`() {
        ContentCodec.validate(pack(statement.copy(options = statement.options.reversed())))
        rejected(pack(statement.copy(options = listOf(OptionDto("yes", "Kyllä"), OptionDto("false", "Tarua")))))
        rejected(pack(statement.copy(options = statement.options + OptionDto("maybe", "Ehkä"))))
        rejected(pack(statement.copy(options = statement.options.take(1), correctOptionId = "true")))
    }
    @Test fun `schema 1 packages cannot contain true false questions`() {
        ContentCodec.validate(pack(choice, schema = 1))
        rejected(pack(statement, schema = 1))
    }
    @Test fun `unknown format is rejected`() { rejected(pack(choice.copy(format = "OPEN_TEXT"))) }

    @Test fun `stored payloads and packages without a format stay single choice with the same id and revision`() {
        val legacy = ContentCodec.questionAdapter.toJson(choice).replace(",\"format\":\"SINGLE_CHOICE\"", "")
        assertFalse(legacy.contains("format"))
        val parsed = requireNotNull(ContentCodec.questionAdapter.fromJson(legacy))
        assertEquals(choice, parsed)
        assertEquals(QuestionFormat.SINGLE_CHOICE, parsed.model().format)
        assertEquals("choice" to 1, parsed.id to parsed.revision)
    }
    @Test fun `true false survives backups and assessment snapshots`() {
        val content = pack(choice, statement)
        val session = BackupAssessment("s", "fi", 1000, 2000, AssessmentPayload(listOf(topic), listOf(statement), mapOf("statement" to "false")))
        val backup = LearningBackup(BackupCodec.FORMAT, BackupCodec.CURRENT_SCHEMA, 3000, content,
            listOf(BackupAttempt("a1", "statement", 1, "rights", "true", true, 1000, "fi")), listOf(session))
        val restored = BackupCodec.parse(BackupCodec.encode(backup))
        assertEquals(backup, restored)
        assertEquals("TRUE_FALSE", restored.assessments!!.single().payload.questions.single().format)
    }
}
