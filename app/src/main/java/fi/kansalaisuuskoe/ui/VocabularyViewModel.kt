package fi.kansalaisuuskoe.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

class VocabularyViewModel(private val saved: SavedStateHandle) : ViewModel() {
    val query = saved.getStateFlow("search", "")
    val topic = saved.getStateFlow<String?>("filter_topic", saved["topicId"])
    fun search(value: String) { saved["search"] = value.take(200) }
    fun filter(id: String?) { saved["filter_topic"] = id }
}
