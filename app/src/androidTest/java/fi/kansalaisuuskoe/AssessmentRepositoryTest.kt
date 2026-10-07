package fi.kansalaisuuskoe

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.moshi.Moshi
import fi.kansalaisuuskoe.data.*
import fi.kansalaisuuskoe.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssessmentRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "assessment-verification.db"
    private lateinit var database: LearningDatabase
    private lateinit var learning: LearningRepository
    private lateinit var assessments: AssessmentRepository
    private fun open() {
        database = Room.databaseBuilder(context, LearningDatabase::class.java, name)
            .addMigrations(LearningDatabase.MIGRATION_4_5, LearningDatabase.MIGRATION_5_6).build()
        learning = LearningRepository(database); assessments = AssessmentRepository(database)
    }
    @Before fun setup() = runBlocking { context.deleteDatabase(name); open(); learning.initialize(context) }
    @After fun cleanup() { database.close(); context.deleteDatabase(name) }
    private suspend fun complete(id: String) {
        val session = requireNotNull(assessments.get(id))
        session.questions.forEachIndexed { index, q ->
            assessments.select(id, q.id, if (index == 0) q.options.first { it.id != q.correctOptionId }.id else q.correctOptionId)
        }
        assessments.finish(id, 2000)
    }

    @Test fun selectionPositionAndSnapshotSurviveReopeningWithoutLearningWrites() = runBlocking {
        val before = learning.snapshot.first()
        val id = assessments.start(StudyLanguage.FINNISH, 1000)
        val initial = requireNotNull(assessments.get(id))
        assessments.select(id, initial.questions.first().id, initial.questions.first().correctOptionId)
        assessments.move(id, 1)
        val saved = assessments.get(id)
        database.close(); open()
        assertEquals(saved, assessments.get(id))
        assertEquals(1, requireNotNull(saved).position)
        assertEquals(before, learning.snapshot.first())
        assertEquals(id, assessments.start(StudyLanguage.FINNISH, 9000))
    }
    @Test fun completionIsIdempotentAndDoesNotChangeMasteryAccuracyOrReviews() = runBlocking {
        val q = learning.snapshot.first().questions.first()
        learning.answer(q, q.correctOptionId, "learning", 500)
        val before = learning.snapshot.first()
        val id = assessments.start(StudyLanguage.FINNISH, 1000)
        complete(id)
        assessments.finish(id, 9000)
        val result = Assessments.result(requireNotNull(assessments.get(id)))
        assertEquals(19, result.correct); assertEquals(20, result.total)
        assertEquals(2000L, assessments.get(id)?.finishedAt)
        assertEquals(before, learning.snapshot.first())
        assertNotEquals(id, assessments.start(StudyLanguage.FINNISH, 3000))
    }
    @Test fun concurrentStartsResumeOnlyOneSessionAndLanguagesHaveSeparateSessions() = runBlocking {
        val ids = coroutineScope { (1..5).map { async(Dispatchers.IO) { assessments.start(StudyLanguage.FINNISH, 1000) } }.awaitAll() }
        assertEquals(1, ids.distinct().size)
        val sv = assessments.start(StudyLanguage.SWEDISH, 1000)
        assertNotEquals(ids.first(), sv)
        assertTrue(requireNotNull(assessments.get(sv)).questions.all { it.language == "sv" })
        assertEquals(2, assessments.sessions.first().size)
    }
    @Test fun invalidChoicesAndIncompleteFinishLeaveStoredSessionUnchanged() = runBlocking {
        val id = assessments.start(StudyLanguage.FINNISH, 1000)
        val initial = assessments.get(id)!!
        try { assessments.select(id, initial.questions.first().id, "missing"); fail("Invalid answer accepted") } catch (_: IllegalArgumentException) { }
        try { assessments.finish(id); fail("Incomplete session finished") } catch (_: IllegalArgumentException) { }
        assertEquals(initial, assessments.get(id))
    }
    @Test fun completedResultsRemainReadableAfterMaterialUpdateAndBackupRestore() = runBlocking {
        val backup = BackupCodec.encode(BackupCodec.parse(learning.exportBackup(500)).copy(schemaVersion = 1, assessments = null))
        val id = assessments.start(StudyLanguage.SWEDISH, 1000)
        complete(id)
        val saved = assessments.get(id)!!
        val seed = ContentCodec.parse(context.assets.open("demo-content.json").bufferedReader().use { it.readText() })
        val revised = seed.copy(version = seed.version + 1, questions = seed.questions.map { it.copy(revision = 2, text = it.text + " (revised)") })
        learning.importContent(Moshi.Builder().build().adapter(ContentPackageDto::class.java).toJson(revised))
        assertEquals(saved, assessments.get(id))
        assertTrue(Assessments.practiceMistakes(saved, learning.snapshot.first().questions).isEmpty())
        assertFalse(backup.contains("\"assessments\""))
        learning.restoreBackup(learning.prepareRestore(backup))
        assertEquals(saved, assessments.get(id))
        assertEquals(1, Assessments.practiceMistakes(saved, learning.snapshot.first().questions).size)
        database.close(); open()
        assertEquals(saved, assessments.get(id))
    }
    @Test fun activeSnapshotDoesNotChangeWhenMaterialIsReplaced() = runBlocking {
        val id = assessments.start(StudyLanguage.FINNISH, 1000)
        val saved = assessments.get(id)!!
        val seed = ContentCodec.parse(context.assets.open("demo-content.json").bufferedReader().use { it.readText() })
        val replacement = seed.copy(packageId = "different-demo", version = 1, questions = seed.questions.filter { it.language == "sv" })
        learning.importContent(Moshi.Builder().build().adapter(ContentPackageDto::class.java).toJson(replacement))
        assertEquals(saved, assessments.get(id))
        assertEquals(id, assessments.start(StudyLanguage.FINNISH))
        complete(id)
        assertEquals(19, Assessments.result(assessments.get(id)!!).correct)
        assertTrue(Assessments.practiceMistakes(assessments.get(id)!!, learning.snapshot.first().questions).isEmpty())
    }
    @Test fun completedSessionRejectsAnswerChangesAndFollowUpUsesOrdinaryLearningRules() = runBlocking {
        val id = assessments.start(StudyLanguage.FINNISH, 1000)
        complete(id)
        val session = assessments.get(id)!!
        try { assessments.select(id, session.questions.first().id, session.questions.first().correctOptionId); fail("Completed answer changed") } catch (_: IllegalStateException) { }
        val followUp = Assessments.practiceMistakes(session, learning.snapshot.first().questions).single()
        learning.answer(followUp, followUp.correctOptionId, "follow-up", 3000)
        assertEquals(25, learning.snapshot.first().progress.single().mastery)
        assertEquals(1, learning.snapshot.first().attempts.size)
        assertEquals(session, assessments.get(id))
    }
}
