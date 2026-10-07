package fi.kansalaisuuskoe.domain

import java.text.Normalizer
import java.util.Locale

data class TopicLesson(val topic: Topic, val ideas: List<Question>, val authored: AuthoredLesson? = null, val english: AuthoredLesson? = null) {
    fun contentStatus(): ContentStatus {
        val statuses = ideas.map { it.status } + listOfNotNull(authored?.status, english?.status)
        return when {
            statuses.isEmpty() || ContentStatus.DEMO in statuses -> ContentStatus.DEMO
            statuses.all { it == ContentStatus.OFFICIAL } -> ContentStatus.OFFICIAL
            statuses.all { it == ContentStatus.OFFICIALLY_SOURCED } -> ContentStatus.OFFICIALLY_SOURCED
            else -> ContentStatus.EDITORIAL
        }
    }
}
data class VocabularyEntry(
    val id: String,
    val term: String,
    val meaning: String,
    val otherTerm: String?,
    val language: String,
    val contexts: List<Question>,
)

/** Reading views derive from the installed package; they never update question mastery. */
object LearningContent {
    fun lesson(snapshot: LearningSnapshot, topicId: String, language: StudyLanguage): TopicLesson? {
        val topic = snapshot.topics.find { it.id == topicId } ?: return null
        val ideas = snapshot.questions.filter { it.topicId == topicId && it.language == language.tag }
        return if (ideas.isEmpty()) null else TopicLesson(topic, ideas, topic.lessons[language.tag], topic.lessons["en"])
    }

    fun vocabulary(snapshot: LearningSnapshot, language: StudyLanguage): List<VocabularyEntry> {
        data class Key(val term: String, val meaning: String, val other: String)
        val words = linkedMapOf<Key, VocabularyEntry>()
        snapshot.questions.filter { it.language == language.tag }.forEach { question ->
            question.vocabulary.forEachIndexed { index, word ->
                val term = if (language == StudyLanguage.SWEDISH) word.swedish ?: return@forEachIndexed else word.term
                val other = if (language == StudyLanguage.SWEDISH) word.term else word.swedish
                val key = Key(normalize(term), normalize(word.meaning), normalize(other.orEmpty()))
                val previous = words[key]
                words[key] = if (previous == null) {
                    VocabularyEntry("${question.id}:$index", term, word.meaning, other, language.tag, listOf(question))
                } else previous.copy(contexts = (previous.contexts + question).distinctBy { it.id })
            }
        }
        return words.values.sortedBy { normalize(it.term) }
    }

    fun search(entries: List<VocabularyEntry>, query: String, topicId: String? = null): List<VocabularyEntry> {
        val tokens = normalize(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
        return entries.mapNotNull { entry ->
            val contexts = entry.contexts.filter { topicId == null || it.topicId == topicId }
            val searchable = normalize(listOf(entry.term, entry.meaning, entry.otherTerm.orEmpty(),
                contexts.joinToString(" ") { it.explanation }).joinToString(" "))
            if (contexts.isNotEmpty() && tokens.all { searchable.contains(it) }) entry.copy(contexts = contexts) else null
        }
    }

    private fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT).trim()
}
