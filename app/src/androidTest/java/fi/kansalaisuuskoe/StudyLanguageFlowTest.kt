package fi.kansalaisuuskoe

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.moshi.Moshi
import fi.kansalaisuuskoe.data.*
import fi.kansalaisuuskoe.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyLanguageFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as CitizenshipApplication
    private val repository get() = app.container.learning
    private fun text(value: String) = rule.onNodeWithText(value)
    private fun await(value: String) { rule.waitUntil(15_000) { rule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() } }
    private fun click(value: String) {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(value)); text(value).performClick()
    }
    @Before fun setup() = runBlocking {
        app.container.database.clearAllTables(); repository.initialize(app); repository.installRegressionContent()
        app.container.settings.setInterfaceLanguage(InterfaceLanguage.ENGLISH)
        app.container.settings.setStudyLanguage(StudyLanguage.FINNISH)
        app.container.settings.setExplanation(ExplanationPreference.STUDY_LANGUAGE)
        app.container.settings.setTheme(ThemePreference.LIGHT)
        app.container.settings.setEasyFinnish(true)
    }
    private fun chooseSwedish() {
        await("Your learning path")
        rule.onNodeWithContentDescription("Settings").performClick()
        click("Swedish questions")
        rule.activityRule.scenario.recreate()
        await("Interface language")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Swedish questions"))
        text("Swedish questions").assertIsSelected()
        rule.onNodeWithContentDescription("Back").performClick()
        await("Study language: Swedish")
    }
    @Test fun swedishLearningRestoresFeedbackAndKeepsFinnishHistorySeparate() {
        chooseSwedish()
        text("Topics").performClick()
        click("Yhteiskunta ja demokratia"); click("Practise this topic")
        await("Vem utövar den lagstiftande makten i Finland?")
        text("Riksdagen").performClick(); click("Check answer")
        await("Correct")
        rule.activityRule.scenario.recreate(); await("Correct")
        text("In easy Finnish").assertDoesNotExist()
        click("Next question")
        await("Hur många ledamöter har Finlands riksdag?")
        text("200").performClick(); click("Check answer")
        click("Show summary"); click("Return to your learning path")
        await("Your learning path")
        val saved = runBlocking { repository.snapshot.first() }
        assertEquals(listOf("sv", "sv"), saved.attempts.map { it.language })
        assertEquals(0, LearningEngine.summarize(saved.forLanguage(StudyLanguage.FINNISH), System.currentTimeMillis()).answered)
        assertEquals(2, LearningEngine.summarize(saved.forLanguage(StudyLanguage.SWEDISH), System.currentTimeMillis()).answered)
        rule.onNodeWithContentDescription("Settings").performClick(); click("Finnish questions")
        rule.onNodeWithContentDescription("Back").performClick(); await("Study language: Finnish")
        click("Start learning")
        await("Kuka käyttää Suomessa lainsäädäntövaltaa?")
        text("Eduskunta").assertExists()
        assertEquals(2, runBlocking { repository.snapshot.first().attempts.size })
    }
    @Test fun reviewOnlyContainsTheSelectedLanguage() {
        runBlocking {
            val now = System.currentTimeMillis() - 660_000
            repository.snapshot.first().questions.filter { it.id in setOf("demo-parliament", "demo-emergency-sv") }.forEach {
                repository.answer(it, it.options.first { o -> o.id != it.correctOptionId }.id, it.id, now)
            }
        }
        chooseSwedish()
        text("Review").performClick(); await("Start review · 1 question")
        click("Start review · 1 question")
        await("Vilket nummer ringer du i en brådskande nödsituation i Finland?")
        text("112").performClick(); click("Check answer")
        click("Show summary"); click("Return to your learning path")
        val saved = runBlocking { repository.snapshot.first() }
        assertEquals(0, LearningEngine.due(saved.forLanguage(StudyLanguage.SWEDISH), System.currentTimeMillis()).size)
        assertEquals(1, LearningEngine.due(saved.forLanguage(StudyLanguage.FINNISH), System.currentTimeMillis()).size)
    }
    @Test fun topicNamesFollowTheStudyLanguage() {
        // The bundled package has Swedish topic names; the older regression package falls back to Finnish.
        runBlocking { app.container.database.clearAllTables(); repository.initialize(app) }
        chooseSwedish()
        text("Topics").performClick(); await("Demokrati och beslutsfattande")
        text("Democracy and decision-making").assertDoesNotExist()
        runBlocking { app.container.settings.setStudyLanguage(StudyLanguage.FINNISH) }
        // Finnish study keeps the interface language (English here) for topic names.
        await("Democracy and decision-making")
        text("Demokrati och beslutsfattande").assertDoesNotExist()
    }
    @Test fun finnishOnlyImportShowsAnHonestSwedishEmptyState() {
        chooseSwedish()
        runBlocking {
            val seed = ContentCodec.parse(regressionContentJson())
            val json = Moshi.Builder().build().adapter(ContentPackageDto::class.java).toJson(seed.copy(version = seed.version + 1, questions = seed.questions.filter { it.language == "fi" }))
            repository.confirmImport(repository.prepareImport(json))
        }
        await("No questions in this language yet")
        text("Start learning").assertDoesNotExist()
        text("Topics").performClick(); await("What would you like to learn?")
        text("Yhteiskunta ja demokratia").assertDoesNotExist()
        text("Review").performClick()
        text("Start review · 1 question").assertDoesNotExist()
        text("Progress").performClick()
        text("Practice material mastery").assertDoesNotExist()
    }
    @Test fun activeSessionKeepsItsLanguageAfterPreferenceChangesAndRecreation() {
        chooseSwedish()
        val planned = runBlocking { DailyLearningPlanner.plan(repository.snapshot.first().forLanguage(StudyLanguage.SWEDISH), System.currentTimeMillis()) }
        click("Start learning")
        await("Vem utövar den lagstiftande makten i Finland?")
        text("Riksdagen").performClick()
        runBlocking { app.container.settings.setStudyLanguage(StudyLanguage.FINNISH) }
        rule.activityRule.scenario.recreate()
        await("Vem utövar den lagstiftande makten i Finland?")
        text("Riksdagen").assertIsSelected()
        click("Check answer"); await("Correct")
        assertEquals("sv", runBlocking { repository.snapshot.first().attempts.single().language })
        click("Next question")
        await(planned.items[1].question.text)
        text("Question 2 / 5").assertExists()
    }
}
