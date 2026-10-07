package fi.kansalaisuuskoe

import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.kansalaisuuskoe.data.InterfaceLanguage
import fi.kansalaisuuskoe.data.ThemePreference
import fi.kansalaisuuskoe.domain.forLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class LocaleFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as CitizenshipApplication
    private val repository get() = app.container.learning
    private fun text(value: String) = rule.onNodeWithText(value)
    private fun await(value: String) { rule.waitUntil(15_000) { rule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() } }
    private fun click(value: String) {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(value))
        text(value).performClick()
    }

    @Before fun resetDemoState() = runBlocking {
        app.container.database.clearAllTables()
        repository.initialize(app); repository.installRegressionContent()
        app.container.settings.setInterfaceLanguage(InterfaceLanguage.FINNISH)
        app.container.settings.setStudyLanguage(fi.kansalaisuuskoe.domain.StudyLanguage.FINNISH)
        app.container.settings.setExplanation(fi.kansalaisuuskoe.data.ExplanationPreference.STUDY_LANGUAGE)
        app.container.settings.setTheme(ThemePreference.LIGHT)
        app.container.settings.setEasyFinnish(true)
    }

    private fun chooseEnglish() {
        await("Oma oppimispolkusi")
        rule.onNodeWithContentDescription("Asetukset").performClick()
        click("English · englanti")
        await("Interface language")
        text("English").assertIsSelected()
        rule.onNodeWithContentDescription("Back").performClick()
        await("Your learning path")
    }

    @Test fun englishMenusFeedbackReviewAndSettingsSurviveRecreation() {
        chooseEnglish()
        rule.onNodeWithContentDescription("Practice material mastery: 0 percent").assertExists()
        text("Topics").performClick()
        await("What would you like to learn?")
        click("Society and democracy"); click("Practise this topic")
        await("Kuka käyttää Suomessa lainsäädäntövaltaa?")
        text("Study language: Finnish").assertExists()
        click("Eduskunta")
        click("Check answer")
        await("Correct")
        rule.activityRule.scenario.recreate()
        await("Correct")
        click("In easy Finnish")
        text("Eduskunta päättää Suomen laeista.").assertExists()
        click("Important vocabulary")
        text("English: power to make laws").assertExists()
        click("Next question")
        await("Kuinka monta kansanedustajaa Suomen eduskunnassa on?")
        click("150")
        click("Check answer")
        await("Not quite — let’s learn from it")
        click("Show summary")
        await("Practice complete")
        text("Correct: 1 / 2. Your answers and progress are saved on this device.").assertExists()
        click("Return to your learning path")
        await("Your learning path")
        text("Review").performClick()
        await("No reviews due right now")
        rule.onNode(hasText("Next review:", substring = true)).assertExists()
        runBlocking {
            val question = repository.snapshot.first().questions.first { it.id == "demo-emergency" }
            repository.answer(question, "b", "overdue", System.currentTimeMillis() - 660_000)
        }
        await("Start review · 1 question")
        click("Start review · 1 question")
        await("Mihin numeroon soitat kiireellisessä hätätilanteessa Suomessa?")
        click("112")
        click("Check answer")
        await("Correct")
        click("Show summary")
        click("Return to your learning path")
        await("Your learning path")
        text("Progress").performClick()
        await("Your learning progress")
        click("Answer accuracy")
        text("50%").assertExists()
        rule.onNodeWithContentDescription("Settings").performClick()
        await("Interface language")
        text("English").assertIsSelected()
        rule.activityRule.scenario.recreate()
        await("Interface language")
        text("English").assertIsSelected()
        assertEquals(InterfaceLanguage.ENGLISH, runBlocking { app.container.settings.settings.first().interfaceLanguage })
        click("Suomi · Finnish")
        await("Käyttöliittymän kieli")
        text("Suomi").assertIsSelected()
        assertEquals(4, runBlocking { repository.snapshot.first().attempts.size })
    }

    @Test fun changingLanguageDuringFeedbackKeepsSessionAndDoesNotRecordAgain() {
        chooseEnglish()
        val planned = runBlocking { fi.kansalaisuuskoe.domain.DailyLearningPlanner.plan(repository.snapshot.first().forLanguage(fi.kansalaisuuskoe.domain.StudyLanguage.FINNISH), System.currentTimeMillis()) }
        click("Start learning")
        await("Kuka käyttää Suomessa lainsäädäntövaltaa?")
        click("Eduskunta")
        click("Check answer")
        await("Correct")
        runBlocking { app.container.settings.setInterfaceLanguage(InterfaceLanguage.FINNISH) }
        await("Oikein")
        click("Seuraava kysymys")
        await(planned.items[1].question.text)
        text("Kysymys 2 / 5").assertExists()
        assertEquals(1, runBlocking { repository.snapshot.first().attempts.size })
    }

    @Test fun translatedPluralErrorsAndDatePatternUseExplicitLocale() {
        val context = rule.activity
        fun resources(tag: String) = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }).resources
        val english = resources("en")
        val finnish = resources("fi")
        assertEquals("1 question", english.getQuantityString(R.plurals.question_count, 1, 1))
        assertEquals("2 questions", english.getQuantityString(R.plurals.question_count, 2, 2))
        assertEquals("1 kysymys", finnish.getQuantityString(R.plurals.question_count, 1, 1))
        assertEquals("2 kysymystä", finnish.getQuantityString(R.plurals.question_count, 2, 2))
        assertEquals("1 topic", english.getQuantityString(R.plurals.topic_count, 1, 1))
        assertEquals("2 topics", english.getQuantityString(R.plurals.topic_count, 2, 2))
        assertEquals("1 aihe", finnish.getQuantityString(R.plurals.topic_count, 1, 1))
        assertEquals("2 aihetta", finnish.getQuantityString(R.plurals.topic_count, 2, 2))
        assertEquals("1 / 1 question practised", english.getQuantityString(R.plurals.studied_count, 1, 1, 1))
        assertEquals("1 / 1 kysymys harjoiteltu", finnish.getQuantityString(R.plurals.studied_count, 1, 1, 1))
        assertEquals("Harjoitusmateriaalin osaaminen 1 prosentti", finnish.getQuantityString(R.plurals.mastery_accessibility, 1, 1))
        assertEquals("Your answer could not be saved. Try again.", english.getString(R.string.error_save_answer))
        assertEquals("The setting could not be saved. Try again.", english.getString(R.string.error_save_setting))
        val date = Instant.parse("2026-10-07T12:00:00Z").atZone(ZoneId.of("UTC"))
        assertEquals("7 Oct 2026 at 12:00", date.format(DateTimeFormatter.ofPattern(english.getString(R.string.date_time_pattern), Locale.ENGLISH)))
        assertEquals("7.10.2026 klo 12.00", date.format(DateTimeFormatter.ofPattern(finnish.getString(R.string.date_time_pattern), Locale.forLanguageTag("fi"))))
    }
}
