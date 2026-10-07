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
class AssessmentFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as CitizenshipApplication
    private val assessments get() = app.container.assessments
    private val learning get() = app.container.learning
    private fun text(value: String) = rule.onNodeWithText(value)
    private fun await(value: String) { rule.waitUntil(15_000) { rule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() } }
    private fun click(value: String) { rule.onNode(hasScrollAction()).performScrollToNode(hasText(value)); text(value).performClick() }
    private fun saved(id: String) = runBlocking { assessments.get(id)!! }
    private fun choose(id: String, question: Question, option: String) {
        click(question.options.first { it.id == option }.text)
        rule.waitUntil(15_000) { saved(id).answers[question.id] == option }
    }
    @Before fun reset() = runBlocking {
        app.container.database.clearAllTables(); learning.initialize(app)
        app.container.settings.setInterfaceLanguage(InterfaceLanguage.ENGLISH)
        app.container.settings.setStudyLanguage(StudyLanguage.FINNISH)
        app.container.settings.setExplanation(ExplanationPreference.ALSO_ENGLISH)
        app.container.settings.setEasyFinnish(true)
        app.container.settings.setTheme(ThemePreference.LIGHT)
    }

    @Test fun mixedAssessmentResumesAllowsEditsDefersFeedbackAndPractisesOnlyMistakes() {
        await("Your learning path"); click("Practice assessment"); click("Start assessment")
        rule.waitUntil(15_000) { runBlocking { assessments.sessions.first().isNotEmpty() } }
        val id = runBlocking { assessments.sessions.first().single().id }
        val initial = saved(id)
        assertEquals(12, initial.questions.size)
        val first = initial.questions.first()
        await(first.text)
        click("Next question") // Disabled until an answer is saved.
        assertEquals(0, saved(id).position)
        choose(id, first, first.options.first { it.id != first.correctOptionId }.id)
        text("Correct answer").assertDoesNotExist()
        text("Why?").assertDoesNotExist()
        text(first.explanation).assertDoesNotExist()
        click("Next question"); await(initial.questions[1].text)
        click("Previous question"); await(first.text)
        choose(id, first, first.correctOptionId)
        click("Next question"); await(initial.questions[1].text)
        val second = initial.questions[1]
        choose(id, second, second.correctOptionId)
        rule.activityRule.scenario.recreate()
        await(second.text)
        assertEquals(1, saved(id).position)
        assertEquals(second.correctOptionId, saved(id).answers[second.id])
        rule.onNodeWithContentDescription("Back").performClick()
        click("Resume assessment"); await(second.text)
        assertEquals(initial.questions, saved(id).questions)
        click("Next question")
        val mistake = initial.questions.last()
        for (index in 2 until initial.questions.size) {
            val q = initial.questions[index]
            await(q.text)
            choose(id, q, if (q.id == mistake.id) q.options.first { it.id != q.correctOptionId }.id else q.correctOptionId)
            click(if (index == initial.questions.lastIndex) "Finish and view result" else "Next question")
        }
        await("Assessment result")
        text("Correct 11 / 12 · 91 %").assertIsDisplayed()
        assertTrue(runBlocking { learning.snapshot.first().attempts.isEmpty() })
        assertTrue(runBlocking { learning.snapshot.first().progress.isEmpty() })
        val description = "Question explanation: ${mistake.text}"
        rule.onNode(hasScrollAction()).performScrollToNode(hasContentDescription(description))
        rule.onNodeWithContentDescription(description).performClick()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(mistake.explanation))
        text(mistake.explanation).assertIsDisplayed()
        val english = mistake.translations.getValue("en").explanation
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(english)); text(english).assertIsDisplayed()
        rule.activityRule.scenario.recreate(); await("Hide explanation")
        assertEquals(11, Assessments.result(saved(id)).correct)
        click("Practise mistakes · 1 question"); await(mistake.text)
        click(mistake.options.first { it.id == mistake.correctOptionId }.text); click("Check answer")
        await("Correct"); click("Show summary"); await("Practice complete")
        val snapshot = runBlocking { learning.snapshot.first() }
        assertEquals(listOf(mistake.id), snapshot.attempts.map { it.questionId })
        assertEquals(25, snapshot.progress.single().mastery)
        assertEquals(11, Assessments.result(saved(id)).correct)
        click("Return to your learning path"); await("Your learning path")
        click("Practice assessment"); click("View result and explanations"); await("Assessment result")
        text("Correct 11 / 12 · 91 %").assertIsDisplayed()
    }

    @Test fun swedishAssessmentAndFollowUpKeepTheirLanguageWhenPreferenceChanges() {
        runBlocking {
            val content = ContentCodec.parse(app.assets.open("demo-content.json").bufferedReader().use { it.readText() })
            val selected = content.questions.filter { it.language == "sv" }.take(3) + content.questions.first { it.language == "fi" }
            learning.importContent(Moshi.Builder().build().adapter(ContentPackageDto::class.java).toJson(content.copy(version = 5, questions = selected)))
            app.container.settings.setStudyLanguage(StudyLanguage.SWEDISH)
            app.container.settings.setInterfaceLanguage(InterfaceLanguage.FINNISH)
        }
        await("Harjoittelukieli: ruotsi"); click("Harjoituskoe"); click("Aloita harjoituskoe")
        rule.waitUntil(15_000) { runBlocking { assessments.sessions.first().isNotEmpty() } }
        val id = runBlocking { assessments.sessions.first().single().id }
        val questions = saved(id).questions
        for ((index, q) in questions.withIndex()) {
            await(q.text)
            choose(id, q, if (index == 0) q.options.first { it.id != q.correctOptionId }.id else q.correctOptionId)
            click(if (index == questions.lastIndex) "Lopeta ja näytä tulos" else "Seuraava kysymys")
        }
        await("Harjoituskokeen tulos")
        text("Oikein 2 / 3 · 66 %").assertIsDisplayed()
        runBlocking { app.container.settings.setStudyLanguage(StudyLanguage.FINNISH) }
        click("Harjoittele virheitä · 1 kysymys"); await(questions.first().text)
        click(questions.first().options.first { it.id == questions.first().correctOptionId }.text)
        click("Tarkista vastaus"); await("Oikein")
        val attempts = runBlocking { learning.snapshot.first().attempts }
        assertEquals("sv", attempts.single().language)
        assertEquals(questions.first().id, attempts.single().questionId)
    }

    @Test fun failedAutosaveCanBeRetriedWithoutLosingOrDuplicatingTheChoice() {
        await("Your learning path"); click("Practice assessment"); click("Start assessment")
        rule.waitUntil(15_000) { runBlocking { assessments.sessions.first().isNotEmpty() } }
        val id = runBlocking { assessments.sessions.first().single().id }
        val q = saved(id).questions.first()
        await(q.text)
        val sql = app.container.database.openHelper.writableDatabase
        sql.execSQL("CREATE TRIGGER assessment_write_failure BEFORE INSERT ON assessments BEGIN SELECT RAISE(ABORT, 'verification failure'); END")
        try {
            click(q.options.first { it.id == q.correctOptionId }.text)
            rule.onNode(hasScrollAction()).performScrollToNode(hasText("Next question"))
            await("Could not process the assessment. Try again. Previously saved choices are retained.")
            assertTrue(saved(id).answers.isEmpty())
            sql.execSQL("DROP TRIGGER assessment_write_failure")
            click("Try again")
            rule.waitUntil(15_000) { saved(id).answers[q.id] == q.correctOptionId }
            assertEquals(1, saved(id).answeredCount)
            assertTrue(runBlocking { learning.snapshot.first().attempts.isEmpty() })
            click("Next question"); await(saved(id).questions[1].text)
        } finally { sql.execSQL("DROP TRIGGER IF EXISTS assessment_write_failure") }
    }

    @Test fun singleQuestionMaterialFinishesAndAllCorrectResultHasNoMistakePractice() {
        val question = runBlocking {
            val content = ContentCodec.parse(app.assets.open("demo-content.json").bufferedReader().use { it.readText() })
            val one = content.questions.first { it.id == "demo-emergency" }
            learning.importContent(Moshi.Builder().build().adapter(ContentPackageDto::class.java).toJson(content.copy(version = 5, questions = listOf(one))))
            one.model()
        }
        await("Your learning path"); click("Practice assessment"); click("Start assessment"); await(question.text)
        val id = runBlocking { assessments.sessions.first().single().id }
        assertEquals(3, question.options.size)
        text("Saved answers: 0 / 1").assertIsDisplayed()
        choose(id, question, question.correctOptionId)
        text("Saved answers: 1 / 1").assertIsDisplayed()
        click("Finish and view result"); await("Assessment result")
        text("Correct 1 / 1 · 100 %").assertIsDisplayed()
        text("All correct. Continue learning by topic and come back for review later.").assertIsDisplayed()
        rule.onNode(hasText("Practise mistakes", substring = true)).assertDoesNotExist()
        assertTrue(runBlocking { learning.snapshot.first().attempts.isEmpty() })
    }
}
