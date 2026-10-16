package fi.kansalaisuuskoe

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.kansalaisuuskoe.data.*
import fi.kansalaisuuskoe.domain.LearningSnapshot
import fi.kansalaisuuskoe.domain.StudyLanguage
import fi.kansalaisuuskoe.ui.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.*
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class BackupFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as CitizenshipApplication
    private val repository get() = app.container.learning
    private lateinit var selected: String
    private var exported: String? = null
    private var writeFails = false
    private fun snapshot() = runBlocking { repository.snapshot.first() }
    private fun await(value: String) { rule.waitUntil(15_000) { rule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() } }
    private fun click(value: String) { runCatching { rule.onNode(hasScrollAction()).performScrollToNode(hasText(value)) }; rule.onNodeWithText(value).performClick() }
    @Before fun setup() {
        runBlocking {
            app.container.database.clearAllTables(); repository.initialize(app)
            val q = repository.snapshot.first().questions.first()
            repository.answer(q, q.correctOptionId, "backed-up", 1000)
            selected = repository.exportBackup(2000)
            repository.answer(q, q.correctOptionId, "newer", 3000)
        }
        installBody()
        await("Saved answers across both languages: 2")
    }
    private fun installBody() {
        rule.activityRule.scenario.onActivity { activity ->
            val model = ViewModelProvider(activity, object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return BackupViewModel(repository) as T
                }
            })[BackupViewModel::class.java]
            activity.setContent {
                InterfaceLocale(InterfaceLanguage.ENGLISH) {
                    CitizenshipTheme(ThemePreference.LIGHT) {
                        val state by model.state.collectAsState()
                        val snapshot by repository.snapshot.collectAsState(LearningSnapshot())
                        BackupBody(state, snapshot.attempts.size,
                            { model.export { if (writeFails) throw IOException("Test write failure") else exported = it } },
                            { model.select { selected } }, model::discard, model::restore)
                    }
                }
            }
        }
    }
    private fun savedAssessments() = runBlocking {
        val assessments = app.container.assessments
        val fi = assessments.start(StudyLanguage.FINNISH, 1000)
        assessments.get(fi)!!.questions.forEach { assessments.select(fi, it.id, it.correctOptionId) }
        assessments.finish(fi, 2000)
        val sv = assessments.start(StudyLanguage.SWEDISH, 2500)
        val q = assessments.get(sv)!!.questions.first()
        assessments.select(sv, q.id, q.correctOptionId); assessments.move(sv, 1)
        assessments.sessions.first()
    }
    @Test fun portableAssessmentPreviewSurvivesRecreationAndRestoresCompletedAndUnfinishedSessions() {
        val expected = savedAssessments()
        runBlocking {
            selected = repository.exportBackup(3000)
            app.container.assessments.start(StudyLanguage.FINNISH, 4000)
        }
        click("Choose a backup to restore"); await("Review the restore")
        click("Completed assessments: 1. Unfinished: 1.")
        rule.onNodeWithText("Restoration also replaces all assessments saved on this device, including their answers and results. Assessments now: 3. After restoration: 2.").assertExists()
        assertEquals(3, runBlocking { app.container.assessments.sessions.first().size })
        rule.activityRule.scenario.recreate(); installBody()
        click("Completed assessments: 1. Unfinished: 1.")
        click("Restore backup"); await("Backup restored. Mastery and review dates have been recalculated.")
        assertEquals(expected, runBlocking { app.container.assessments.sessions.first() })
        assertEquals(2, snapshot().attempts.size)
    }
    @Test fun olderBackupExplainsThatAssessmentsRemainOnDevice() {
        val expected = savedAssessments()
        selected = BackupCodec.encode(BackupCodec.parse(selected).copy(schemaVersion = 1, assessments = null))
        click("Choose a backup to restore"); await("Review the restore")
        click("This is an older backup without assessments. Assessments saved on this device will stay unchanged.")
        rule.onNodeWithText("Completed assessments: 1. Unfinished: 1.").assertDoesNotExist()
        click("Restore backup"); await("Backup restored. Mastery and review dates have been recalculated.")
        assertEquals(expected, runBlocking { app.container.assessments.sessions.first() })
        assertEquals(1, snapshot().attempts.size)
    }
    @Test fun assessmentEditAfterPreviewRequiresFreshReviewWithoutReplacingAnyData() {
        val expected = savedAssessments()
        runBlocking { selected = repository.exportBackup(3000) }
        click("Choose a backup to restore"); await("Review the restore")
        runBlocking { app.container.assessments.move(expected.first { !it.complete }.id, 2) }
        click("Restore backup"); await("Your saved data changed after the preview. Select the file again and review the restore.")
        rule.onNodeWithText("Restore backup").assertDoesNotExist()
        assertEquals(2, runBlocking { app.container.assessments.get(expected.first { !it.complete }.id)!!.position })
        assertEquals(2, snapshot().attempts.size)
    }
    @Test fun exportReportsSuccessOnlyAfterWritingAndDoesNotChangeHistory() {
        val before = snapshot()
        click("Save a backup"); await("Backup saved.")
        assertEquals(before, snapshot())
        assertEquals(2, BackupCodec.parse(requireNotNull(exported)).attempts.size)
    }
    @Test fun failedWriteDoesNotReportSuccessAndCanBeRetried() {
        val before = snapshot(); writeFails = true
        click("Save a backup")
        await("The backup could not be saved. Delete any incomplete file and try again. Your data in the app is still saved.")
        rule.onNodeWithText("Backup saved.").assertDoesNotExist()
        assertNull(exported); assertEquals(before, snapshot())
        writeFails = false; click("Save a backup"); await("Backup saved.")
        assertNotNull(exported)
    }
    @Test fun previewAndCancelKeepCurrentMaterialHistoryAndScores() {
        val before = snapshot(); click("Choose a backup to restore"); await("Review the restore")
        rule.onNodeWithText("Your current study material and entire answer history will be replaced. Saved answers now: 2. In this backup: 1. Answers given since this backup will be removed.").assertExists()
        assertEquals(before, snapshot())
        click("Cancel restore"); await("Choose a backup to restore")
        assertEquals(before, snapshot())
    }
    @Test fun explicitConfirmationReplacesHistoryAndRecalculatesProgress() {
        click("Choose a backup to restore"); await("Review the restore"); click("Restore backup")
        await("Backup restored. Mastery and review dates have been recalculated.")
        assertEquals(listOf("backed-up"), snapshot().attempts.map { it.id })
        assertEquals(25, snapshot().progress.single().mastery)
        assertEquals(86_401_000L, snapshot().progress.single().nextReview)
    }
    @Test fun invalidAndStaleFilesDoNotReplaceDataAndRequireNewReview() {
        val before = snapshot(); val valid = selected; selected = "invalid"
        click("Choose a backup to restore"); await("The file is invalid or incompatible with this app version. Your current data has not changed.")
        assertEquals(before, snapshot())
        selected = valid; click("Choose a backup to restore"); await("Review the restore")
        runBlocking { val q = repository.snapshot.first().questions.first(); repository.answer(q, q.correctOptionId, "after-review", 4000) }
        click("Restore backup"); await("Your saved data changed after the preview. Select the file again and review the restore.")
        rule.onNodeWithText("Restore backup").assertDoesNotExist()
        assertEquals(3, snapshot().attempts.size)
    }
    @Test fun storageFailureRetainsHistoryAndAllowsRetryOfReviewedBackup() {
        val before = snapshot()
        click("Choose a backup to restore"); await("Review the restore")
        app.container.database.openHelper.writableDatabase.execSQL("CREATE TRIGGER abort_backup_ui BEFORE INSERT ON question_progress BEGIN SELECT RAISE(ABORT, 'test restore failure'); END")
        try {
            click("Restore backup"); await("Restoration failed. Your current data is intact. You can retry the restore.")
            assertEquals(before, snapshot())
        } finally { app.container.database.openHelper.writableDatabase.execSQL("DROP TRIGGER abort_backup_ui") }
        click("Restore backup"); await("Backup restored. Mastery and review dates have been recalculated.")
        assertEquals(1, snapshot().attempts.size)
    }
}
