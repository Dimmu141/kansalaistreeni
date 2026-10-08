package fi.kansalaisuuskoe.data

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ContentValidationTest {
    @Test fun `every demo variant has English support with stable option ids`() {
        content().questions.forEach { q ->
            val en = q.translations.getValue("en")
            assertEquals(q.options.map { it.id }, en.options.map { it.id })
            assertTrue(en.explanation.isNotBlank())
            val counterpart = content().questions.first { it.id.removeSuffix("-sv") == q.id.removeSuffix("-sv") && it.language != q.language }
            assertEquals(en, counterpart.translations.getValue("en"))
        }
    }
    @Test(expected = IllegalArgumentException::class) fun `invalid translation language tag is rejected`() {
        val c = content(); val q = c.questions.first()
        ContentCodec.validate(c.copy(questions = listOf(q.copy(translations = mapOf("English!" to q.translations.getValue("en"))))))
    }
    @Test(expected = IllegalArgumentException::class) fun `source URLs cannot contain credentials`() {
        val c = content()
        ContentCodec.validate(c.copy(questions = listOf(c.questions.first().copy(source = SourceDto("Misleading", "https://finlex.fi@evil.example/source")))))
    }
    @Test(expected = IllegalArgumentException::class) fun `blank vocabulary cannot pass editorial validation`() {
        val c = content()
        ContentCodec.validate(c.copy(questions = listOf(c.questions.first().copy(vocabulary = listOf(VocabularyDto("", "meaning"))))))
    }
    @Test(expected = IllegalArgumentException::class) fun `blank easy explanation is rejected`() {
        val c = content()
        ContentCodec.validate(c.copy(questions = listOf(c.questions.first().copy(easyExplanation = " "))))
    }
    private fun content() = ContentCodec.parse(File("src/main/assets/demo-content.json").readText())
    @Test fun `seed is valid sourced demo across all topics with variable options`() {
        val c = content()
        assertEquals(60, c.questions.size)
        assertEquals(mapOf("fi" to 30, "sv" to 30), c.questions.groupingBy { it.language }.eachCount())
        assertEquals(6, c.topics.size)
        assertTrue(c.questions.all { it.status == "DEMO" && (it.language == "sv" || it.easyExplanation != null) })
        assertTrue(c.topics.all { t -> c.questions.count { it.topicId == t.id } == 10 })
        assertEquals(setOf(3, 4), c.questions.map { it.options.size }.toSet())
        assertTrue(c.questions.all { it.model().source.url.startsWith("https://") })
    }
    @Test(expected = IllegalArgumentException::class) fun `unknown topic fails validation`() {
        val c = content()
        ContentCodec.validate(c.copy(questions = listOf(c.questions.first().copy(topicId = "missing"))))
    }
    @Test(expected = IllegalArgumentException::class) fun `duplicate question ids fail validation`() {
        val c = content()
        ContentCodec.validate(c.copy(questions = listOf(c.questions.first(), c.questions.first())))
    }
    @Test(expected = IllegalArgumentException::class) fun `missing correct answer fails validation`() {
        val c = content()
        ContentCodec.validate(c.copy(questions = listOf(c.questions.first().copy(correctOptionId = "missing"))))
    }
    @Test(expected = IllegalArgumentException::class) fun `demo cannot silently become official`() {
        val c = content()
        ContentCodec.validate(c.copy(questions = listOf(c.questions.first().copy(status = "OFFICIAL"))))
    }
    @Test(expected = IllegalArgumentException::class) fun `unsupported content schema fails validation`() {
        ContentCodec.validate(content().copy(schemaVersion = ContentCodec.SCHEMA_VERSION + 1))
    }
    @Test(expected = IllegalArgumentException::class) fun `translations must preserve answer ids`() {
        val c = content()
        val q = c.questions.first()
        ContentCodec.validate(c.copy(questions = listOf(q.copy(translations = mapOf("sv" to TranslationDto("Fråga", listOf(OptionDto("missing", "Svar")), "Förklaring"))))))
    }
    @Test(expected = IllegalArgumentException::class) fun `non https source fails validation`() {
        val c = content()
        val question = c.questions.first().copy(source = SourceDto("Source", "http://example.org"))
        ContentCodec.validate(c.copy(questions = listOf(question)))
    }
    @Test fun `translated topic labels retain original fallback`() {
        val topic = content().topics.first().model()
        assertEquals("Society and democracy", topic.titleFor("en"))
        assertEquals("Parliament, decision-making and participation.", topic.descriptionFor("en"))
        assertEquals(topic.title, topic.titleFor("fi"))
        assertEquals("Riksdagen, beslut och deltagande.", topic.descriptionFor("sv"))
    }
    @Test fun `old content without topic translations still parses`() {
        val c = content()
        val json = com.squareup.moshi.Moshi.Builder().build().adapter(ContentPackageDto::class.java)
            .toJson(c.copy(topics = c.topics.map { it.copy(translations = emptyMap()) }))
            .replace(",\"translations\":{}", "")
        val original = ContentCodec.parse(json).topics.first().model()
        assertEquals(original.title, original.titleFor("en"))
    }
    @Test(expected = IllegalArgumentException::class) fun `blank translated topic label is rejected`() {
        val c = content()
        ContentCodec.validate(c.copy(topics = listOf(c.topics.first().copy(translations = mapOf("en" to TopicTranslationDto("", "Description"))))))
    }
    @Test(expected = IllegalArgumentException::class) fun `invalid topic translation language tag is rejected`() {
        val c = content()
        ContentCodec.validate(c.copy(topics = listOf(c.topics.first().copy(translations = mapOf("English" to TopicTranslationDto("Title", "Description"))))))
    }
}
