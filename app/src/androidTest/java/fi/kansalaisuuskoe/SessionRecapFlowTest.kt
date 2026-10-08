package fi.kansalaisuuskoe

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.kansalaisuuskoe.data.*
import fi.kansalaisuuskoe.domain.StudyLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionRecapFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as CitizenshipApplication
    private fun text(value: String) = rule.onNodeWithText(value)
    private fun await(value: String) { rule.waitUntil(15_000) { rule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() } }
    private fun click(value: String) { rule.onNode(hasScrollAction()).performScrollToNode(hasText(value)); text(value).performClick() }
    @Before fun reset() = runBlocking {
        app.container.database.clearAllTables(); app.container.learning.initialize(app); app.container.learning.installRegressionContent()
        app.container.settings.setInterfaceLanguage(InterfaceLanguage.ENGLISH)
        app.container.settings.setStudyLanguage(StudyLanguage.FINNISH)
        app.container.settings.setExplanation(ExplanationPreference.ALSO_ENGLISH)
        app.container.settings.setEasyFinnish(true)
        app.container.settings.setTheme(ThemePreference.LIGHT)
    }
    private fun startTopic() {
        await("Your learning path"); text("Topics").performClick(); click("Society and democracy"); click("Practise this topic")
    }
    @Test fun missedQuestionExplanationSurvivesRecreationWithoutRecordingAnotherAnswer() {
        startTopic(); await("Kuka käyttää Suomessa lainsäädäntövaltaa?")
        text("Tasavallan presidentti").performClick(); click("Check answer"); click("Next question")
        await("Kuinka monta kansanedustajaa Suomen eduskunnassa on?")
        text("200").performClick(); click("Check answer"); click("Show summary")
        await("Practice complete")
        text("Correct: 1 / 2. Your answers and progress are saved on this device.").assertIsDisplayed()
        click("Read explanation")
        rule.onNodeWithContentDescription("Question explanation: Kuka käyttää Suomessa lainsäädäntövaltaa?")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Explanation expanded"))
        val native = "Eduskunta säätää lait. Hallitus valmistelee lakiesityksiä."
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(native)); text(native).assertIsDisplayed()
        val english = "Parliament makes laws. The Government prepares legislative proposals."
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(english)); text(english).assertIsDisplayed()
        val before = runBlocking { app.container.learning.snapshot.first() }
        // The recap intentionally restores its scroll position; the heading may be offscreen.
        rule.activityRule.scenario.recreate(); await("Hide explanation")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(english)); text(english).assertIsDisplayed()
        assertEquals(before, runBlocking { app.container.learning.snapshot.first() })
        click("Hide explanation"); click("Return to your learning path"); await("Your learning path")
        assertEquals(2, runBlocking { app.container.learning.snapshot.first().attempts.size })
    }
    @Test fun allCorrectSwedishLessonDoesNotIncludeEarlierFinnishMistakes() {
        await("Your learning path")
        runBlocking {
            val q = app.container.learning.snapshot.first().questions.first { it.id == "demo-parliament" }
            app.container.learning.answer(q, q.options.first { it.id != q.correctOptionId }.id, "earlier-fi", System.currentTimeMillis())
            app.container.settings.setStudyLanguage(StudyLanguage.SWEDISH)
        }
        await("Study language: Swedish"); text("Topics").performClick(); click("Samhälle och demokrati"); click("Practise this topic")
        await("Vem utövar den lagstiftande makten i Finland?")
        text("Riksdagen").performClick(); click("Check answer"); click("Next question")
        await("Hur många ledamöter har Finlands riksdag?")
        text("200").performClick(); click("Check answer"); click("Show summary"); await("Practice complete")
        text("Correct: 2 / 2. Your answers and progress are saved on this device.").assertIsDisplayed()
        text("Read explanation").assertDoesNotExist()
        rule.activityRule.scenario.recreate(); await("Practice complete")
        text("Correct: 2 / 2. Your answers and progress are saved on this device.").assertIsDisplayed()
        assertEquals(3, runBlocking { app.container.learning.snapshot.first().attempts.size })
        click("Return to your learning path"); await("Your learning path")
    }
}
