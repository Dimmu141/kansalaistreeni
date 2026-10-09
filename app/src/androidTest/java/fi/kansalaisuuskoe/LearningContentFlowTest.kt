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
class LearningContentFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as CitizenshipApplication
    private fun text(value: String) = rule.onNodeWithText(value)
    private fun await(value: String) { rule.waitUntil(15_000) { rule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() } }
    private fun click(value: String) { rule.onNode(hasScrollAction()).performScrollToNode(hasText(value)); text(value).performClick() }
    private fun snapshot() = runBlocking { app.container.learning.snapshot.first() }
    private fun search(value: String) { rule.onNode(hasSetTextAction()).performTextReplacement(value); rule.waitForIdle() }
    @Before fun reset() = runBlocking {
        app.container.database.clearAllTables(); app.container.learning.initialize(app); app.container.learning.installRegressionContent()
        app.container.settings.setInterfaceLanguage(InterfaceLanguage.ENGLISH)
        app.container.settings.setStudyLanguage(StudyLanguage.FINNISH)
        app.container.settings.setExplanation(ExplanationPreference.ALSO_ENGLISH)
        app.container.settings.setEasyFinnish(true)
        app.container.settings.setTheme(ThemePreference.LIGHT)
    }
    private fun openWords() { await("Your learning path"); text("Topics").performClick(); click("Vocabulary"); await("Search a word or meaning") }

    @Test fun topicReadingShowsNativeExplanationAndSourceWithoutRecordingAnswers() {
        await("Your learning path"); text("Topics").performClick(); click("Society and democracy")
        await("Practise this topic")
        val before = snapshot()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Eduskunta säätää lait. Hallitus valmistelee lakiesityksiä."))
        text("Eduskunta säätää lait. Hallitus valmistelee lakiesityksiä.").assertIsDisplayed()
        click("Parliament makes laws. The Government prepares legislative proposals.")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("https://www.finlex.fi/fi/lainsaadanto/1999/731"))
        rule.onAllNodesWithText("https://www.finlex.fi/fi/lainsaadanto/1999/731").onFirst().assertIsDisplayed()
        rule.activityRule.scenario.recreate()
        assertEquals(before, snapshot())
        click("Practise this topic"); await("Kuka käyttää Suomessa lainsäädäntövaltaa?")
        text("Eduskunta").performClick(); click("Check answer")
        assertEquals(1, snapshot().attempts.size)
    }
    @Test fun searchAndContextRestoreWithoutChangingProgress() {
        openWords(); search("power"); await("lainsäädäntövalta")
        val before = snapshot()
        click("Show explanation and source"); click("Parliament makes laws. The Government prepares legislative proposals.")
        rule.activityRule.scenario.recreate(); await("Hide explanation and source")
        assertEquals(before, snapshot())
        rule.onNode(hasScrollAction()).performScrollToNode(hasSetTextAction())
        rule.onNode(hasSetTextAction()).assertTextContains("power")
        search("no match xyz"); await("No words found. Try another search or remove the topic filter.")
        click("Clear search and topic filter"); await("Practise words")
    }
    @Test fun filteredWordExerciseRestoresRevealAndReturnsToSearchUnscored() {
        openWords(); search("power"); await("lainsäädäntövalta"); click("Practise words")
        await("Show meaning")
        text("power to make laws").assertDoesNotExist()
        val before = snapshot()
        click("Show meaning"); await("power to make laws")
        rule.activityRule.scenario.recreate(); await("Finish exercise")
        text("power to make laws").assertIsDisplayed()
        click("Finish exercise"); await("Word practice complete")
        click("Return to vocabulary"); await("Search a word or meaning")
        rule.onNode(hasSetTextAction()).assertTextContains("power")
        assertEquals(before, snapshot())
    }
    @Test fun nextWordStartsHiddenAndPositionSurvivesRecreation() {
        openWords(); click("Practise words"); await("Word 1 / 10")
        click("Show meaning"); click("Next word"); await("Word 2 / 10")
        text("Meaning in English").assertDoesNotExist()
        rule.activityRule.scenario.recreate(); await("Word 2 / 10")
        text("Show meaning").assertIsDisplayed()
        assertTrue(snapshot().attempts.isEmpty())
    }
    @Test fun topicVocabularyFiltersAndRestoresAcrossWordPractice() {
        await("Your learning path"); text("Topics").performClick(); click("Society and democracy")
        click("Vocabulary for this topic"); await("Topic: Society and democracy")
        text("2 words").assertIsDisplayed()
        click("Practise words"); await("Word 1 / 2")
        rule.onNodeWithContentDescription("Back").performClick(); await("Topic: Society and democracy")
        rule.activityRule.scenario.recreate(); await("Topic: Society and democracy")
        click("Clear search and topic filter"); await("Topic: All topics")
    }
    @Test fun SwedishVocabularyKeepsNativeTermAlongsideFinnishAndEnglish() {
        runBlocking { app.container.settings.setStudyLanguage(StudyLanguage.SWEDISH) }
        openWords(); search("power"); await("lagstiftande makt")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("lainsäädäntövalta"))
        text("In Finnish").assertIsDisplayed(); text("lainsäädäntövalta").assertIsDisplayed()
        click("Show explanation and source")
        click("Riksdagen stiftar lagarna. Regeringen bereder lagförslag.")
        assertTrue(snapshot().attempts.isEmpty())
    }
    @Test fun importedVocabularyReplacesBrowseResultsWithoutChangingHistory() {
        openWords(); search("power"); await("lainsäädäntövalta")
        val content = regressionContent()
        val revised = content.copy(version = content.version + 1, questions = content.questions.map {
            if (it.id == "demo-parliament") it.copy(revision = it.revision + 1, vocabulary = listOf(VocabularyDto("hallinto", "governance", "förvaltning"))) else it
        })
        runBlocking { app.container.learning.importContent(com.squareup.moshi.Moshi.Builder().build().adapter(ContentPackageDto::class.java).toJson(revised)) }
        await("No words found. Try another search or remove the topic filter.")
        search("governance"); await("hallinto"); text("lainsäädäntövalta").assertDoesNotExist()
        assertTrue(snapshot().attempts.isEmpty())
    }
    @Test fun importedPackageWithoutSwedishVocabularyShowsHonestEmptyState() {
        val content = regressionContent()
        val revised = content.copy(version = content.version + 1, questions = content.questions.map {
            if (it.language == "sv") it.copy(revision = it.revision + 1, vocabulary = emptyList()) else it
        })
        runBlocking {
            app.container.learning.importContent(com.squareup.moshi.Moshi.Builder().build().adapter(ContentPackageDto::class.java).toJson(revised))
            app.container.settings.setStudyLanguage(StudyLanguage.SWEDISH)
        }
        openWords(); await("Vocabulary is not available in this study language yet.")
        text("Practise words").assertDoesNotExist()
        assertTrue(snapshot().progress.isEmpty())
    }
}
