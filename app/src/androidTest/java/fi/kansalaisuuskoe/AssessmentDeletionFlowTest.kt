package fi.kansalaisuuskoe

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
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
class AssessmentDeletionFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as CitizenshipApplication
    private val assessments get() = app.container.assessments
    private val learning get() = app.container.learning
    private fun text(value: String) = rule.onNodeWithText(value)
    private fun dialog(value: String) = rule.onNode(hasText(value) and hasAnyAncestor(isDialog()))
    private fun await(value: String) { rule.waitUntil(15_000) { rule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() } }
    private fun click(value: String) { rule.onNode(hasScrollAction()).performScrollToNode(hasText(value)); text(value).performClick() }
    @Before fun reset(): Unit = runBlocking {
        app.container.database.clearAllTables(); learning.initialize(app)
        app.container.settings.setInterfaceLanguage(InterfaceLanguage.ENGLISH)
        app.container.settings.setStudyLanguage(StudyLanguage.FINNISH)
        app.container.settings.setTheme(ThemePreference.LIGHT)
        val q = learning.snapshot.first().questions.first()
        learning.answer(q, q.correctOptionId, "learning-before-deletion", 500)
    }
    private fun completed(): String = runBlocking {
        val id = assessments.start(StudyLanguage.FINNISH, 1000)
        assessments.get(id)!!.questions.forEach { assessments.select(id, it.id, it.correctOptionId) }
        assessments.finish(id, 2000)
        id
    }
    private fun openResult() {
        await("Your learning path"); click("Practice assessment")
        click("View result and explanations"); await("Assessment result")
    }
    @Test fun resultDeletionCanBeCancelledAndRequiresFreshConfirmationAfterRecreation() {
        val id = completed()
        val before = runBlocking { learning.snapshot.first() }
        openResult(); click("Delete result"); await("Delete this result?")
        dialog("Correct 12 / 12 · 100 %").assertIsDisplayed()
        text("Keep assessment").performClick()
        assertNotNull(runBlocking { assessments.get(id) })
        click("Delete result"); await("Delete this result?")
        rule.activityRule.scenario.recreate(); await("Assessment result")
        text("Delete this result?").assertDoesNotExist()
        assertNotNull(runBlocking { assessments.get(id) })
        click("Delete result"); text("Delete").performClick()
        await("Completed assessments and their results will appear here.")
        assertNull(runBlocking { assessments.get(id) })
        assertEquals(before, runBlocking { learning.snapshot.first() })
        text("View result and explanations").assertDoesNotExist()
    }
    @Test fun unfinishedSwedishDiscardIsExplicitAndNewAssessmentStartsEmpty() {
        val id = runBlocking {
            app.container.settings.setStudyLanguage(StudyLanguage.SWEDISH)
            app.container.settings.setInterfaceLanguage(InterfaceLanguage.FINNISH)
            val id = assessments.start(StudyLanguage.SWEDISH, 1000)
            val q = assessments.get(id)!!.questions.first()
            assessments.select(id, q.id, q.correctOptionId); assessments.move(id, 1)
            id
        }
        val before = runBlocking { learning.snapshot.first() }
        await("Harjoittelukieli: ruotsi"); click("Harjoituskoe")
        click("Poista keskeneräinen harjoituskoe")
        await("Poistetaanko keskeneräinen harjoituskoe?")
        dialog("Harjoittelukieli: ruotsi").assertIsDisplayed()
        dialog("Tallennetut vastaukset: 1 / 12").assertIsDisplayed()
        text("Säilytä harjoituskoe").performClick()
        assertEquals(1, runBlocking { assessments.get(id)!!.position })
        click("Poista keskeneräinen harjoituskoe"); text("Poista").performClick()
        await("Aloita harjoituskoe")
        assertNull(runBlocking { assessments.get(id) })
        click("Aloita harjoituskoe"); await("Tallennetut vastaukset: 0 / 12")
        val fresh = runBlocking { assessments.sessions.first().single() }
        assertNotEquals(id, fresh.id)
        assertEquals("sv", fresh.language); assertEquals(0, fresh.position)
        assertEquals(before, runBlocking { learning.snapshot.first() })
    }
    @Test fun failedResultDeletionShowsRetryAndKeepsLearningData() {
        val id = completed()
        val before = runBlocking { learning.snapshot.first() }
        openResult()
        val sql = app.container.database.openHelper.writableDatabase
        sql.execSQL("CREATE TRIGGER fail_deletion_ui BEFORE DELETE ON assessments BEGIN SELECT RAISE(ABORT, 'test delete failure'); END")
        try {
            click("Delete result"); text("Delete").performClick()
            await("Could not delete the assessment. Your saved data is unchanged. You can try again.")
            assertNotNull(runBlocking { assessments.get(id) })
            assertEquals(before, runBlocking { learning.snapshot.first() })
            sql.execSQL("DROP TRIGGER fail_deletion_ui")
            click("Try again")
            await("Completed assessments and their results will appear here.")
            assertNull(runBlocking { assessments.get(id) })
            assertEquals(before, runBlocking { learning.snapshot.first() })
        } finally { sql.execSQL("DROP TRIGGER IF EXISTS fail_deletion_ui") }
    }
    @Test fun recordChangedWhileConfirmationIsOpenNeedsFreshReview() {
        val id = runBlocking { assessments.start(StudyLanguage.FINNISH, 1000) }
        await("Your learning path"); click("Practice assessment")
        click("Discard unfinished assessment"); await("Discard this unfinished assessment?")
        runBlocking { assessments.move(id, 1) }
        text("Delete").performClick()
        await("The assessment has changed. Nothing was deleted. Review its details and choose deletion again.")
        assertEquals(1, runBlocking { assessments.get(id)!!.position })
        text("Try again").assertDoesNotExist()
        click("Discard unfinished assessment"); text("Delete").performClick()
        await("Start assessment")
        assertNull(runBlocking { assessments.get(id) })
    }
}
