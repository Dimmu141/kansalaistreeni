package fi.kansalaisuuskoe.data

import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

enum class ImportProblem { TOO_LARGE, INVALID, VERSION, REVISION, STALE }
class ContentImportException(val problem: ImportProblem) : IllegalArgumentException(problem.name)

/** The same limit applies to document providers with missing or inaccurate size metadata. */
object ContentFile {
    const val MAX_BYTES = 2 * 1024 * 1024
    fun read(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            if (output.size() + count > MAX_BYTES) throw ContentImportException(ImportProblem.TOO_LARGE)
            output.write(buffer, 0, count)
        }
        val bytes = output.toByteArray()
        return try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
        } catch (_: java.nio.charset.CharacterCodingException) {
            throw ContentImportException(ImportProblem.INVALID)
        }
    }
    fun hash(json: String): String = MessageDigest.getInstance("SHA-256")
        .digest(json.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

data class ContentChanges(val added: Int, val revised: Int, val unchanged: Int, val removed: Int, val translated: Int = 0, val reclassified: Int = 0)

/**
 * What a learner practises: everything except supporting translations and classification. Moving a
 * question to another topic or objective therefore keeps its revision, mastery and review date.
 */
internal fun QuestionDto.practised() = copy(translations = emptyMap(), topicId = "", objectiveIds = emptyList())
data class InstalledContent(val metadata: ContentMetadata?, val topics: List<TopicDto>, val questions: List<QuestionDto>, val objectives: List<ObjectiveDto> = emptyList())
class PreparedContent internal constructor(
    val content: ContentPackageDto,
    val changes: ContentChanges,
    val alreadyInstalled: Boolean,
    internal val hash: String,
    internal val baseline: ContentMetadata?,
    val changedLessonVariants: Int = 0,
)

/** Question IDs are global across packages. Reintroduced studied IDs require a new revision. */
object ContentUpdates {
    fun prepare(content: ContentPackageDto, hash: String, installed: InstalledContent, historicalRevisions: Map<String, Int>, historicalLanguages: Map<String, Set<String>> = emptyMap()): PreparedContent {
        val old = installed.metadata
        // File formatting is not a material update. Compare every persisted field; preserve
        // question/option order and tied topic order because they affect the learning flow.
        val sameMaterial = content.topics.sortedBy { it.order } == installed.topics.sortedBy { it.order } &&
            content.questions == installed.questions && content.objectives == installed.objectives
        val sameVersion = old?.packageId == content.packageId && old.version == content.version
        if (old?.packageId == content.packageId) {
            if (content.version < old.version || (sameVersion && !sameMaterial)) {
                throw ContentImportException(ImportProblem.VERSION)
            }
        }
        val existing = installed.questions.associateBy { it.id }
        val previousTopics = installed.topics.associateBy { it.id }
        content.topics.forEach { topic ->
            topic.lessons.filterKeys { it in NATIVE_LANGUAGES }.forEach { (language, lesson) ->
                previousTopics[topic.id]?.lessons?.get(language)?.let { before ->
                    if (before.id == lesson.id && (lesson.revision < before.revision ||
                            (lesson != before && lesson.revision <= before.revision))) {
                        throw ContentImportException(ImportProblem.REVISION)
                    }
                }
            }
        }
        content.questions.forEach { question ->
            val before = existing[question.id]
            val historical = historicalRevisions[question.id] ?: 0
            // Language variants are separate learning items, never a revision of the same ID.
            if (before != null && before.language != question.language) throw ContentImportException(ImportProblem.REVISION)
            if (historicalLanguages[question.id]?.any { it != question.language } == true) throw ContentImportException(ImportProblem.REVISION)
            // Supporting translations and reclassification cannot change the practised text, answer key or facts.
            val learningChanged = before != null && question.practised() != before.practised()
            if ((before != null && (question.revision < before.revision || (learningChanged && question.revision <= before.revision))) ||
                question.revision < historical || (before == null && question.revision <= historical)) {
                throw ContentImportException(ImportProblem.REVISION)
            }
        }
        val added = content.questions.count { it.id !in existing }
        val unchanged = content.questions.count { it == existing[it.id] }
        val translated = content.questions.count { q -> existing[q.id]?.let { q != it && q.copy(translations = it.translations) == it } == true }
        val reclassified = content.questions.count { q -> existing[q.id]?.let { q != it && q.copy(translations = it.translations) != it && q.practised() == it.practised() } == true }
        val incomingIds = content.questions.map { it.id }.toSet()
        val removed = existing.keys.count { it !in incomingIds }
        val incomingLessons = content.topics.flatMap { topic -> topic.lessons.map { (language, lesson) -> "${topic.id}:$language" to lesson } }.toMap()
        val previousLessons = installed.topics.flatMap { topic -> topic.lessons.map { (language, lesson) -> "${topic.id}:$language" to lesson } }.toMap()
        val lessonChanges = (incomingLessons.keys + previousLessons.keys).count { incomingLessons[it] != previousLessons[it] }
        return PreparedContent(content, ContentChanges(added, content.questions.size - added - unchanged - translated - reclassified, unchanged, removed, translated, reclassified),
            sameVersion && sameMaterial, hash, old, lessonChanges)
    }
}
