package fi.kansalaisuuskoe

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
class AuthoredLessonRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "authored-lesson-verification.db"
    private lateinit var database: LearningDatabase
    private lateinit var learning: LearningRepository
    private fun open() { database = Room.databaseBuilder(context, LearningDatabase::class.java, name).build(); learning = LearningRepository(database) }
    private fun json(c: ContentPackageDto) = Moshi.Builder().build().adapter(ContentPackageDto::class.java).toJson(c)
    private fun seed() = ContentCodec.parse(context.assets.open("demo-content.json").bufferedReader().use { it.readText() })
    private fun revised(): ContentPackageDto {
        val c = seed(); val t = c.topics.first(); val l = t.lessons.getValue("fi")
        return c.copy(version = c.version + 1, topics = listOf(t.copy(lessons = t.lessons + ("fi" to l.copy(revision = l.revision + 1, introduction = "Päivitetty oppitunti.")))) + c.topics.drop(1))
    }
    @Before fun setup(): Unit = runBlocking { context.deleteDatabase(name); open(); learning.initialize(context) }
    @After fun cleanup() { database.close(); context.deleteDatabase(name) }

    @Test fun bundledExpansionKeepsEveryOldAttemptScoreScheduleAndAssessment() = runBlocking {
        // Real previous package, not a reconstructed approximation of old questions.
        val old = ContentCodec.parse(regressionContentJson())
        learning.restoreBackup(learning.prepareRestore(BackupCodec.encode(LearningBackup(BackupCodec.FORMAT, 2, 500, old, emptyList(), emptyList()))))
        old.questions.filter { it.id in setOf("demo-parliament", "demo-parliament-sv") }.forEach { learning.answer(it.model(), it.correctOptionId, it.id, 1000) }
        val assessments = AssessmentRepository(database)
        val id = assessments.start(StudyLanguage.FINNISH, 2000)
        val assessment = assessments.get(id)!!
        val before = learning.snapshot.first()
        learning.initialize(context)
        val after = learning.snapshot.first()
        assertEquals(60, after.questions.size)
        assertEquals(before.attempts, after.attempts); assertEquals(before.progress, after.progress)
        assertEquals(assessment, assessments.get(id)); assertEquals(12, assessment.questions.size)
        assertEquals(before.questions, after.questions.filter { it.id in before.questions.map { q -> q.id } })
        assertTrue(after.topics.all { it.lessons.keys == setOf("fi", "sv", "en") })
        assertEquals(5, learning.installedContent().metadata?.version)
    }
    @Test fun lessonOnlyImportPersistsAcrossReopenAndPortableBackupWithoutTouchingLearning() = runBlocking {
        val q = learning.snapshot.first().questions.first()
        learning.answer(q, q.correctOptionId, "before-lesson", 1000)
        val before = learning.snapshot.first()
        val preview = learning.prepareImport(json(revised()))
        assertEquals(before, learning.snapshot.first()); assertEquals(60, preview.changes.unchanged)
        learning.confirmImport(preview)
        val updated = learning.snapshot.first()
        assertEquals(before.attempts, updated.attempts); assertEquals(before.progress, updated.progress)
        assertEquals("Päivitetty oppitunti.", updated.topics.first().lessons.getValue("fi").introduction)
        database.close(); open(); learning.initialize(context)
        assertEquals(updated, learning.snapshot.first())
        val exported = learning.exportBackup(2000)
        learning.restoreBackup(learning.prepareRestore(exported))
        assertEquals(updated, learning.snapshot.first())
        assertTrue(learning.prepareImport(json(revised())).alreadyInstalled)
    }
    @Test fun failedLessonUpdateRollsBackTopicsQuestionsMetadataAndHistory() = runBlocking {
        val before = learning.snapshot.first(); val installed = learning.installedContent()
        val preview = learning.prepareImport(json(revised()))
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_lesson BEFORE INSERT ON topics BEGIN SELECT RAISE(ABORT, 'test storage failure'); END")
        try { learning.confirmImport(preview); fail("Write failure ignored") } catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(before, learning.snapshot.first()); assertEquals(installed, learning.installedContent())
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_lesson")
        learning.confirmImport(preview)
        assertEquals("Päivitetty oppitunti.", learning.snapshot.first().topics.first().lessons.getValue("fi").introduction)
    }
    @Test fun changedLessonInvalidatesAnEarlierImportAndBackupPreview() = runBlocking {
        val import = learning.prepareImport(json(seed().copy(version = 7)))
        val restore = learning.prepareRestore(learning.exportBackup(1000))
        learning.importContent(json(revised()))
        val before = learning.snapshot.first()
        try { learning.confirmImport(import); fail("Stale import accepted") } catch (e: ContentImportException) { assertEquals(ImportProblem.STALE, e.problem) }
        try { learning.restoreBackup(restore); fail("Stale restore accepted") } catch (e: BackupException) { assertEquals(BackupProblem.STALE, e.problem) }
        assertEquals(before, learning.snapshot.first())
    }
}
