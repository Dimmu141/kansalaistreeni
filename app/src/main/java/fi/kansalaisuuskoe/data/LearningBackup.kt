package fi.kansalaisuuskoe.data

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import fi.kansalaisuuskoe.domain.*
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

enum class BackupProblem { INVALID, TOO_LARGE, STALE }
class BackupException(val problem: BackupProblem) : IllegalArgumentException(problem.name)

@JsonClass(generateAdapter = true)
data class BackupAttempt(
    val id: String, val questionId: String, val revision: Int, val topicId: String,
    val selectedOptionId: String, val correct: Boolean, val answeredAt: Long, val language: String,
) {
    fun model() = QuestionAttempt(id, questionId, revision, topicId, selectedOptionId, correct, answeredAt, language)
    fun entity() = AttemptEntity(id, questionId, revision, topicId, selectedOptionId, correct, answeredAt, language)
    companion object {
        fun from(attempt: AttemptEntity) = BackupAttempt(attempt.id, attempt.questionId, attempt.revision,
            attempt.topicId, attempt.selectedOptionId, attempt.correct, attempt.answeredAt, attempt.language)
    }
}

@JsonClass(generateAdapter = true)
data class BackupAssessment(
    val id: String, val language: String, val startedAt: Long, val finishedAt: Long?,
    val payload: AssessmentPayload,
) {
    fun entity() = AssessmentEntity(id, language, startedAt, finishedAt, payloadAdapter.toJson(payload))
    companion object {
        private val payloadAdapter = Moshi.Builder().build().adapter(AssessmentPayload::class.java)
        fun from(row: AssessmentEntity) = BackupAssessment(row.id, row.language, row.startedAt, row.finishedAt,
            requireNotNull(payloadAdapter.fromJson(row.payload)))
    }
}

@JsonClass(generateAdapter = true)
data class LearningBackup(
    val format: String,
    val schemaVersion: Int,
    val exportedAt: Long,
    val content: ContentPackageDto,
    val attempts: List<BackupAttempt>,
    // Null means a legacy backup that cannot replace assessment history; [] deliberately clears it.
    val assessments: List<BackupAssessment>? = null,
)

class PreparedBackup internal constructor(
    val backup: LearningBackup,
    val currentAnswers: Int,
    internal val baseline: String,
    val currentAssessments: Int = 0,
) {
    val finnishAnswers get() = backup.attempts.count { it.language == "fi" }
    val swedishAnswers get() = backup.attempts.count { it.language == "sv" }
    val replacesAssessments get() = backup.schemaVersion == 2
    val completedAssessments get() = backup.assessments.orEmpty().count { it.finishedAt != null }
    val unfinishedAssessments get() = backup.assessments.orEmpty().size - completedAssessments
}

/** Scores are never imported. Current mastery/scheduling is replayed from validated answers. */
object BackupCodec {
    const val FORMAT = "fi.kansalaisuuskoe.learning-backup"
    const val MAX_ATTEMPTS = 50_000
    const val MAX_ASSESSMENTS = 1000
    const val CURRENT_SCHEMA = 2
    private const val MAX_TIMESTAMP = Long.MAX_VALUE - 14 * 86_400_000L
    private val adapter = Moshi.Builder().build().adapter(LearningBackup::class.java).failOnUnknown()
    private val contentAdapter = Moshi.Builder().build().adapter(ContentPackageDto::class.java)

    fun parse(json: String): LearningBackup {
        if (json.toByteArray(Charsets.UTF_8).size > BackupFile.MAX_BYTES) throw BackupException(BackupProblem.TOO_LARGE)
        return try { requireNotNull(adapter.fromJson(json)).also(::validate) }
        catch (e: BackupException) { throw e }
        catch (_: Exception) { throw BackupException(BackupProblem.INVALID) }
    }

    fun encode(backup: LearningBackup): String {
        validate(backup)
        return adapter.toJson(backup).also {
            if (it.toByteArray(Charsets.UTF_8).size > BackupFile.MAX_BYTES) throw BackupException(BackupProblem.TOO_LARGE)
        }
    }

    fun contentJson(backup: LearningBackup): String = contentAdapter.toJson(backup.content)

    fun validate(backup: LearningBackup) {
        try {
            require(backup.format == FORMAT && backup.schemaVersion in 1..CURRENT_SCHEMA && backup.exportedAt in 0..MAX_TIMESTAMP)
            require(if (backup.schemaVersion == 1) backup.assessments == null else backup.assessments != null)
            if (backup.attempts.size > MAX_ATTEMPTS) throw BackupException(BackupProblem.TOO_LARGE)
            val assessments = backup.assessments.orEmpty()
            if (assessments.size > MAX_ASSESSMENTS) throw BackupException(BackupProblem.TOO_LARGE)
            require(assessments.map { it.id }.toSet().size == assessments.size)
            require(assessments.filter { it.finishedAt == null }.groupBy { it.language }.values.all { it.size <= 1 })
            assessments.forEach(::validateAssessment)
            ContentCodec.validate(backup.content)
            require(backup.attempts.map { it.id }.toSet().size == backup.attempts.size)
            val questions = backup.content.questions.associateBy { it.id }
            val languages = mutableMapOf<String, String>()
            backup.attempts.forEach { attempt ->
                require(attempt.id.isNotBlank() && attempt.questionId.isNotBlank() && attempt.topicId.isNotBlank() && attempt.selectedOptionId.isNotBlank())
                require(attempt.revision > 0 && attempt.answeredAt in 0..MAX_TIMESTAMP && attempt.language in setOf("fi", "sv"))
                require(languages.put(attempt.questionId, attempt.language)?.let { it == attempt.language } != false)
                questions[attempt.questionId]?.let { question ->
                    require(attempt.language == question.language && attempt.revision <= question.revision)
                    if (attempt.revision == question.revision) {
                        // An attempt keeps the topic it was answered under; reclassification may move the question since.
                        require(question.options.any { it.id == attempt.selectedOptionId })
                        require(attempt.correct == (attempt.selectedOptionId == question.correctOptionId))
                    }
                }
            }
        } catch (e: BackupException) { throw e }
        catch (_: Exception) { throw BackupException(BackupProblem.INVALID) }
    }

    private fun validateAssessment(session: BackupAssessment) {
        require(session.id.isNotBlank() && session.language in setOf("fi", "sv"))
        require(session.startedAt in 0..MAX_TIMESTAMP)
        session.finishedAt?.let { require(it in session.startedAt..MAX_TIMESTAMP) }
        val payload = session.payload
        require(payload.questions.size in 1..Assessments.MAX_QUESTIONS && payload.position in payload.questions.indices)
        // Validate original snapshots independently of today's installed question revisions.
        ContentCodec.validate(ContentPackageDto(ContentCodec.SCHEMA_VERSION, "assessment-snapshot", 1,
            ContentCodec.envelopeStatus(payload.topics, payload.questions), payload.topics, payload.questions), snapshot = true)
        require(payload.questions.all { it.language == session.language })
        val questions = payload.questions.associateBy { it.id }
        payload.answers.forEach { (id, answer) -> require(questions[id]?.options?.any { it.id == answer } == true) }
        payload.timeLimitMinutes?.let { require(it in 1..Assessments.MAX_QUESTIONS * Assessments.MINUTES_PER_QUESTION) }
        // Only a timed session that ran out of time may finish with unanswered questions.
        if (session.finishedAt != null && payload.answers.size != payload.questions.size)
            require(payload.timeLimitMinutes != null && session.finishedAt >= session.startedAt + payload.timeLimitMinutes * 60_000L)
    }
}

object BackupFile {
    const val MAX_BYTES = 16 * 1024 * 1024
    fun read(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            if (output.size() + count > MAX_BYTES) throw BackupException(BackupProblem.TOO_LARGE)
            output.write(buffer, 0, count)
        }
        return try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(output.toByteArray())).toString().removePrefix("\uFEFF")
        } catch (_: java.nio.charset.CharacterCodingException) { throw BackupException(BackupProblem.INVALID) }
    }
}
