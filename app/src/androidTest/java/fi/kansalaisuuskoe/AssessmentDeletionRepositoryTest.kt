package fi.kansalaisuuskoe

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.kansalaisuuskoe.data.*
import fi.kansalaisuuskoe.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssessmentDeletionRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "assessment-deletion-verification.db"
    private lateinit var database: LearningDatabase
    private lateinit var learning: LearningRepository
    private lateinit var assessments: AssessmentRepository
    private fun open() {
        database = Room.databaseBuilder(context, LearningDatabase::class.java, name).build()
        learning = LearningRepository(database); assessments = AssessmentRepository(database)
    }
    @Before fun setup(): Unit = runBlocking {
        context.deleteDatabase(name); open(); learning.initialize(context)
        val q = learning.snapshot.first().questions.first()
        learning.answer(q, q.correctOptionId, "ordinary-learning", 500)
    }
    @After fun cleanup() { database.close(); context.deleteDatabase(name) }
    private suspend fun completed(language: StudyLanguage = StudyLanguage.FINNISH, now: Long = 1000): AssessmentSession {
        val id = assessments.start(language, now)
        assessments.get(id)!!.questions.forEach { assessments.select(id, it.id, it.correctOptionId) }
        assessments.finish(id, now + 1000)
        return assessments.get(id)!!
    }
    @Test fun selectedCompletedResultIsDeletedPersistentlyWithoutTouchingOtherSessionsOrLearning() = runBlocking {
        val selected = completed()
        val other = completed(now = 3000)
        val sv = assessments.start(StudyLanguage.SWEDISH, 6000)
        val before = learning.snapshot.first()
        assessments.delete(selected); assessments.delete(selected) // Retrying a committed delete is safe.
        database.close(); open()
        assertNull(assessments.get(selected.id))
        assertEquals(other, assessments.get(other.id))
        assertNotNull(assessments.get(sv))
        assertEquals(before, learning.snapshot.first())
        assertEquals(setOf(other.id, sv), BackupCodec.parse(learning.exportBackup(7000)).assessments!!.map { it.id }.toSet())
    }
    @Test fun discardingUnfinishedSwedishSessionAllowsFreshStartAndKeepsFinnishHistory() = runBlocking {
        val fi = completed()
        val id = assessments.start(StudyLanguage.SWEDISH, 3000)
        val q = assessments.get(id)!!.questions.first()
        assessments.select(id, q.id, q.correctOptionId); assessments.move(id, 1)
        val before = learning.snapshot.first()
        assessments.delete(assessments.get(id)!!)
        val replacement = assessments.start(StudyLanguage.SWEDISH, 4000)
        assertNotEquals(id, replacement)
        assertEquals(0, assessments.get(replacement)!!.position)
        assertTrue(assessments.get(replacement)!!.answers.isEmpty())
        assertEquals(fi, assessments.get(fi.id))
        assertEquals(before, learning.snapshot.first())
    }
    @Test fun choicesOrPositionChangedAfterReviewAreNeverDeleted() = runBlocking {
        val id = assessments.start(StudyLanguage.FINNISH, 1000)
        val reviewed = assessments.get(id)!!
        val q = reviewed.questions.first()
        assessments.select(id, q.id, q.correctOptionId)
        try { assessments.delete(reviewed); fail("Changed answers deleted") } catch (_: AssessmentChangedException) { }
        val afterChoice = assessments.get(id)!!
        assessments.move(id, 1)
        try { assessments.delete(afterChoice); fail("Changed position deleted") } catch (_: AssessmentChangedException) { }
        assertEquals(1, assessments.get(id)!!.position)
        assertEquals(q.correctOptionId, assessments.get(id)!!.answers[q.id])
    }
    @Test fun restoredSnapshotWithSameIdRequiresFreshDeletionReview() = runBlocking {
        val reviewed = completed()
        val backup = BackupCodec.parse(learning.exportBackup(3000))
        val row = backup.assessments!!.single()
        val replacement = backup.copy(assessments = listOf(row.copy(startedAt = 900, finishedAt = 1900)))
        learning.restoreBackup(learning.prepareRestore(BackupCodec.encode(replacement)))
        val before = learning.snapshot.first()
        try { assessments.delete(reviewed); fail("Restored record deleted by stale review") } catch (_: AssessmentChangedException) { }
        assertEquals(1900L, assessments.get(reviewed.id)!!.finishedAt)
        assertEquals(before, learning.snapshot.first())
        assessments.delete(assessments.get(reviewed.id)!!)
        assertNull(assessments.get(reviewed.id))
    }
    @Test fun deletionInvalidatesV2BackupPreviewButLegacyRestoreKeepsDeletion() = runBlocking {
        val selected = completed()
        val backup = BackupCodec.parse(learning.exportBackup(3000))
        val v2 = learning.prepareRestore(BackupCodec.encode(backup))
        val v1 = learning.prepareRestore(BackupCodec.encode(backup.copy(schemaVersion = 1, assessments = null)))
        assessments.delete(selected)
        try { learning.restoreBackup(v2); fail("Deleted result silently restored") } catch (e: BackupException) { assertEquals(BackupProblem.STALE, e.problem) }
        learning.restoreBackup(v1)
        assertNull(assessments.get(selected.id))
        // A newly reviewed old v2 file can deliberately restore the deleted record.
        learning.restoreBackup(learning.prepareRestore(BackupCodec.encode(backup)))
        assertEquals(selected, assessments.get(selected.id))
    }
    @Test fun failedDeleteRetainsAllDataAndRetryDeletesOnlyTheReviewedRecord() = runBlocking {
        val selected = completed()
        val before = learning.snapshot.first()
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER abort_assessment_delete BEFORE DELETE ON assessments BEGIN SELECT RAISE(ABORT, 'test delete failure'); END")
        try {
            try { assessments.delete(selected); fail("Delete trigger ignored") } catch (_: android.database.sqlite.SQLiteException) { }
            assertEquals(selected, assessments.get(selected.id))
            assertEquals(before, learning.snapshot.first())
        } finally { database.openHelper.writableDatabase.execSQL("DROP TRIGGER abort_assessment_delete") }
        assessments.delete(selected)
        assertNull(assessments.get(selected.id))
        assertEquals(before, learning.snapshot.first())
    }
}
