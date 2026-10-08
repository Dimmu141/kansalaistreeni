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

@RunWith(AndroidJUnit4::class)
class TopicRoutingFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as CitizenshipApplication
    private fun await(value: String) { rule.waitUntil(15_000) { rule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() } }
    private fun click(value: String) {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(value))
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

    @Test fun reviewTopicOpensFromItsLessonAndRestoresTheTopicQueue() = practiseImportedTopic("review")
    @Test fun allTopicOpensFromItsLessonAndCompletesOnlyThatTopic() = practiseImportedTopic("all")

    private fun practiseImportedTopic(topicId: String) {
        val content = ContentCodec.parse(app.assets.open("demo-content.json").bufferedReader().use { it.readText() })
        val original = content.topics.first()
        val questions = content.questions.filter { it.topicId == original.id && it.language == "fi" }.take(3)
            .map { it.copy(topicId = topicId, revision = it.revision + 1) }
        val updated = content.copy(packageId = "topic-routing-$topicId",
            topics = content.topics.map { if (it.id == original.id) it.copy(id = topicId) else it },
            questions = content.questions.filter { it.topicId != original.id } + questions)
        runBlocking { app.container.learning.importContent(Moshi.Builder().build().adapter(ContentPackageDto::class.java).toJson(updated)) }
        await("Your learning path")
        rule.onNodeWithText("Topics").performClick()
        click("Society and democracy")
        click("Practise this topic")
        questions.forEachIndexed { index, question ->
            await(question.text)
            rule.onNodeWithText("Question ${index + 1} / ${questions.size}").assertIsDisplayed()
            if (index == 0) {
                rule.activityRule.scenario.recreate()
                await(question.text)
            }
            click(question.options.first { it.id == question.correctOptionId }.text)
            click("Check answer")
            await("Correct")
            click(if (index == questions.lastIndex) "Show summary" else "Next question")
        }
        await("Practice complete")
        val attempts = runBlocking { app.container.learning.snapshot.first() }.attempts
        assertEquals(questions.map { it.id }.toSet(), attempts.map { it.questionId }.toSet())
        assertEquals(questions.size, attempts.size)
        assertTrue(attempts.all { it.topicId == topicId && it.correct })
    }
}
