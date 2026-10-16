package fi.kansalaisuuskoe

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.moshi.Moshi
import fi.kansalaisuuskoe.data.*
import fi.kansalaisuuskoe.domain.StudyLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Statements use the normal check-then-feedback flow with interface-language answer labels. */
@RunWith(AndroidJUnit4::class)
class TrueFalseFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as CitizenshipApplication
    private fun await(value: String) { rule.waitUntil(15_000) { rule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() } }
    private fun click(value: String) {
        // Docked controls (Check, Next) sit outside the list and need no scrolling.
        runCatching { rule.onNode(hasScrollAction()).performScrollToNode(hasText(value)) }
        rule.onAllNodesWithText(value).onFirst().performClick()
    }

    @Before fun reset(): Unit = runBlocking {
        app.container.database.clearAllTables()
        app.container.learning.initialize(app)
        app.container.settings.setInterfaceLanguage(InterfaceLanguage.ENGLISH)
        app.container.settings.setStudyLanguage(StudyLanguage.FINNISH)
        app.container.settings.setExplanation(ExplanationPreference.STUDY_LANGUAGE)
        app.container.settings.setTheme(ThemePreference.LIGHT)
    }

    @Test fun statementsUseTrueFalseButtonsAndNormalFeedback() {
        val content = ContentCodec.parse(app.assets.open("demo-content.json").bufferedReader().use { it.readText() })
        val topic = content.topics.first()
        val template = content.questions.first { it.topicId == topic.id && it.language == "fi" }
        fun statement(id: String, text: String, answer: String) = template.copy(id = id, text = text, format = "TRUE_FALSE",
            options = listOf(OptionDto("false", "Väärin"), OptionDto("true", "Oikein")), correctOptionId = answer, translations = emptyMap())
        val statements = listOf(
            statement("tf-parliament-laws", "Eduskunta säätää Suomen lait.", "true"),
            statement("tf-parliament-size", "Eduskunnassa on 100 kansanedustajaa.", "false"),
        )
        val updated = content.copy(schemaVersion = 2, version = content.version + 1,
            questions = content.questions.filter { it.topicId != topic.id || it.language != "fi" } + statements)
        runBlocking { app.container.learning.importContent(Moshi.Builder().build().adapter(ContentPackageDto::class.java).toJson(updated)) }

        await("Your learning path")
        rule.onNodeWithText("Topics").performClick()
        click("Democracy and decision-making"); click("Practise this topic")

        await(statements[0].text)
        rule.onNodeWithText("Statement · is it true?").assertIsDisplayed()
        rule.onNodeWithText("True").assertIsDisplayed(); rule.onNodeWithText("False").assertIsDisplayed()
        rule.onNodeWithText("Oikein").assertDoesNotExist()
        rule.onNodeWithText("True").performClick()
        rule.activityRule.scenario.recreate()
        await(statements[0].text)
        rule.onNodeWithText("True").assertIsSelected()
        click("Check answer"); await("Correct")
        rule.onNodeWithText("Correct answer: True").assertDoesNotExist() // a correct statement needs no repeat
        click("Next question")

        await(statements[1].text)
        rule.onNodeWithText("True").performClick()
        click("Check answer"); await("Correct answer: False")
        click("Show summary"); await("Practice complete")

        val attempts = runBlocking { app.container.learning.snapshot.first() }.attempts
        assertEquals(listOf("true", "true"), attempts.map { it.selectedOptionId })
        assertEquals(listOf(true, false), attempts.map { it.correct })
    }
}
