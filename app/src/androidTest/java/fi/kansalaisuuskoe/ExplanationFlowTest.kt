package fi.kansalaisuuskoe

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.moshi.Moshi
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
class ExplanationFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as CitizenshipApplication
    private fun text(value: String) = rule.onNodeWithText(value)
    private fun await(value: String) { rule.waitUntil(15_000) { rule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() } }
    private fun click(value: String) {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(value)); text(value).performClick()
    }
    @Before fun reset() = runBlocking {
        app.container.database.clearAllTables(); app.container.learning.initialize(app)
        app.container.settings.setInterfaceLanguage(InterfaceLanguage.ENGLISH)
        app.container.settings.setStudyLanguage(StudyLanguage.FINNISH)
        app.container.settings.setExplanation(ExplanationPreference.STUDY_LANGUAGE)
        app.container.settings.setTheme(ThemePreference.LIGHT)
        app.container.settings.setEasyFinnish(true)
    }
    private fun chooseEnglishSupport() {
        await("Your learning path")
        rule.onNodeWithContentDescription("Settings").performClick(); click("Also in English")
        rule.waitUntil(10_000) { runBlocking { app.container.settings.settings.first().explanation == ExplanationPreference.ALSO_ENGLISH } }
        rule.activityRule.scenario.recreate(); await("Interface language")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Also in English"))
        text("Also in English").assertIsSelected()
        rule.onNodeWithContentDescription("Back").performClick(); await("Your learning path")
    }
    private fun checkLocale(value: String, language: String) {
        val annotated = text(value).fetchSemanticsNode().config[SemanticsProperties.Text].first { it.text == value }
        assertEquals(language, annotated.spanStyles.first { it.item.localeList != null }.item.localeList!![0].language)
    }
    @Test fun englishSupportAppearsAfterAnswerWithoutReplacingFinnishAndSurvivesRecreation() {
        chooseEnglishSupport(); click("Start learning")
        await("Kuka käyttää Suomessa lainsäädäntövaltaa?")
        text("Explanation in English").assertDoesNotExist()
        text("Who exercises legislative power in Finland?").assertDoesNotExist()
        checkLocale("Kuka käyttää Suomessa lainsäädäntövaltaa?", "fi")
        checkLocale("Eduskunta", "fi")
        text("Kuka käyttää Suomessa lainsäädäntövaltaa?").assert(SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit))
        text("Eduskunta").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertHeightIsAtLeast(56.dp)
        text("Eduskunta").assertIsDisplayed().performClick(); click("Check answer"); await("Correct")
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)).assertExists()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Explanation in English"))
        val english = "Parliament makes laws. The Government prepares legislative proposals."
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(english))
        text(english).assertIsDisplayed(); checkLocale(english, "en")
        rule.activityRule.scenario.recreate(); await("Correct")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(english)); text(english).assertIsDisplayed()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Eduskunta säätää lait. Hallitus valmistelee lakiesityksiä."))
        checkLocale("Eduskunta säätää lait. Hallitus valmistelee lakiesityksiä.", "fi")
        assertEquals(1, runBlocking { app.container.learning.snapshot.first().attempts.size })
    }
    @Test fun swedishStudyKeepsSwedishAlongsideEnglishSupport() {
        chooseEnglishSupport()
        runBlocking { app.container.settings.setStudyLanguage(StudyLanguage.SWEDISH) }
        await("Study language: Swedish"); click("Start learning")
        await("Vem utövar den lagstiftande makten i Finland?")
        checkLocale("Vem utövar den lagstiftande makten i Finland?", "sv")
        text("Riksdagen").performClick(); click("Check answer"); await("Correct")
        val english = "Parliament makes laws. The Government prepares legislative proposals."
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(english)); text(english).assertIsDisplayed()
        text("In easy Finnish").assertDoesNotExist()
        assertEquals("sv", runBlocking { app.container.learning.snapshot.first().attempts.single().language })
    }
    @Test fun importedQuestionWithoutEnglishShowsFallbackAndKeepsNativeExplanation() {
        chooseEnglishSupport()
        runBlocking {
            val seed = ContentCodec.parse(app.assets.open("demo-content.json").bufferedReader().use { it.readText() })
            val json = Moshi.Builder().build().adapter(ContentPackageDto::class.java).toJson(seed.copy(version = seed.version + 1,
                questions = seed.questions.map { it.copy(translations = emptyMap()) }))
            app.container.learning.confirmImport(app.container.learning.prepareImport(json))
        }
        click("Start learning"); await("Kuka käyttää Suomessa lainsäädäntövaltaa?")
        text("Eduskunta").performClick(); click("Check answer"); await("Correct")
        val missing = "This question has no English explanation. Read the explanation in your study language."
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(missing)); text(missing).assertIsDisplayed()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Eduskunta säätää lait. Hallitus valmistelee lakiesityksiä."))
        text("Eduskunta säätää lait. Hallitus valmistelee lakiesityksiä.").assertIsDisplayed()
    }
    @Test fun defaultPreferenceHidesEnglishSupport() {
        await("Your learning path"); click("Start learning"); await("Kuka käyttää Suomessa lainsäädäntövaltaa?")
        text("Eduskunta").performClick(); click("Check answer"); await("Correct")
        text("Explanation in English").assertDoesNotExist()
        text("Parliament makes laws. The Government prepares legislative proposals.").assertDoesNotExist()
        assertEquals(ExplanationPreference.STUDY_LANGUAGE, runBlocking { app.container.settings.settings.first().explanation })
    }
}
