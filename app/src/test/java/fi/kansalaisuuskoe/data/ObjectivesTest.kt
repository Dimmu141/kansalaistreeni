package fi.kansalaisuuskoe.data

import fi.kansalaisuuskoe.domain.*
import org.junit.Assert.*
import org.junit.Test

class ObjectivesTest {
    private val topics = listOf(TopicDto("rights", "Oikeudet", "Kuvaus", 0), TopicDto("history", "Historia", "Kuvaus", 1))
    private val objectives = listOf(
        ObjectiveDto("rights-religion", "rights", mapOf("fi" to "Uskonnonvapaus", "sv" to "Religionsfrihet", "en" to "Freedom of religion"), "Perustuslaki 11 §"),
        ObjectiveDto("hist-1917", "history", mapOf("fi" to "Itsenäisyys", "sv" to "Självständigheten")),
    )
    private fun question(id: String, topic: String, objectiveIds: List<String>, language: String = "fi") = QuestionDto(id, 1, topic, "Kysymys?",
        listOf(OptionDto("a", "A"), OptionDto("b", "B")), "a", "Selitys", source = SourceDto("Finlex", "https://www.finlex.fi/"),
        language = language, difficulty = "EASY", tags = emptyList(), status = "EDITORIAL", objectiveIds = objectiveIds)
    private val questions = listOf(
        question("q1", "rights", listOf("rights-religion")), question("q2", "rights", listOf("rights-religion")),
        question("q3", "rights", listOf("rights-religion")), question("q1-sv", "rights", listOf("rights-religion"), "sv"),
        // A question can also serve an objective in another topic.
        question("q4", "history", listOf("hist-1917", "rights-religion")),
    )
    private fun pack(qs: List<QuestionDto> = questions, objs: List<ObjectiveDto> = objectives, schema: Int = 2) =
        ContentPackageDto(schema, "objectives", 1, "EDITORIAL", topics, qs, objs)
    private fun rejected(content: ContentPackageDto, snapshot: Boolean = false) {
        try { ContentCodec.validate(content, snapshot); fail("Invalid objectives accepted") } catch (_: IllegalArgumentException) {}
    }

    @Test fun `valid objectives and references pass`() { ContentCodec.validate(pack()) }
    @Test fun `every question must reference an existing objective`() {
        rejected(pack(questions + question("orphan", "rights", emptyList())))
        rejected(pack(questions + question("unknown", "rights", listOf("missing"))))
        rejected(pack(questions + question("twice", "rights", listOf("rights-religion", "rights-religion"))))
    }
    @Test fun `objectives need a known topic unique ids and Finnish and Swedish statements`() {
        rejected(pack(objs = objectives + objectives.first()))
        rejected(pack(objs = objectives + ObjectiveDto("x", "missing", mapOf("fi" to "A", "sv" to "B"))))
        rejected(pack(objs = objectives.map { it.copy(statement = it.statement - "sv") }))
        rejected(pack(objs = objectives.map { it.copy(statement = it.statement + ("German!" to "Ziel")) }))
        rejected(pack(objs = objectives.map { it.copy(sourceRef = " ") }))
        rejected(pack(objs = objectives.map { it.copy(id = "Bad Id") }))
    }
    @Test fun `objectives need schema 2 and references need an objective list`() {
        rejected(pack(questions.map { it.copy(objectiveIds = emptyList()) }, schema = 1))
        rejected(pack(objs = emptyList()))
        ContentCodec.validate(pack(questions.map { it.copy(objectiveIds = emptyList()) }, emptyList()))
    }
    @Test fun `assessment snapshots keep references without copying objectives`() {
        ContentCodec.validate(pack(objs = emptyList()), snapshot = true)
    }
    @Test fun `coverage counts questions per objective and language and flags thin objectives`() {
        val report = Coverage.report(topics.map { it.model() }, objectives.map { it.model() }, questions.map { it.model() })
        assertEquals(listOf("rights", "history"), report.map { it.topic.id })
        val religion = report.first().objectives.single()
        assertEquals(mapOf("fi" to 4, "sv" to 1), religion.questionCounts)
        assertTrue(religion.thin)
        val independence = report.last().objectives.single()
        assertEquals(1, independence.count("fi"))
        assertEquals(0, independence.count("sv"))
        assertEquals(1, report.last().thinCount)
    }
    @Test fun `an untaught language is not reported as a gap`() {
        val finnish = questions.filter { it.language == "fi" } + (5..7).map { question("h$it", "history", listOf("hist-1917")) }
        val report = Coverage.report(topics.map { it.model() }, objectives.map { it.model() }, finnish.map { it.model() })
        assertTrue(report.flatMap { it.objectives }.none { it.thin })
        assertEquals(setOf("fi"), report.first().objectives.single().questionCounts.keys)
    }
    @Test fun `statements fall back to Finnish`() {
        assertEquals("Itsenäisyys", objectives.last().model().statementFor("en"))
        assertEquals("Religionsfrihet", objectives.first().model().statementFor("sv"))
    }
    @Test fun `objectives survive backups and changing them needs a package version`() {
        val content = pack()
        val backup = LearningBackup(BackupCodec.FORMAT, BackupCodec.CURRENT_SCHEMA, 3000, content, emptyList(), emptyList())
        assertEquals(content.objectives, BackupCodec.parse(BackupCodec.encode(backup)).content.objectives)
        val installed = InstalledContent(ContentMetadata(packageId = "objectives", version = 1, hash = "h"), topics, questions, objectives)
        assertTrue(ContentUpdates.prepare(content, "h", installed, emptyMap()).alreadyInstalled)
        val reworded = content.copy(objectives = objectives.map { it.copy(statement = it.statement + ("fi" to "Uusi")) })
        try { ContentUpdates.prepare(reworded, "h2", installed, emptyMap()); fail("Same-version change accepted") }
        catch (e: ContentImportException) { assertEquals(ImportProblem.VERSION, e.problem) }
        ContentUpdates.prepare(reworded.copy(version = 2), "h2", installed, emptyMap())
    }
}
