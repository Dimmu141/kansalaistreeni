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
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/** Runtime regressions for the snapshot and topic-route defects found by the architecture audit. */
@RunWith(AndroidJUnit4::class)
class ArchitectureRegressionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: LearningDatabase
    private lateinit var repository: LearningRepository
    private fun seed() = ContentCodec.parse(context.assets.open("demo-content.json").bufferedReader().use { it.readText() })
    private fun json(c: ContentPackageDto) = Moshi.Builder().build().adapter(ContentPackageDto::class.java).toJson(c)
    @Before fun setup(): Unit = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, LearningDatabase::class.java).build()
        repository = LearningRepository(database)
        repository.initialize(context)
    }
    @After fun cleanup() { database.close() }

    @Test fun everyObservedAnswerSnapshotPairsHistoryWithItsProgress() = runBlocking {
        val q = repository.snapshot.first().questions.first()
        val ready = CompletableDeferred<Unit>()
        val finalVisible = CompletableDeferred<Unit>()
        val inconsistent = CopyOnWriteArrayList<String>()
        val collector = launch(Dispatchers.Default) {
            repository.snapshot.collect { snapshot ->
                val attempts = snapshot.attempts.count { it.questionId == q.id }
                val progress = snapshot.progress.firstOrNull { it.questionId == q.id }?.attempts ?: 0
                if (attempts != progress) inconsistent.add("history=$attempts, progress=$progress")
                ready.complete(Unit)
                if (attempts == 40 && progress == 40) finalVisible.complete(Unit)
            }
        }
        try {
            withTimeout(10_000) { ready.await() }
            repeat(40) { index ->
                repository.answer(q, q.correctOptionId, "architecture-$index", 1000L + index)
                delay(20)
            }
            withTimeout(10_000) { finalVisible.await() }
        } finally { collector.cancelAndJoin() }
        assertTrue("Mixed answer snapshots: ${inconsistent.size}; examples=${inconsistent.take(5)}", inconsistent.isEmpty())
    }

    @Test fun everyObservedImportedQuestionHasItsMatchingTopic() = runBlocking {
        val base = seed()
        val ready = CompletableDeferred<Unit>()
        val finalVisible = CompletableDeferred<Unit>()
        val inconsistent = CopyOnWriteArrayList<String>()
        val collector = launch(Dispatchers.Default) {
            repository.snapshot.collect { snapshot ->
                val ids = snapshot.topics.map { it.id }.toSet()
                snapshot.questions.firstOrNull { it.topicId !in ids }?.let {
                    inconsistent.add("question topic=${it.topicId}, first visible topic=${ids.firstOrNull()}")
                }
                ready.complete(Unit)
                if (snapshot.questions.isNotEmpty() && snapshot.questions.all { it.topicId.startsWith("round-30-") }) finalVisible.complete(Unit)
            }
        }
        try {
            withTimeout(10_000) { ready.await() }
            for (index in 1..30) {
                repository.importContent(json(base.copy(version = base.version + index,
                    topics = base.topics.map { it.copy(id = "round-$index-${it.id}") },
                    questions = base.questions.map { it.copy(revision = it.revision + index, topicId = "round-$index-${it.topicId}") })))
                delay(30)
            }
            withTimeout(10_000) { finalVisible.await() }
        } finally { collector.cancelAndJoin() }
        assertTrue("Mixed imported snapshots: ${inconsistent.size}; examples=${inconsistent.take(5)}", inconsistent.isEmpty())
    }

    @Test fun emptyDatabaseEmitsAnInitialSnapshotAndObservesLaterImport() = runBlocking {
        database.clearAllTables()
        assertEquals(LearningSnapshot(), withTimeout(10_000) { repository.snapshot.first() })
        val next = async { withTimeout(10_000) { repository.snapshot.first { it.questions.isNotEmpty() } } }
        repository.initialize(context)
        assertEquals(seed().questions.size, next.await().questions.size)
    }

    @Test fun importedTopicNamedReviewStartsItsOwnUnseenQuestions() = assertTopicQueue("review")
    @Test fun importedTopicNamedAllDoesNotStartTheDailyPlan() = assertTopicQueue("all")
    @Test fun ordinaryImportedTopicUsesItsOwnQueue() = assertTopicQueue("my-topic")

    private fun assertTopicQueue(topicId: String) = runBlocking {
        val base = seed()
        val topic = base.topics.first().copy(id = topicId)
        val questions = base.questions.filter { it.topicId == base.topics.first().id && it.language == "fi" }.take(3)
            .map { it.copy(topicId = topicId, revision = it.revision + 1) }
        // Other questions make an accidentally selected daily plan observably different.
        val otherTopic = base.topics[1]
        val others = base.questions.filter { it.topicId == otherTopic.id && it.language == "fi" }
        val content = base.copy(packageId = "architecture-reserved-topic", topics = listOf(topic, otherTopic), questions = questions + others)
        ContentCodec.validate(content) // This ID is explicitly accepted by the import contract.
        repository.importContent(json(content))
        val store = ViewModelStore()
        val saved = SavedStateHandle(mapOf("mode" to "topic", "topicId" to topicId))
        val model = withContext(Dispatchers.Main) {
            QuestionViewModel(repository, saved, StudyLanguage.FINNISH).also { store.put("practice", it) }
        }
        try {
            val state = withTimeout(10_000) { model.state.first { !it.loading } }
            assertNull(state.error)
            assertEquals(questions.size, state.total)
            assertEquals(questions.map { it.id }.toSet(), saved.get<ArrayList<String>>("question_ids")!!.toSet())
            assertEquals(topicId, state.question?.topicId)
            assertNull(state.dailyReason)
            assertFalse(state.finished)
            // Recreate from saved arguments; changing language must not change this queue.
            val restoredSaved = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
            val restored = withContext(Dispatchers.Main) {
                store.clear()
                QuestionViewModel(repository, restoredSaved, StudyLanguage.SWEDISH).also { store.put("practice", it) }
            }
            val recovered = withTimeout(10_000) { restored.state.first { !it.loading } }
            assertEquals(state.question, recovered.question)
            assertEquals(state.total, recovered.total)
            assertNull(recovered.error)
            assertTrue(repository.snapshot.first().attempts.isEmpty())
        } finally { withContext(Dispatchers.Main) { store.clear() } }
    }
}
