package fi.kansalaisuuskoe

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.moshi.Moshi
import fi.kansalaisuuskoe.data.*
import fi.kansalaisuuskoe.domain.QuestionFormat
import fi.kansalaisuuskoe.domain.StudyLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SwedishInterfaceFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as CitizenshipApplication
    private fun await(value: String) { rule.waitUntil(15_000) { rule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() } }
    private fun click(value: String) { rule.onNode(hasScrollAction()).performScrollToNode(hasText(value)); rule.onAllNodesWithText(value).onFirst().performClick() }
    @Before fun reset(): Unit = runBlocking {
        app.container.database.clearAllTables(); app.container.learning.initialize(app)
        app.container.settings.setTheme(ThemePreference.LIGHT)
        app.container.settings.setStudyLanguage(StudyLanguage.SWEDISH)
        app.container.settings.setInterfaceLanguage(InterfaceLanguage.SWEDISH)
        app.container.settings.setExplanation(ExplanationPreference.STUDY_LANGUAGE)
    }

    @Test fun swedishTrueFalseQuestionGetsSwedishLabelsAndFeedback() {
        val statement = runBlocking {
            val content = ContentCodec.parse(app.assets.open("demo-content.json").bufferedReader().use { it.readText() })
            val one = content.questions.first { it.language == "sv" && it.format == QuestionFormat.TRUE_FALSE.name }
            app.container.learning.importContent(Moshi.Builder().build().adapter(ContentPackageDto::class.java).toJson(content.copy(version = content.version + 1, questions = listOf(one))))
            one.model()
        }
        await("Din inlärningsväg"); rule.onNodeWithText("Inte officiellt material").assertIsDisplayed()
        click("Börja lära dig"); await(statement.text)
        rule.onNodeWithText("Påstående. Är det rätt eller fel?").assertExists()
        click(rule.activity.answerLabel(statement, statement.correctOptionId)); click("Kontrollera svaret")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(statement.explanation))
        rule.onNodeWithText(statement.explanation).assertIsDisplayed()
        val attempt = runBlocking { app.container.learning.snapshot.first().attempts.single() }
        assertEquals("sv", attempt.language); assertEquals(true, attempt.correct)
    }
}
