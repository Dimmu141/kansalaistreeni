package fi.kansalaisuuskoe

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fi.kansalaisuuskoe.data.*
import fi.kansalaisuuskoe.domain.LearningEngine
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

/** Build actual old databases from exported schemas, then migrate using the production path. */
@RunWith(AndroidJUnit4::class)
class LearningMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-verification.db"
    private var database: LearningDatabase? = null

    @After fun cleanUp() { database?.close(); context.deleteDatabase(name) }

    private fun createLegacyDatabase(packageId: String, schemaVersion: Int, language: String): String {
        context.deleteDatabase(name)
        val seed = context.assets.open("demo-content.json").bufferedReader().use { it.readText() }
        val current = ContentCodec.parse(seed)
        val content = current.copy(version = 1, questions = current.questions.filter { it.language == language }, topics = current.topics.map { it.copy(translations = emptyMap()) })
        val legacyJson = Moshi.Builder().build().adapter(ContentPackageDto::class.java).toJson(content)
        val schemaJson = InstrumentationRegistry.getInstrumentation().context.assets
            .open("fi.kansalaisuuskoe.data.LearningDatabase/$schemaVersion.json").bufferedReader().use { it.readText() }
        val schema = JSONObject(schemaJson).getJSONObject("database")
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                if (indices != null) for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            content.topics.forEach { topic ->
                db.insertOrThrow("topics", null, ContentValues().apply {
                    put("id", topic.id); put("title", topic.title); put("description", topic.description); put("sortOrder", topic.order)
                })
            }
            content.questions.forEach { question ->
                db.insertOrThrow("questions", null, ContentValues().apply {
                    put("id", question.id); put("revision", question.revision); put("payload", ContentCodec.questionAdapter.toJson(question))
                })
            }
            val hash = MessageDigest.getInstance("SHA-256").digest(legacyJson.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            db.insertOrThrow("content_metadata", null, ContentValues().apply {
                put("slot", 1); put("packageId", packageId); put("version", content.version); put("hash", hash)
            })
            val q = content.questions.first()
            repeat(4) { index ->
                db.insertOrThrow("attempts", null, ContentValues().apply {
                    put("id", "legacy-$index"); put("questionId", q.id); put("revision", q.revision); put("topicId", q.topicId)
                    put("selectedOptionId", q.correctOptionId); put("correct", 1); put("answeredAt", 1000L + index)
                })
            }
            db.insertOrThrow("question_progress", null, ContentValues().apply {
                put("questionId", q.id); put("revision", q.revision); put("attempts", 4); put("correctAnswers", 4)
                put("consecutiveCorrect", if (schemaVersion == 1) 4 else 1); put("mastery", if (schemaVersion == 1) 100 else 25)
                put("lastAnswered", 1003L); put("nextReview", if (schemaVersion == 1) 1003L + 14 * 86_400_000L else 1000L + 86_400_000L)
            })
            if (schemaVersion >= 2) db.insertOrThrow("learning_rules", null, ContentValues().apply { put("slot", 1); put("version", LearningEngine.RULES_VERSION) })
            db.version = schemaVersion
        }
        return content.questions.first().id
    }

    private fun verifyUpgrade(packageId: String, schemaVersion: Int = 1, language: String = "fi") = runBlocking {
        val questionId = createLegacyDatabase(packageId, schemaVersion, language)
        val opened = Room.databaseBuilder(context, LearningDatabase::class.java, name)
            .addMigrations(LearningDatabase.MIGRATION_1_2, LearningDatabase.MIGRATION_2_3, LearningDatabase.MIGRATION_3_4, LearningDatabase.MIGRATION_4_5).build().also { database = it }
        val repository = LearningRepository(opened)
        repository.initialize(context)
        val snapshot = repository.snapshot.first()
        assertEquals(if (packageId == "fi-civics-demo") 24 else 12, snapshot.questions.size)
        assertTrue(snapshot.attempts.all { it.language == language })
        assertEquals(4, snapshot.attempts.size)
        assertEquals(listOf("legacy-0", "legacy-1", "legacy-2", "legacy-3"), snapshot.attempts.map { it.id })
        val progress = snapshot.progress.single()
        assertEquals(questionId, progress.questionId)
        assertEquals(4, progress.attempts)
        assertEquals(4, progress.correctAnswers)
        assertEquals(25, progress.mastery)
        assertEquals(1, progress.consecutiveCorrect)
        assertEquals(1000L + 86_400_000L, progress.nextReview)
        assertEquals(packageId, opened.learningDao().metadata()!!.packageId)
        if (packageId == "fi-civics-demo") {
            assertEquals("Society and democracy", snapshot.topics.first().titleFor("en"))
            assertEquals(ContentCodec.parse(context.assets.open("demo-content.json").bufferedReader().use { it.readText() }).version, opened.learningDao().metadata()!!.version)
        } else {
            // An imported package without translations falls back to its original labels.
            assertEquals(snapshot.topics.first().title, snapshot.topics.first().titleFor("en"))
            assertEquals(1, opened.learningDao().metadata()!!.version)
        }
        assertEquals(LearningEngine.RULES_VERSION, opened.learningDao().rulesVersion())
        val q = snapshot.questions.first { it.id == questionId }
        val reviewed = repository.answer(q, q.correctOptionId, "due", requireNotNull(progress.nextReview))
        assertEquals(50, reviewed.progress.mastery)
        repository.initialize(context)
        assertEquals(5, repository.snapshot.first().attempts.size)
        assertEquals(reviewed.progress, repository.snapshot.first().progress.single())
        assertEquals(5, opened.openHelper.readableDatabase.version)
    }

    @Test fun bundledContentUpgradeRecalculatesWithoutLosingHistory() {
        val content = ContentCodec.parse(context.assets.open("demo-content.json").bufferedReader().use { it.readText() })
        verifyUpgrade(content.packageId)
    }

    @Test fun importedContentAlsoGetsNewRulesWithoutBeingReplaced() { verifyUpgrade("future-editorial") }
    @Test fun schemaTwoUpgradePreservesScoresAndAddsBundledTopicTranslations() { verifyUpgrade("fi-civics-demo", 2) }
    @Test fun schemaTwoImportedPackageKeepsItsContentAndFallsBackToOriginalLabels() { verifyUpgrade("future-editorial", 2) }
    @Test fun schemaThreeKeepsFinnishHistoryWhileAddingSwedishDemo() { verifyUpgrade("fi-civics-demo", 3) }
    @Test fun schemaThreeInternalSwedishPackageRecoversAttemptLanguage() { verifyUpgrade("swedish-editorial", 3, "sv") }
    @Test fun schemaFourPreservesExistingLearningAndAddsAssessmentStorage() = runBlocking {
        verifyUpgrade("fi-civics-demo", 4)
        val repository = AssessmentRepository(requireNotNull(database))
        assertTrue(repository.sessions.first().isEmpty())
        val id = repository.start(fi.kansalaisuuskoe.domain.StudyLanguage.FINNISH, 5000)
        assertEquals(12, repository.get(id)?.questions?.size)
    }
}
