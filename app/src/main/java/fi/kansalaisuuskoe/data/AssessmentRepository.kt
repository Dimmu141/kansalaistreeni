package fi.kansalaisuuskoe.data

import androidx.room.*
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import fi.kansalaisuuskoe.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

@JsonClass(generateAdapter = true)
data class AssessmentPayload(
    val topics: List<TopicDto>, val questions: List<QuestionDto>,
    val answers: Map<String, String> = emptyMap(), val position: Int = 0,
)

@Entity(tableName = "assessments")
data class AssessmentEntity(
    @PrimaryKey val id: String, val language: String, val startedAt: Long,
    val finishedAt: Long?, val payload: String,
)

@Dao
interface AssessmentDao {
    @Query("SELECT * FROM assessments ORDER BY startedAt DESC, rowid DESC")
    fun observe(): kotlinx.coroutines.flow.Flow<List<AssessmentEntity>>
    @Query("SELECT * FROM assessments WHERE id = :id") suspend fun get(id: String): AssessmentEntity?
    @Query("SELECT * FROM assessments WHERE language = :language AND finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun active(language: String): AssessmentEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(session: AssessmentEntity)
    @Query("SELECT * FROM assessments ORDER BY startedAt ASC, rowid ASC") suspend fun all(): List<AssessmentEntity>
    @Query("DELETE FROM assessments") suspend fun deleteAll()
    @Query("DELETE FROM assessments WHERE id = :id") suspend fun delete(id: String)
}

class AssessmentChangedException : IllegalStateException("Assessment changed after deletion review")

/** Assessment snapshots/history are independent of scored learning attempts. */
class AssessmentRepository(private val database: LearningDatabase) {
    private val dao = database.assessmentDao()
    private val adapter = Moshi.Builder().build().adapter(AssessmentPayload::class.java)
    val sessions = dao.observe().map { items -> items.map(::decode) }.flowOn(Dispatchers.Default)

    private fun decode(row: AssessmentEntity): AssessmentSession {
        val payload = requireNotNull(adapter.fromJson(row.payload))
        return AssessmentSession(row.id, row.language, row.startedAt, row.finishedAt,
            payload.topics.map { it.model() }, payload.questions.map { it.model() }, payload.answers, payload.position)
    }

    suspend fun get(id: String): AssessmentSession? = withContext(Dispatchers.IO) { dao.get(id)?.let(::decode) }

    suspend fun start(language: StudyLanguage, now: Long = System.currentTimeMillis()): String = withContext(Dispatchers.IO) {
        database.withTransaction {
            dao.active(language.tag)?.let { return@withTransaction it.id }
            val learning = database.learningDao()
            val material = learning.questions().map { requireNotNull(ContentCodec.questionAdapter.fromJson(it.payload)) }
            val queue = Assessments.queue(material.map { it.model() }, language.tag)
            require(queue.isNotEmpty()) { "No questions in selected study language" }
            val topics = learning.topics().filter { t -> queue.any { it.topicId == t.id } }.map {
                // Results need original labels and question explanations, not a copy of every lesson.
                it.dto().copy(lessons = emptyMap())
            }
            val payload = AssessmentPayload(topics, queue.map { q -> material.first { it.id == q.id } })
            val id = UUID.randomUUID().toString()
            dao.save(AssessmentEntity(id, language.tag, now, null, adapter.toJson(payload)))
            id
        }
    }

    private suspend fun update(id: String, change: (AssessmentSession) -> AssessmentSession) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val row = requireNotNull(dao.get(id))
            val old = requireNotNull(adapter.fromJson(row.payload))
            val updated = change(decode(row))
            dao.save(row.copy(finishedAt = updated.finishedAt,
                payload = adapter.toJson(old.copy(answers = updated.answers, position = updated.position))))
        }
    }

    suspend fun select(id: String, questionId: String, optionId: String) = update(id) { Assessments.select(it, questionId, optionId) }
    suspend fun move(id: String, position: Int) = update(id) { Assessments.move(it, position) }
    suspend fun finish(id: String, now: Long = System.currentTimeMillis()) = update(id) { Assessments.finish(it, now) }

    /** Delete only the reviewed snapshot. Never touch ordinary learning attempts or scores. */
    suspend fun delete(expected: AssessmentSession) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val row = dao.get(expected.id) ?: return@withTransaction
            if (decode(row) != expected) throw AssessmentChangedException()
            dao.delete(expected.id)
        }
    }
}
