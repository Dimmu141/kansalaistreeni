package fi.kansalaisuuskoe.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ReviewDatesTest {
    private val topic = TopicDto("democracy", "Demokratia", "Kuvaus", 0)
    private val q = QuestionDto("president", 1, "democracy", "Kuka on Suomen presidentti?", listOf(OptionDto("a", "A"), OptionDto("b", "B")), "a", "Selitys",
        source = SourceDto("Valtioneuvosto", "https://valtioneuvosto.fi/", "2026-10-08"), language = "fi", difficulty = "EASY", tags = emptyList(),
        status = "EDITORIAL", validFrom = "2024-03-01", reviewBy = "2027-03-01")
    private fun pack(question: QuestionDto) = ContentPackageDto(2, "dates", 1, "EDITORIAL", listOf(topic), listOf(question))
    private fun rejected(question: QuestionDto) {
        try { ContentCodec.validate(pack(question)); fail("Invalid dates accepted") } catch (_: RuntimeException) {}
    }

    @Test fun `changeable facts carry validity review and source check dates`() {
        ContentCodec.validate(pack(q))
        val model = q.model()
        assertEquals("2026-10-08", model.source.checkedAt)
        assertFalse(model.reviewOverdue(LocalDate.parse("2027-03-01")))
        assertTrue(model.reviewOverdue(LocalDate.parse("2027-03-02")))
        assertFalse(q.copy(reviewBy = null).model().reviewOverdue(LocalDate.parse("2099-01-01")))
    }
    @Test fun `dates must be ISO and in order`() {
        rejected(q.copy(reviewBy = "1.3.2027"))
        rejected(q.copy(validFrom = "2027-03-01", reviewBy = "2027-03-01"))
        rejected(q.copy(source = q.source.copy(checkedAt = "2027-04-01")))
        rejected(q.copy(source = q.source.copy(checkedAt = "yesterday")))
    }
    @Test fun `questions without dates stay valid`() {
        ContentCodec.validate(pack(q.copy(validFrom = null, reviewBy = null, source = q.source.copy(checkedAt = null))))
    }
}
