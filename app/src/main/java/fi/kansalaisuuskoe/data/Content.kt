package fi.kansalaisuuskoe.data

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import fi.kansalaisuuskoe.domain.*
import java.net.URI

@JsonClass(generateAdapter = true)
data class ContentPackageDto(val schemaVersion: Int, val packageId: String, val version: Int, val status: String, val topics: List<TopicDto>, val questions: List<QuestionDto>, val objectives: List<ObjectiveDto> = emptyList())
@JsonClass(generateAdapter = true)
data class TopicDto(val id: String, val title: String, val description: String, val order: Int, val translations: Map<String, TopicTranslationDto> = emptyMap(), val lessons: Map<String, LessonDto> = emptyMap()) {
    fun model() = Topic(id, title, description, order, translations.mapValues { (_, t) -> TopicTranslation(t.title, t.description) }, lessons.mapValues { (language, lesson) -> lesson.model(language) })
}
@JsonClass(generateAdapter = true)
data class LessonDto(val id: String, val revision: Int, val introduction: String, val status: String, val sections: List<LessonSectionDto>,
                     /** Support translations only: true once a person has checked the translation. */
                     val reviewed: Boolean = false) {
    fun model(language: String) = AuthoredLesson(id, revision, introduction, ContentStatus.valueOf(status), sections.map { it.model() }, reviewed || language in SOURCE_LANGUAGES)
}
@JsonClass(generateAdapter = true)
data class LessonSectionDto(val id: String, val title: String, val body: String, val sources: List<SourceDto>, val easyBody: String? = null) {
    fun model() = LessonSection(id, title, body, easyBody, sources.map { Source(it.title, it.url, it.checkedAt) })
}
@JsonClass(generateAdapter = true)
data class ObjectiveDto(val id: String, val topicId: String, val statement: Map<String, String>, val sourceRef: String? = null) {
    fun model() = LearningObjective(id, topicId, statement, sourceRef)
}
@JsonClass(generateAdapter = true)
data class TopicTranslationDto(val title: String, val description: String)
@JsonClass(generateAdapter = true)
data class OptionDto(val id: String, val text: String)
@JsonClass(generateAdapter = true)
data class VocabularyDto(val term: String, val meaning: String, val swedish: String? = null)
@JsonClass(generateAdapter = true)
data class SourceDto(val title: String, val url: String, val checkedAt: String? = null)
@JsonClass(generateAdapter = true)
data class TranslationDto(
    val text: String, val options: List<OptionDto>, val explanation: String,
    /** Meanings of the question's vocabulary in this language, keyed by the Finnish term. */
    val vocabulary: Map<String, String> = emptyMap(),
    /** True once a person has checked the translation; otherwise the app labels it as machine-translated. */
    val reviewed: Boolean = false,
)
@JsonClass(generateAdapter = true)
data class QuestionDto(
    val id: String, val revision: Int, val topicId: String, val text: String,
    val options: List<OptionDto>, val correctOptionId: String, val explanation: String,
    val easyExplanation: String? = null, val vocabulary: List<VocabularyDto> = emptyList(),
    val source: SourceDto, val language: String, val difficulty: String,
    val tags: List<String>, val status: String,
    val translations: Map<String, TranslationDto> = emptyMap(),
    // Absent in schema 1 packages and in payloads stored before v0.17.
    val format: String = "SINGLE_CHOICE",
    val objectiveIds: List<String> = emptyList(),
    val validFrom: String? = null,
    val reviewBy: String? = null,
) {
    fun model() = Question(
        id, revision, topicId, text, options.map { AnswerOption(it.id, it.text) }, correctOptionId,
        explanation, easyExplanation, vocabulary.map { VocabularyItem(it.term, it.meaning, it.swedish) },
        Source(source.title, source.url, source.checkedAt), language, Difficulty.valueOf(difficulty), tags,
        ContentStatus.valueOf(status), translations.mapValues { (language, value) ->
            QuestionTranslation(value.text, value.options.map { AnswerOption(it.id, it.text) }, value.explanation, value.vocabulary, value.reviewed || language in SOURCE_LANGUAGES)
        },
        QuestionFormat.valueOf(format),
        objectiveIds, validFrom, reviewBy,
    )
}

/** Study languages; every other language tag in a package is a support translation. */
val NATIVE_LANGUAGES = setOf("fi", "sv")
/** Written with the Finnish source and covered by its review, so never labelled as a machine translation. */
val SOURCE_LANGUAGES = NATIVE_LANGUAGES + "en"
private val LANGUAGE_TAG = Regex("[a-z]{2,3}(-[A-Za-z0-9]{2,8})*")

object ContentCodec {
    private val moshi = Moshi.Builder().build()
    val questionAdapter = moshi.adapter(QuestionDto::class.java)
    val topicTranslationsAdapter = moshi.adapter<Map<String, TopicTranslationDto>>(Types.newParameterizedType(Map::class.java, String::class.java, TopicTranslationDto::class.java))
    val lessonsAdapter = moshi.adapter<Map<String, LessonDto>>(Types.newParameterizedType(Map::class.java, String::class.java, LessonDto::class.java))
    val objectiveAdapter = moshi.adapter(ObjectiveDto::class.java)
    private val packageAdapter = moshi.adapter(ContentPackageDto::class.java)
    // Schema 2 adds question formats and learning objectives. Older apps reject it rather than
    // misreading a true/false item.
    const val SCHEMA_VERSION = 2
    fun parse(json: String): ContentPackageDto = requireNotNull(packageAdapter.fromJson(json)).also(::validate)

    /**
     * The package label for material re-wrapped without its original one (backups, assessment
     * snapshots): exactly the strongest label its questions and lessons carry, so it neither fails
     * validation nor claims more than any item does.
     */
    fun envelopeStatus(topics: List<TopicDto>, questions: List<QuestionDto>): String =
        (questions.map { it.status } + topics.flatMap { topic -> topic.lessons.values.map { it.status } })
            .maxOfOrNull { ContentStatus.valueOf(it) }?.name ?: ContentStatus.DEMO.name

    /**
     * [snapshot] validates a saved assessment copy: its questions keep their objective IDs, but the
     * snapshot does not copy the objective list.
     */
    fun validate(content: ContentPackageDto, snapshot: Boolean = false) {
        require(content.schemaVersion in 1..SCHEMA_VERSION) { "Unsupported content schema" }
        require(content.packageId.isNotBlank() && content.version > 0)
        // No lesson or question may claim stronger provenance than the package it arrives in.
        val packageStatus = ContentStatus.valueOf(content.status)
        require(content.topics.isNotEmpty() && content.questions.isNotEmpty())
        require(content.topics.map { it.id }.distinct().size == content.topics.size) { "Duplicate topic ID" }
        require(content.questions.map { it.id }.distinct().size == content.questions.size) { "Duplicate question ID" }
        val topics = content.topics.map { it.id }.toSet()
        content.topics.forEach { topic ->
            require(topic.id.matches(Regex("[a-z0-9-]+")) && topic.title.isNotBlank() && topic.description.isNotBlank())
            topic.translations.forEach { (language, translation) ->
                require(language.matches(LANGUAGE_TAG) && translation.title.isNotBlank() && translation.description.isNotBlank()) { "Invalid topic translation" }
            }
            topic.lessons.forEach { (language, lesson) ->
                require(language in NATIVE_LANGUAGES || language.matches(LANGUAGE_TAG)) { "Unsupported lesson language" }
                require(lesson.id.isNotBlank() && lesson.revision > 0 && lesson.introduction.isNotBlank() && lesson.sections.isNotEmpty())
                require(ContentStatus.valueOf(lesson.status) <= packageStatus) { "Lesson provenance exceeds its package" }
                require(lesson.sections.map { it.id }.distinct().size == lesson.sections.size) { "Duplicate lesson section" }
                lesson.sections.forEach { section ->
                    require(section.id.isNotBlank() && section.title.isNotBlank() && section.body.isNotBlank())
                    require(section.easyBody == null || (language == "fi" && section.easyBody.isNotBlank()))
                    require(section.sources.isNotEmpty()) { "Lesson sections need sources" }
                    section.sources.forEach { validateSource(it) }
                }
            }
            // Supporting translations must describe the same sections as the study-language lesson.
            topic.lessons.filterKeys { it !in NATIVE_LANGUAGES }.values.forEach { support ->
                require(topic.lessons.keys.any { it in NATIVE_LANGUAGES }) { "Support translations need a native lesson" }
                topic.lessons.filterKeys { it in NATIVE_LANGUAGES }.values.forEach { native ->
                    require(native.id == support.id && native.sections.map { it.id } == support.sections.map { it.id }) { "Misaligned lesson translation" }
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
            when (QuestionFormat.valueOf(q.format)) {
                QuestionFormat.SINGLE_CHOICE -> Unit
                QuestionFormat.TRUE_FALSE -> {
                    require(content.schemaVersion >= 2) { "True/false questions need content schema 2" }
                    require(q.options.size == 2 && q.options.map { it.id }.toSet() == setOf(QuestionFormat.TRUE_ID, QuestionFormat.FALSE_ID)) { "True/false options must be true and false" }
                }
            }
            require(q.options.map { it.id }.distinct().size == q.options.size)
            require(q.options.count { it.id == q.correctOptionId } == 1) { "Missing correct answer" }
            require(q.language in setOf("fi", "sv"))
            Difficulty.valueOf(q.difficulty)
            require(ContentStatus.valueOf(q.status) <= packageStatus) { "Question provenance exceeds its package" }
            validateSource(q.source)
            val validFrom = q.validFrom?.let(::date)
            val reviewBy = q.reviewBy?.let(::date)
            require(validFrom == null || reviewBy == null || validFrom < reviewBy) { "Review date must follow validity date" }
            require(reviewBy == null || q.source.checkedAt?.let(::date)?.let { it <= reviewBy } != false) { "Source check must precede review date" }
            q.translations.forEach { (language, t) ->
                require(language.matches(LANGUAGE_TAG) && language != q.language && t.text.isNotBlank() && t.explanation.isNotBlank())
                require(t.options.map { it.id }.toSet() == q.options.map { it.id }.toSet() && t.options.size == q.options.size)
                require(t.options.all { it.text.isNotBlank() })
                require(t.vocabulary.all { (term, meaning) -> meaning.isNotBlank() && q.vocabulary.any { it.term == term } }) { "Translated word not in vocabulary" }
            }
        }
        validateObjectives(content, snapshot)
    }

    private fun validateObjectives(content: ContentPackageDto, snapshot: Boolean) {
        val topics = content.topics.map { it.id }.toSet()
        val ids = content.objectives.map { it.id }
        require(content.schemaVersion >= 2 || content.objectives.isEmpty()) { "Objectives need content schema 2" }
        require(ids.distinct().size == ids.size) { "Duplicate objective ID" }
        content.objectives.forEach { objective ->
            require(objective.id.matches(Regex("[a-z0-9-]+")) && objective.topicId in topics) { "Invalid objective" }
            require(objective.statement.keys.all { it.matches(LANGUAGE_TAG) } && objective.statement.values.all { it.isNotBlank() }) { "Invalid objective statement" }
            require(objective.statement.containsKey("fi") && objective.statement.containsKey("sv")) { "Objectives need Finnish and Swedish statements" }
            require(objective.sourceRef == null || objective.sourceRef.isNotBlank())
        }
        content.questions.forEach { q ->
            require(q.objectiveIds.distinct().size == q.objectiveIds.size) { "Duplicate objective reference" }
            when {
                snapshot -> Unit
                content.objectives.isEmpty() -> require(q.objectiveIds.isEmpty()) { "Objective references need an objectives list" }
                else -> require(q.objectiveIds.isNotEmpty() && q.objectiveIds.all { it in ids }) { "Question ${q.id} needs existing objectives" }
            }
        }
    }

    private fun date(value: String): java.time.LocalDate = java.time.LocalDate.parse(value)

    private fun validateSource(source: SourceDto) {
        source.checkedAt?.let(::date)
        val uri = URI(source.url)
        require(source.title.isNotBlank() && uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null) { "Source must use HTTPS without credentials" }
    }
}
