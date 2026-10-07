package fi.kansalaisuuskoe.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fi.kansalaisuuskoe.data.LearningRepository
import fi.kansalaisuuskoe.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class WordPracticeState(val loading: Boolean = true, val word: VocabularyEntry? = null, val index: Int = 0, val total: Int = 0, val revealed: Boolean = false, val finished: Boolean = false, val failed: Boolean = false)

class WordPracticeViewModel(repository: LearningRepository, private val saved: SavedStateHandle, studyLanguage: StudyLanguage) : ViewModel() {
    private val mutable = MutableStateFlow(WordPracticeState())
    val state = mutable.asStateFlow()
    private var words = emptyList<VocabularyEntry>()
    init {
        viewModelScope.launch {
            try {
                val snapshot = repository.snapshot.first { it.questions.isNotEmpty() }
                val language = saved.get<String>("word_language")?.let { tag -> StudyLanguage.entries.first { it.tag == tag } } ?: studyLanguage
                saved["word_language"] = language.tag
                val entries = LearningContent.vocabulary(snapshot, language)
                val ids = saved.get<ArrayList<String>>("word_ids") ?: ArrayList(LearningContent.search(entries, saved["query"] ?: "", saved["topicId"]).shuffled().take(10).map { it.id }).also { saved["word_ids"] = it }
                words = ids.map { id -> requireNotNull(entries.find { it.id == id }) }
                val revisions = ArrayList(words.map { word -> word.contexts.joinToString(";") { "${it.id}:${it.revision}" } })
                val original = saved.get<ArrayList<String>>("word_revisions")
                require(original == null || original == revisions) { "Word content changed" }
                saved["word_revisions"] = revisions
                render()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutable.value = WordPracticeState(loading = false, failed = true)
            }
        }
    }
    fun reveal() { if (mutable.value.word != null) { saved["revealed"] = true; render() } }
    fun next() {
        if (mutable.value.revealed && !mutable.value.finished) {
            saved["word_index"] = mutable.value.index + 1
            saved["revealed"] = false
            render()
        }
    }
    private fun render() {
        val index = saved.get<Int>("word_index") ?: 0
        require(index in 0..words.size)
        mutable.value = WordPracticeState(loading = false, word = words.getOrNull(index), index = index, total = words.size,
            revealed = saved["revealed"] ?: false, finished = index >= words.size)
    }
}
