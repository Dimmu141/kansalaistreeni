package fi.kansalaisuuskoe

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
class HardQuestionsFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as CitizenshipApplication
    private fun text(value: String) = rule.onNodeWithText(value)
    private fun await(value: String) { rule.waitUntil(15_000) { rule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() } }
    private fun click(value: String) { runCatching { rule.onNode(hasScrollAction()).performScrollToNode(hasText(value)) }; text(value).performClick() }
    private fun marked() = runBlocking { app.container.learning.marked.first() }
    private fun attempts() = runBlocking { app.container.learning.snapshot.first().attempts.size }
    @Before fun reset() = runBlocking {
        app.container.database.clearAllTables(); app.container.learning.initialize(app); app.container.learning.installRegressionContent()
        app.container.settings.setInterfaceLanguage(InterfaceLanguage.ENGLISH)
        app.container.settings.setStudyLanguage(StudyLanguage.FINNISH)
        app.container.settings.setExplanation(ExplanationPreference.STUDY_LANGUAGE)
        app.container.settings.setTheme(ThemePreference.LIGHT)
    }
    private fun missFirstThenFinish() {
        await("Your learning path"); text("Topics").performClick(); click("Society and democracy"); click("Practise this topic")
        await("Kuka käyttää Suomessa lainsäädäntövaltaa?")
        text("Tasavallan presidentti").performClick(); click("Check answer"); await("Add to hard")
    }

    @Test fun missedQuestionsCanBePractisedAgainFromTheSummary() {
        missFirstThenFinish(); click("Next question")
        await("Kuinka monta kansanedustajaa Suomen eduskunnassa on?")
        text("200").performClick(); click("Check answer"); click("Show summary"); await("Practice complete")
        click("Practise mistakes again · 1 question")
        // Only the missed question comes back.
        await("Kuka käyttää Suomessa lainsäädäntövaltaa?"); text("1 / 1").assertIsDisplayed()
        text("Eduskunta").performClick(); click("Check answer"); click("Show summary"); await("Practice complete")
        text("Correct: 1 / 1. Your answers and progress are saved on this device.").assertIsDisplayed()
        text("Practise mistakes again · 1 question").assertDoesNotExist()
        assertEquals(3, attempts())
        click("Return to your learning path"); await("Your learning path")
    }

    @Test fun learnerMarksAQuestionPractisesItFromReviewAndRemovesIt() {
        missFirstThenFinish()
        click("Add to hard"); await("Remove from hard")
        assertEquals(listOf("demo-parliament"), marked())
        // The mark survives recreation and never changes scores.
        rule.activityRule.scenario.recreate(); await("Remove from hard")
        assertEquals(1, attempts())
        // Back out of the session and the topic lesson to the tab bar.
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        await("Practise this topic")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        await("Society and democracy"); text("Review").performClick()
        await("Hard questions"); click("Practise hard questions · 1 question")
        await("Kuka käyttää Suomessa lainsäädäntövaltaa?"); text("1 / 1").assertIsDisplayed()
        text("Eduskunta").performClick(); click("Check answer"); click("Remove from hard"); await("Add to hard")
        assertTrue(marked().isEmpty())
        click("Show summary"); await("Practice complete"); click("Return to your learning path")
        text("Review").performClick(); await("Hard questions")
        text("Practise hard questions · 1 question").assertDoesNotExist()
    }
}
