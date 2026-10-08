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

/** Settings → Study material → coverage maps objectives to installed questions without recording learning. */
@RunWith(AndroidJUnit4::class)
class CoverageFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as CitizenshipApplication
    private fun await(value: String) { rule.waitUntil(15_000) { rule.onAllNodesWithText(value, substring = true).fetchSemanticsNodes().isNotEmpty() } }
    private fun click(value: String) {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(value))
        rule.onAllNodesWithText(value).onFirst().performClick()
    }

    @Before fun reset(): Unit = runBlocking {
        app.container.database.clearAllTables()
        app.container.learning.initialize(app)
        app.container.settings.setInterfaceLanguage(InterfaceLanguage.ENGLISH)
        app.container.settings.setStudyLanguage(StudyLanguage.FINNISH)
        app.container.settings.setTheme(ThemePreference.LIGHT)
    }

    @Test fun coverageListsObjectivesWithCountsAndHighlightsThinOnes() {
        val content = ContentCodec.parse(app.assets.open("demo-content.json").bufferedReader().use { it.readText() })
        val topic = content.topics.first()
        val objectives = listOf(
            ObjectiveDto("cov-wide", topic.id, mapOf("fi" to "Laajasti harjoiteltu", "sv" to "Brett övad", "en" to "Widely practised")),
            ObjectiveDto("cov-thin", topic.id, mapOf("fi" to "Vähän harjoiteltu", "sv" to "Lite övad", "en" to "Rarely practised")),
        )
        val topicFinnish = content.questions.filter { it.topicId == topic.id && it.language == "fi" }.map { it.id }
        val questions = content.questions.map { q ->
            q.copy(revision = q.revision + 1, objectiveIds = listOf(if (q.id == topicFinnish.first()) "cov-thin" else "cov-wide"))
        }
        val updated = content.copy(schemaVersion = 2, version = content.version + 1, objectives = objectives, questions = questions)
        runBlocking { app.container.learning.importContent(Moshi.Builder().build().adapter(ContentPackageDto::class.java).toJson(updated)) }
        val before = runBlocking { app.container.learning.snapshot.first() }

        await("Your learning path")
        rule.onNodeWithContentDescription("Settings").performClick()
        click("Study material")
        click("Objective coverage")
        await("Widely practised")
        rule.onNodeWithText("FI 1 · SV 0").assertExists()
        rule.onNodeWithText("Fewer than 3 questions").assertExists()
        rule.onNodeWithText("2 learning objectives. Objectives with fewer than 3 questions: 1.").assertExists()
        rule.activityRule.scenario.recreate()
        await("Rarely practised")
        assertEquals(before, runBlocking { app.container.learning.snapshot.first() })
    }
}
