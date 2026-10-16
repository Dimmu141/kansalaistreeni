package fi.kansalaisuuskoe

import kotlin.math.roundToInt
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.moshi.Moshi
import fi.kansalaisuuskoe.data.*
import fi.kansalaisuuskoe.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthoredLessonFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as CitizenshipApplication
    private fun snapshot() = runBlocking { app.container.learning.snapshot.first() }
    private fun text(value: String) = rule.onNodeWithText(value)
    private fun await(value: String) { rule.waitUntil(15_000) { rule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() } }
    private fun show(value: String) { runCatching { rule.onNode(hasScrollAction()).performScrollToNode(hasText(value)) }; rule.onAllNodesWithText(value).onFirst().assertIsDisplayed() }
    private fun click(value: String) { show(value); rule.onAllNodesWithText(value).onFirst().performClick() }
    private fun openTopic(name: String = "Democracy and decision-making") { await("Your learning path"); text("Topics").performClick(); click(name) }
    @Before fun reset(): Unit = runBlocking {
        app.container.database.clearAllTables(); app.container.learning.initialize(app)
        app.container.settings.setInterfaceLanguage(InterfaceLanguage.ENGLISH)
        app.container.settings.setStudyLanguage(StudyLanguage.FINNISH)
        app.container.settings.setExplanation(ExplanationPreference.ALSO_ENGLISH)
        app.container.settings.setEasyFinnish(true)
        app.container.settings.setTheme(ThemePreference.LIGHT)
    }
    @Test fun authoredFinnishEasyAndEnglishReadingRestoresWithoutScoring() {
        val before = snapshot(); val topic = before.topics.first(); val lesson = topic.lessons.getValue("fi")
        openTopic()
        lesson.sections.forEach { section -> show(section.body); show(section.easyBody!!) }
        val english = topic.lessons.getValue("en").sections.last().body
        show(english)
        rule.activityRule.scenario.recreate(); show(english)
        assertEquals(before, snapshot())
        click("Vocabulary for this topic"); await("Topic: Democracy and decision-making"); show("${LearningContent.vocabulary(before, StudyLanguage.FINNISH).count { w -> w.contexts.any { it.topicId == topic.id } }} words")
        assertEquals(before, snapshot())
    }
    @Test fun SwedishLessonUsesSwedishWithEnglishSupportAndNoEasyFinnish() {
        runBlocking { app.container.settings.setStudyLanguage(StudyLanguage.SWEDISH) }
        val before = snapshot(); val topic = before.topics.first()
        openTopic("Demokrati och beslutsfattande")
        topic.lessons.getValue("sv").sections.forEach { section -> show(section.body); text("In easy Finnish").assertDoesNotExist() }
        show(topic.lessons.getValue("en").sections.last().body)
        assertEquals(before, snapshot())
    }
    @Test fun preferencesHideSupportAndFullTopicActuallyCompletes() {
        runBlocking { app.container.settings.setExplanation(ExplanationPreference.STUDY_LANGUAGE); app.container.settings.setEasyFinnish(false) }
        openTopic(); show(snapshot().topics.first().lessons.getValue("fi").sections.first().body)
        text("In easy Finnish").assertDoesNotExist(); text("Explanation in English").assertDoesNotExist()
        click("Practise this topic")
        val questions = snapshot().questions.filter { it.language == "fi" && it.topicId == "democracy" }
        questions.forEachIndexed { index, q ->
            await(q.text); click(rule.activity.answerLabel(q, q.correctOptionId)); click("Check answer")
            await("Correct"); show(q.explanation)
            click(if (index == questions.lastIndex) "Show summary" else "Next question")
        }
        await("Practice complete")
        val result = snapshot().forLanguage(StudyLanguage.FINNISH)
        assertEquals(questions.size, result.attempts.size); assertTrue(result.attempts.all { it.correct })
        val summary = LearningEngine.summarize(result, System.currentTimeMillis())
        assertEquals(100, summary.accuracy); assertEquals(25, summary.topics.first().mastery)
        // One correct answer gives each topic question 25 mastery; the rest of the material stays at 0.
        assertEquals((25.0 * questions.size / result.questions.size).roundToInt(), summary.mastery); assertEquals(0, summary.dueCount)
    }
    @Test fun importingOldStyleReadingFallsBackAndCannotKeepADeletedAuthoredLesson() {
        openTopic(); val before = snapshot()
        val c = ContentCodec.parse(app.assets.open("demo-content.json").bufferedReader().use { it.readText() })
        val updated = c.copy(version = c.version + 1, topics = c.topics.map { it.copy(lessons = emptyMap()) })
        runBlocking { app.container.learning.importContent(Moshi.Builder().build().adapter(ContentPackageDto::class.java).toJson(updated)) }
        show(before.questions.first().explanation)
        text(before.topics.first().lessons.getValue("fi").sections.first().body).assertDoesNotExist()
        assertEquals(before.attempts, snapshot().attempts); assertEquals(before.progress, snapshot().progress)
    }
}
