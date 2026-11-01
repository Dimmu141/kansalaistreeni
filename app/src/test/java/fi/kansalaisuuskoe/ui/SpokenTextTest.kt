package fi.kansalaisuuskoe.ui

import fi.kansalaisuuskoe.domain.*
import org.junit.Assert.assertEquals
import org.junit.Test

class SpokenTextTest {
    private val q = Question("q", 1, "t", "Kuka säätää Suomen lait?", listOf(AnswerOption("a", "Eduskunta"), AnswerOption("b", "Tasavallan presidentti."),
        AnswerOption("c", "Valtioneuvosto ")), "a", "Explanation", null, emptyList(), Source("Source", "https://example.org"), "fi", Difficulty.EASY, emptyList(), ContentStatus.DEMO)

    @Test fun `choices are read as separate sentences after the question`() {
        assertEquals("Kuka säätää Suomen lait? Eduskunta. Tasavallan presidentti. Valtioneuvosto.", q.spokenText())
    }
    @Test fun `true or false statements are read without the answer buttons`() {
        val statement = q.copy(text = "Eduskunta säätää lait.", options = listOf(AnswerOption(QuestionFormat.TRUE_ID, "Totta"), AnswerOption(QuestionFormat.FALSE_ID, "Tarua")),
            format = QuestionFormat.TRUE_FALSE)
        assertEquals("Eduskunta säätää lait.", statement.spokenText())
    }
}
