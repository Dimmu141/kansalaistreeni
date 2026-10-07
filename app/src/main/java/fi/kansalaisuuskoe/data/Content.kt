package fi.kansalaisuuskoe.data

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import fi.kansalaisuuskoe.domain.*
import java.net.URI

@JsonClass(generateAdapter = true)
data class ContentPackageDto(val schemaVersion: Int, val packageId: String, val version: Int, val status: String, val topics: List<TopicDto>, val questions: List<QuestionDto>)
@JsonClass(generateAdapter = true)
data class TopicDto(val id: String, val title: String, val description: String, val order: Int, val translations: Map<String, TopicTranslationDto> = emptyMap(), val lessons: Map<String, LessonDto> = emptyMap()) {
    fun model() = Topic(id, title, description, order, translations.mapValues { (_, t) -> TopicTranslation(t.title, t.description) }, lessons.mapValues { (_, lesson) -> lesson.model() })
}
@JsonClass(generateAdapter = true)
data class LessonDto(val id: String, val revision: Int, val introduction: String, val status: String, val sections: List<LessonSectionDto>) {
    fun model() = AuthoredLesson(id, revision, introduction, ContentStatus.valueOf(status), sections.map { it.model() })
}
@JsonClass(generateAdapter = true)
data class LessonSectionDto(val id: String, val title: String, val body: String, val sources: List<SourceDto>, val easyBody: String? = null) {
    fun model() = LessonSection(id, title, body, easyBody, sources.map { Source(it.title, it.url) })
}
@JsonClass(generateAdapter = true)
data class TopicTranslationDto(val title: String, val description: String)
@JsonClass(generateAdapter = true)
data class OptionDto(val id: String, val text: String)
@JsonClass(generateAdapter = true)
data class VocabularyDto(val term: String, val meaning: String, val swedish: String? = null)
@JsonClass(generateAdapter = true)
data class SourceDto(val title: String, val url: String)
@JsonClass(generateAdapter = true)
data class TranslationDto(val text: String, val options: List<OptionDto>, val explanation: String)
@JsonClass(generateAdapter = true)
data class QuestionDto(
    val id: String, val revision: Int, val topicId: String, val text: String,
    val options: List<OptionDto>, val correctOptionId: String, val explanation: String,
    val easyExplanation: String? = null, val vocabulary: List<VocabularyDto> = emptyList(),
    val source: SourceDto, val language: String, val difficulty: String,
    val tags: List<String>, val status: String,
    val translations: Map<String, TranslationDto> = emptyMap(),
) {
    fun model() = Question(
        id, revision, topicId, text, options.map { AnswerOption(it.id, it.text) }, correctOptionId,
        explanation, easyExplanation, vocabulary.map { VocabularyItem(it.term, it.meaning, it.swedish) },
        Source(source.title, source.url), language, Difficulty.valueOf(difficulty), tags,
        ContentStatus.valueOf(status), translations.mapValues { (_, value) ->
            QuestionTranslation(value.text, value.options.map { AnswerOption(it.id, it.text) }, value.explanation)
        },
    )
}

object ContentCodec {
    private val moshi = Moshi.Builder().build()
    val questionAdapter = moshi.adapter(QuestionDto::class.java)
    val topicTranslationsAdapter = moshi.adapter<Map<String, TopicTranslationDto>>(Types.newParameterizedType(Map::class.java, String::class.java, TopicTranslationDto::class.java))
    val lessonsAdapter = moshi.adapter<Map<String, LessonDto>>(Types.newParameterizedType(Map::class.java, String::class.java, LessonDto::class.java))
    private val packageAdapter = moshi.adapter(ContentPackageDto::class.java)
    fun parse(json: String): ContentPackageDto = requireNotNull(packageAdapter.fromJson(json)).also(::validate)

    fun validate(content: ContentPackageDto) {
        require(content.schemaVersion == 1) { "Unsupported content schema" }
        require(content.packageId.isNotBlank() && content.version > 0)
        ContentStatus.valueOf(content.status)
        require(content.topics.isNotEmpty() && content.questions.isNotEmpty())
        require(content.topics.map { it.id }.distinct().size == content.topics.size) { "Duplicate topic ID" }
        require(content.questions.map { it.id }.distinct().size == content.questions.size) { "Duplicate question ID" }
        val topics = content.topics.map { it.id }.toSet()
        content.topics.forEach { topic ->
            require(topic.id.matches(Regex("[a-z0-9-]+")) && topic.title.isNotBlank() && topic.description.isNotBlank())
            topic.translations.forEach { (language, translation) ->
                require(language.matches(Regex("[a-z]{2,3}(-[A-Za-z0-9]{2,8})*")) && translation.title.isNotBlank() && translation.description.isNotBlank()) { "Invalid topic translation" }
            }
            topic.lessons.forEach { (language, lesson) ->
                require(language in setOf("fi", "sv", "en")) { "Unsupported lesson language" }
                require(lesson.id.isNotBlank() && lesson.revision > 0 && lesson.introduction.isNotBlank() && lesson.sections.isNotEmpty())
                ContentStatus.valueOf(lesson.status)
                if (content.status == "DEMO") require(lesson.status == "DEMO")
                require(lesson.sections.map { it.id }.distinct().size == lesson.sections.size) { "Duplicate lesson section" }
                lesson.sections.forEach { section ->
                    require(section.id.isNotBlank() && section.title.isNotBlank() && section.body.isNotBlank())
                    require(section.easyBody == null || (language == "fi" && section.easyBody.isNotBlank()))
                    require(section.sources.isNotEmpty()) { "Lesson sections need sources" }
                    section.sources.forEach { validateSource(it) }
                }
            }
            // Supporting translations must describe the same sections as the study-language lesson.
            val english = topic.lessons["en"]
            if (english != null) {
                require(topic.lessons.keys.any { it == "fi" || it == "sv" }) { "English support needs a native lesson" }
                topic.lessons.filterKeys { it != "en" }.values.forEach { native ->
                    require(native.id == english.id && native.sections.map { it.id } == english.sections.map { it.id }) { "Misaligned lesson translation" }
                }
            }
        }
        content.questions.forEach { q ->
            require(q.id.isNotBlank() && q.revision > 0 && q.topicId in topics)
            require(q.text.isNotBlank() && q.explanation.isNotBlank())
            require(q.easyExplanation == null || q.easyExplanation.isNotBlank())
            require(q.vocabulary.all { it.term.isNotBlank() && it.meaning.isNotBlank() && (it.swedish == null || it.swedish.isNotBlank()) })
            require(q.tags.all { it.isNotBlank() } && q.tags.distinct().size == q.tags.size)
            require(q.options.size >= 2 && q.options.all { it.id.isNotBlank() && it.text.isNotBlank() })
            require(q.options.map { it.id }.distinct().size == q.options.size)
            require(q.options.count { it.id == q.correctOptionId } == 1) { "Missing correct answer" }
            require(q.language in setOf("fi", "sv"))
            ContentStatus.valueOf(q.status)
            Difficulty.valueOf(q.difficulty)
            if (content.status == "DEMO") require(q.status == "DEMO")
            validateSource(q.source)
            q.translations.forEach { (language, t) ->
                require(language.matches(Regex("[a-z]{2,3}(-[A-Za-z0-9]{2,8})*")) && language != q.language && t.text.isNotBlank() && t.explanation.isNotBlank())
                require(t.options.map { it.id }.toSet() == q.options.map { it.id }.toSet() && t.options.size == q.options.size)
                require(t.options.all { it.text.isNotBlank() })
            }
        }
    }

    private fun validateSource(source: SourceDto) {
        val uri = URI(source.url)
        require(source.title.isNotBlank() && uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null) { "Source must use HTTPS without credentials" }
    }
}
