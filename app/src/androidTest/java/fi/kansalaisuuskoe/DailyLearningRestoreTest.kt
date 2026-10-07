package fi.kansalaisuuskoe

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.moshi.Moshi
import fi.kansalaisuuskoe.data.*
import fi.kansalaisuuskoe.domain.*
import fi.kansalaisuuskoe.ui.QuestionViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DailyLearningRestoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: LearningDatabase
    private lateinit var repository: LearningRepository
    private val store = ViewModelStore()
    private fun seed() = context.assets.open("demo-content.json").bufferedReader().use { it.readText() }
    private suspend fun model(saved: SavedStateHandle, language: StudyLanguage = StudyLanguage.FINNISH) = withContext(Dispatchers.Main) {
        QuestionViewModel(repository, saved, language).also { store.put("session", it) }
    }
    private suspend fun loaded(model: QuestionViewModel) = withTimeout(15_000) { model.state.first { !it.loading } }
    private fun savedCopy(saved: SavedStateHandle) = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
    @Before fun before() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, LearningDatabase::class.java).build()
        repository = LearningRepository(database)
        repository.importContent(seed())
    }
    @After fun after() = runBlocking { withContext(Dispatchers.Main) { store.clear() }; database.close() }

    @Test fun pinnedQueueAndReasonsSurviveFeedbackAndLanguageChangeWithoutReplanning() = runBlocking {
        val snapshot = repository.snapshot.first().forLanguage(StudyLanguage.FINNISH)
        val first = snapshot.questions.first()
        repository.answer(first, first.options.first { it.id != first.correctOptionId }.id, "due", System.currentTimeMillis() - 700_000)
        val saved = SavedStateHandle(mapOf("mode" to "all"))
        val original = model(saved)
        val start = loaded(original)
        assertEquals(DailyQuestionReason.DUE_REVIEW, start.dailyReason)
        val ids = saved.get<ArrayList<String>>("question_ids")!!.toList()
        val reasons = saved.get<ArrayList<String>>("daily_reasons")!!.toList()
        withContext(Dispatchers.Main) { original.select(first.correctOptionId); original.submit() }
        withTimeout(15_000) { original.state.first { it.answered } }
        // Another session changes the available pool while this session is backgrounded.
        val outside = snapshot.questions.first { it.id !in ids }
        repository.answer(outside, outside.correctOptionId, "outside", System.currentTimeMillis())
        val restoredSaved = savedCopy(saved)
        withContext(Dispatchers.Main) { store.clear() }
        val restored = model(restoredSaved, StudyLanguage.SWEDISH)
        val feedback = loaded(restored)
        assertTrue(feedback.answered)
        assertEquals(first.id, feedback.question?.id)
        assertEquals(DailyQuestionReason.DUE_REVIEW, feedback.dailyReason)
        assertEquals(ids, restoredSaved.get<ArrayList<String>>("question_ids"))
        assertEquals(reasons, restoredSaved.get<ArrayList<String>>("daily_reasons"))
        withContext(Dispatchers.Main) { restored.submit(); restored.next() }
        assertEquals(ids[1], restored.state.value.question?.id)
        assertEquals(DailyQuestionReason.valueOf(reasons[1]), restored.state.value.dailyReason)
        assertEquals(3, repository.snapshot.first().attempts.size)
    }

    @Test fun fullReviewAndTopicSessionsDoNotAcquireDailyReasons() = runBlocking {
        val q = repository.snapshot.first().questions.first()
        repository.answer(q, q.correctOptionId, "old", 1000)
        val review = loaded(model(SavedStateHandle(mapOf("mode" to "review"))))
        assertEquals(1, review.total)
        assertNull(review.dailyReason)
        assertNull(loaded(model(SavedStateHandle(mapOf("mode" to q.topicId)))).dailyReason)
    }

    @Test fun changedRevisionRejectsActivePinnedSessionWithoutWritingHistory() = runBlocking {
        val saved = SavedStateHandle(mapOf("mode" to "all"))
        val first = loaded(model(saved)).question!!
        val content = ContentCodec.parse(seed())
        val changed = content.copy(version = content.version + 1, questions = content.questions.map { if (it.id == first.id) it.copy(revision = it.revision + 1) else it })
        repository.importContent(Moshi.Builder().build().adapter(ContentPackageDto::class.java).toJson(changed))
        val restored = loaded(model(savedCopy(saved)))
        assertEquals(R.string.error_open_practice, restored.error)
        assertTrue(repository.snapshot.first().attempts.isEmpty())
    }

    @Test fun finishedPinnedSessionAllowsRevisedMaterialButWithholdsStaleAnswerDetails() = runBlocking {
        val q = repository.snapshot.first().questions.first()
        repository.answer(q, q.correctOptionId, "finished", 1000)
        val content = ContentCodec.parse(seed())
        repository.importContent(Moshi.Builder().build().adapter(ContentPackageDto::class.java).toJson(content.copy(version = content.version + 1, questions = content.questions.map { if (it.id == q.id) it.copy(revision = it.revision + 1) else it })))
        val state = loaded(model(SavedStateHandle(mapOf("question_ids" to arrayListOf(q.id), "queue_revisions" to arrayListOf(q.revision), "index" to 1, "correct_count" to 1, "session_attempt_ids" to arrayListOf("finished")))))
        assertTrue(state.finished)
        assertNull(state.error)
        assertEquals(1, state.correctCount)
        assertTrue(state.recap.answers.isEmpty())
    }

    @Test fun legacyActiveSessionKeepsItsSavedOrderWithoutInventingReasons() = runBlocking {
        val qs = repository.snapshot.first().questions.filter { it.language == "fi" }.take(2).reversed()
        val state = loaded(model(SavedStateHandle(mapOf("question_ids" to ArrayList(qs.map { it.id }), "index" to 0))))
        assertEquals(qs.first().id, state.question?.id)
        assertEquals(2, state.total)
        assertNull(state.dailyReason)
    }
}
