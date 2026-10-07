package fi.kansalaisuuskoe

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.kansalaisuuskoe.data.*
import fi.kansalaisuuskoe.domain.StudyLanguage
import fi.kansalaisuuskoe.ui.WordPracticeViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WordPracticeRestoreTest {
    private lateinit var database: LearningDatabase
    private lateinit var repository: LearningRepository
    private val store = ViewModelStore()
    @Before fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, LearningDatabase::class.java).build()
        repository = LearningRepository(database)
        repository.initialize(context)
    }
    @After fun close() = runBlocking { withContext(Dispatchers.Main) { store.clear() }; database.close() }
    private suspend fun model(saved: SavedStateHandle, language: StudyLanguage = StudyLanguage.FINNISH): WordPracticeViewModel {
        val vm = withContext(Dispatchers.Main) { WordPracticeViewModel(repository, saved, language).also { store.put("words", it) } }
        withTimeout(15_000) { vm.state.first { !it.loading } }
        return vm
    }
    private suspend fun copy(saved: SavedStateHandle) = withContext(Dispatchers.Main) { SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }) }

    @Test fun savedQueuePositionAndRevealRestoreWithoutWritingLearningHistory() = runBlocking {
        val before = repository.snapshot.first()
        val saved = SavedStateHandle()
        val first = model(saved)
        assertEquals(10, first.state.value.total)
        withContext(Dispatchers.Main) { first.next() }
        assertEquals(0, first.state.value.index)
        withContext(Dispatchers.Main) { first.reveal(); first.next(); first.reveal() }
        val original = first.state.value
        val restored = model(copy(saved))
        assertEquals(original, restored.state.value)
        assertEquals(before, repository.snapshot.first())
    }
    @Test fun restoredStudyLanguageDoesNotSwitchWithPreferences() = runBlocking {
        val saved = SavedStateHandle(mapOf("query" to "power"))
        val first = model(saved, StudyLanguage.SWEDISH)
        assertEquals("lagstiftande makt", first.state.value.word?.term)
        assertEquals(first.state.value, model(copy(saved), StudyLanguage.FINNISH).state.value)
    }
    @Test fun changedRevisionRejectsRestoredWordRatherThanShowingOldRevealForNewContent() = runBlocking {
        val saved = SavedStateHandle(mapOf("query" to "power"))
        val first = model(saved)
        withContext(Dispatchers.Main) { first.reveal() }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val content = ContentCodec.parse(context.assets.open("demo-content.json").bufferedReader().use { it.readText() })
        val revised = content.copy(version = content.version + 1, questions = content.questions.map {
            if (it.id == "demo-parliament") it.copy(revision = it.revision + 1, explanation = "Updated test explanation.") else it
        })
        repository.importContent(com.squareup.moshi.Moshi.Builder().build().adapter(ContentPackageDto::class.java).toJson(revised))
        assertTrue(model(copy(saved)).state.value.failed)
        assertTrue(repository.snapshot.first().attempts.isEmpty())
    }
    @Test fun removedWordOrInvalidPositionShowsRecoverableFailure() = runBlocking {
        assertTrue(model(SavedStateHandle(mapOf("word_ids" to arrayListOf("removed:0")))).state.value.failed)
        assertTrue(model(SavedStateHandle(mapOf("word_index" to -1))).state.value.failed)
    }
    @Test fun emptyFilterFinishesWithoutInventingWordsOrScore() = runBlocking {
        val empty = model(SavedStateHandle(mapOf("topicId" to "unavailable"))).state.value
        assertEquals(0, empty.total)
        assertNull(empty.word)
        assertTrue(empty.finished)
        assertFalse(empty.failed)
        assertTrue(repository.snapshot.first().progress.isEmpty())
    }
}
