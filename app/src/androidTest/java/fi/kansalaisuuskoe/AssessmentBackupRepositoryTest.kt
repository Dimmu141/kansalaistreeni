package fi.kansalaisuuskoe

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.moshi.Moshi
import fi.kansalaisuuskoe.data.*
import fi.kansalaisuuskoe.domain.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssessmentBackupRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "assessment-backup-verification.db"
    private lateinit var database: LearningDatabase
    private lateinit var learning: LearningRepository
    private lateinit var assessments: AssessmentRepository
    private fun open() {
        database = Room.databaseBuilder(context, LearningDatabase::class.java, name).build()
        learning = LearningRepository(database); assessments = AssessmentRepository(database)
    }
    @Before fun setup() = runBlocking { context.deleteDatabase(name); open(); learning.initialize(context) }
    @After fun cleanup() { database.close(); context.deleteDatabase(name) }
    private suspend fun completed(): String {
        val id = assessments.start(StudyLanguage.FINNISH, 1000)
        assessments.get(id)!!.questions.forEachIndexed { index, q -> assessments.select(id, q.id, if (index == 0) q.options.first { it.id != q.correctOptionId }.id else q.correctOptionId) }
        assessments.finish(id, 2000)
        return id
    }
    private suspend fun active(): String {
        val id = assessments.start(StudyLanguage.SWEDISH, 2500)
        val q = assessments.get(id)!!.questions.first()
        assessments.select(id, q.id, q.correctOptionId); assessments.move(id, 1)
        return id
    }
    @Test fun finishedAndUnfinishedSnapshotsRestoreAfterMaterialReplacementAndDatabaseReopen() = runBlocking {
        val finished = completed(); val unfinished = active()
        val expected = assessments.sessions.first()
        val learningBefore = learning.snapshot.first()
        val backup = learning.exportBackup(3000)
        val content = ContentCodec.parse(context.assets.open("demo-content.json").bufferedReader().use { it.readText() })
        learning.importContent(Moshi.Builder().build().adapter(ContentPackageDto::class.java).toJson(content.copy(version = content.version + 1, questions = content.questions.map { it.copy(revision = it.revision + 1, text = it.text + " changed") })))
        val q = learning.snapshot.first().questions.first()
        learning.answer(q, q.correctOptionId, "newer", 4000)
        val preview = learning.prepareRestore(backup)
        assertEquals(1, preview.completedAssessments); assertEquals(1, preview.unfinishedAssessments)
        assertEquals(2, preview.currentAssessments)
        learning.restoreBackup(preview)
        database.close(); open()
        assertEquals(expected, assessments.sessions.first())
        assertEquals(learningBefore, learning.snapshot.first())
        assertEquals(19, Assessments.result(assessments.get(finished)!!).correct)
        assertEquals(unfinished, assessments.start(StudyLanguage.SWEDISH, 5000))
        val old = assessments.get(unfinished)!!
        assertEquals(1, old.position); assertEquals(1, old.answeredCount)
        old.questions.filter { it.id !in old.answers }.forEach { assessments.select(unfinished, it.id, it.correctOptionId) }
        assessments.finish(unfinished, 6000)
        assertEquals(20, Assessments.result(assessments.get(unfinished)!!).correct)
        assertTrue(learning.snapshot.first().attempts.isEmpty())
    }
    @Test fun emptyV2BackupReplacesAllAssessmentHistoryOnlyAfterConfirmation() = runBlocking {
        val empty = learning.exportBackup(500)
        completed(); active()
        val preview = learning.prepareRestore(empty)
        assertEquals(2, assessments.sessions.first().size)
        assertEquals(2, preview.currentAssessments)
        learning.restoreBackup(preview)
        assertTrue(assessments.sessions.first().isEmpty())
    }
    @Test fun legacyRestoreKeepsAssessmentEditsMadeAfterPreview() = runBlocking {
        val legacy = BackupCodec.encode(BackupCodec.parse(learning.exportBackup(500)).copy(schemaVersion = 1, assessments = null))
        val id = active()
        val preview = learning.prepareRestore(legacy)
        assertFalse(preview.replacesAssessments)
        assessments.move(id, 2)
        val expected = assessments.get(id)
        learning.restoreBackup(preview)
        assertEquals(expected, assessments.get(id))
    }
    private fun stale(change: suspend (String) -> Unit) = runBlocking {
        val id = active()
        val preview = learning.prepareRestore(learning.exportBackup(3000))
        change(id)
        val before = assessments.sessions.first()
        try { learning.restoreBackup(preview); fail("Assessment change did not invalidate preview") } catch (e: BackupException) { assertEquals(BackupProblem.STALE, e.problem) }
        assertEquals(before, assessments.sessions.first())
        assertTrue(learning.snapshot.first().attempts.isEmpty())
    }
    @Test fun choiceChangeInvalidatesV2Preview() = stale { id -> val q = assessments.get(id)!!.questions.first(); assessments.select(id, q.id, q.options.first { it.id != q.correctOptionId }.id) }
    @Test fun positionChangeInvalidatesV2Preview() = stale { assessments.move(it, 2) }
    @Test fun newSessionInvalidatesV2Preview() = stale { assessments.start(StudyLanguage.FINNISH, 4000) }
    @Test fun completionInvalidatesV2Preview() = stale { id -> assessments.get(id)!!.questions.forEach { assessments.select(id, it.id, it.correctOptionId) }; assessments.finish(id, 4000) }
    @Test fun assessmentWriteFailureRollsBackMaterialAttemptsScoresAndSessionsTogether() = runBlocking {
        completed(); val id = active()
        val q = learning.snapshot.first().questions.first()
        learning.answer(q, q.correctOptionId, "backup-answer", 1000)
        val backup = learning.exportBackup(3000)
        assessments.move(id, 3)
        learning.answer(q, q.correctOptionId, "newer-answer", 4000)
        val before = learning.snapshot.first(); val sessions = assessments.sessions.first(); val metadata = learning.installedContent().metadata
        val preview = learning.prepareRestore(backup)
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER abort_assessment_restore BEFORE INSERT ON assessments BEGIN SELECT RAISE(ABORT, 'test restore failure'); END")
        try { assertTrue(runCatching { learning.restoreBackup(preview) }.isFailure) }
        finally { database.openHelper.writableDatabase.execSQL("DROP TRIGGER abort_assessment_restore") }
        assertEquals(before, learning.snapshot.first()); assertEquals(sessions, assessments.sessions.first()); assertEquals(metadata, learning.installedContent().metadata)
        learning.restoreBackup(preview)
        assertEquals(1, assessments.get(id)!!.position)
        assertEquals(listOf("backup-answer"), learning.snapshot.first().attempts.map { it.id })
    }
}
