package fi.kansalaisuuskoe.data

import android.content.Context
import androidx.room.deferredTransaction
import androidx.room.useReaderConnection
import androidx.room.withTransaction
import fi.kansalaisuuskoe.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class LearningRepository(private val database: LearningDatabase) {
    private val dao = database.learningDao()
    val snapshot = database.invalidationTracker.createFlow("topics", "questions", "question_progress", "attempts")
        // Changes that arrive during a read collapse into one further read of the newest state.
        .conflate()
        .map {
            // A committed write can invalidate several tables. Capture all rows together:
            // combining independent query flows would expose different database versions.
            // A deferred reader transaction sees one version without taking the writer lock,
            // so a large read cannot delay saving an answer.
            database.useReaderConnection { reader ->
                reader.deferredTransaction { SnapshotRows(dao.topics(), dao.questions(), dao.allProgress(), dao.attempts()) }
            }
        }
        .map { rows ->
            // Decode content after releasing the read transaction.
            LearningSnapshot(rows.topics.map { it.model() },
                rows.questions.map { requireNotNull(ContentCodec.questionAdapter.fromJson(it.payload)).model() },
                rows.progress.map { it.model() }, rows.attempts.map { it.model() })
        }.flowOn(Dispatchers.Default)

    private data class SnapshotRows(val topics: List<TopicEntity>, val questions: List<QuestionEntity>,
        val progress: List<ProgressEntity>, val attempts: List<AttemptEntity>)

    suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
        val json = context.assets.open("demo-content.json").bufferedReader().use { it.readText() }
        val seed = ContentCodec.parse(json)
        val installed = dao.metadata()
        // A newer imported package must never be replaced by the bundled demo.
        if (installed != null && (installed.packageId != seed.packageId || installed.version > seed.version ||
                (installed.version == seed.version && installed.hash != ContentFile.hash(json)))) {
            database.withTransaction { ensureCurrentRules() }
            return@withContext
        }
        try { importContent(json) }
        catch (error: ContentImportException) {
            // A user's revised demo may share the bundled package ID/version space.
            // Keep that material when a bundled update would downgrade its questions.
            if (installed == null || error.problem !in setOf(ImportProblem.VERSION, ImportProblem.REVISION)) throw error
            database.withTransaction { ensureCurrentRules() }
        }
    }

    /** Validated full-package replacement, in one transaction. Keeps historical attempts. */
    suspend fun importContent(json: String) = withContext(Dispatchers.IO) {
        val content = ContentCodec.parse(json)
        val hash = ContentFile.hash(json)
        database.withTransaction {
            applyContent(prepare(content, hash))
        }
    }

    suspend fun installedContent(): InstalledContent = withContext(Dispatchers.IO) {
        database.withTransaction { installed() }
    }

    /** A consistent portable learning snapshot; device preferences and derived scores are excluded. */
    suspend fun exportBackup(now: Long = System.currentTimeMillis()): String = withContext(Dispatchers.IO) {
        database.withTransaction {
            val material = installed()
            val metadata = requireNotNull(material.metadata)
            // Metadata does not retain the original package-level label. Preserve every question's
            // provenance, and use a conservative envelope instead of asserting official origin.
            val status = if (material.questions.all { it.status == "DEMO" }) "DEMO" else "EDITORIAL"
            val content = ContentPackageDto(ContentCodec.SCHEMA_VERSION, metadata.packageId, metadata.version, status, material.topics, material.questions, material.objectives)
            BackupCodec.encode(LearningBackup(BackupCodec.FORMAT, BackupCodec.CURRENT_SCHEMA, now, content,
                dao.attempts().map(BackupAttempt::from), database.assessmentDao().all().map(BackupAssessment::from)))
        }
    }

    suspend fun prepareRestore(json: String): PreparedBackup = withContext(Dispatchers.IO) {
        val backup = BackupCodec.parse(json)
        database.withTransaction { PreparedBackup(backup, dao.attempts().size, backupBaseline(backup.schemaVersion == 2), database.assessmentDao().all().size) }
    }

    /** Deliberate snapshot replacement may roll back material versions, after a visible review. */
    suspend fun restoreBackup(preview: PreparedBackup) = withContext(Dispatchers.IO) {
        database.withTransaction {
            if (backupBaseline(preview.replacesAssessments) != preview.baseline) throw BackupException(BackupProblem.STALE)
            BackupCodec.validate(preview.backup)
            val backup = preview.backup
            val content = backup.content
            val attempts = backup.attempts.sortedBy { it.answeredAt }
            val progress = LearningEngine.replayProgress(content.questions.map { it.model() }, attempts.map { it.model() })
            dao.deleteAttempts()
            dao.deleteProgress()
            dao.deleteQuestions()
            dao.deleteTopics()
            dao.insertTopics(content.topics.map { TopicEntity(it.id, it.title, it.description, it.order, ContentCodec.topicTranslationsAdapter.toJson(it.translations), ContentCodec.lessonsAdapter.toJson(it.lessons)) })
            dao.deleteObjectives()
            dao.insertObjectives(content.objectives.mapIndexed { index, it -> ObjectiveEntity.from(it, index) })
            dao.insertQuestions(content.questions.map { QuestionEntity(it.id, it.revision, ContentCodec.questionAdapter.toJson(it)) })
            dao.insertAttempts(attempts.map { it.entity() })
            progress.forEach { dao.saveProgress(ProgressEntity.from(it)) }
            dao.saveMetadata(ContentMetadata(packageId = content.packageId, version = content.version, hash = ContentFile.hash(BackupCodec.contentJson(backup))))
            dao.saveRules(LearningRulesEntity(version = LearningEngine.RULES_VERSION))
            if (preview.replacesAssessments) {
                database.assessmentDao().deleteAll()
                backup.assessments.orEmpty().forEach { database.assessmentDao().save(it.entity()) }
            }
        }
    }

    private suspend fun backupBaseline(includeAssessments: Boolean): String {
        val metadata = dao.metadata()
        val history = dao.attempts().map(BackupAttempt::from)
        val adapter = com.squareup.moshi.Moshi.Builder().build().adapter<List<BackupAttempt>>(
            com.squareup.moshi.Types.newParameterizedType(List::class.java, BackupAttempt::class.java))
        val assessments = if (includeAssessments) {
            val assessmentAdapter = com.squareup.moshi.Moshi.Builder().build().adapter<List<BackupAssessment>>(
                com.squareup.moshi.Types.newParameterizedType(List::class.java, BackupAssessment::class.java))
            assessmentAdapter.toJson(database.assessmentDao().all().map(BackupAssessment::from))
        } else ""
        return ContentFile.hash("$metadata\n${adapter.toJson(history)}\n$assessments")
    }

    /** Read/validate only. The preview never changes content or progress. */
    suspend fun prepareImport(json: String): PreparedContent = withContext(Dispatchers.IO) {
        if (json.toByteArray(Charsets.UTF_8).size > ContentFile.MAX_BYTES) throw ContentImportException(ImportProblem.TOO_LARGE)
        val content = try { ContentCodec.parse(json) } catch (_: Exception) { throw ContentImportException(ImportProblem.INVALID) }
        database.withTransaction { prepare(content, ContentFile.hash(json)) }
    }

    /** Commit exactly the reviewed package; reject a preview if another update intervened. */
    suspend fun confirmImport(preview: PreparedContent) = withContext(Dispatchers.IO) {
        database.withTransaction {
            if (dao.metadata() != preview.baseline) throw ContentImportException(ImportProblem.STALE)
            applyContent(prepare(preview.content, preview.hash))
        }
    }

    private suspend fun installed() = InstalledContent(dao.metadata(), dao.topics().map {
        it.dto()
    }, dao.questions().map { requireNotNull(ContentCodec.questionAdapter.fromJson(it.payload)) }, dao.objectives().map { it.dto() })

    private suspend fun prepare(content: ContentPackageDto, hash: String): PreparedContent {
        val history = dao.attempts().groupBy { it.questionId }
        return ContentUpdates.prepare(content, hash, installed(),
            history.mapValues { (_, attempts) -> attempts.maxOf { it.revision } },
            history.mapValues { (_, attempts) -> attempts.map { it.language }.toSet() })
    }

    private suspend fun applyContent(preview: PreparedContent) {
        if (!preview.alreadyInstalled) {
            val content = preview.content
            dao.deleteQuestions()
            dao.deleteTopics()
            dao.insertTopics(content.topics.map { TopicEntity(it.id, it.title, it.description, it.order, ContentCodec.topicTranslationsAdapter.toJson(it.translations), ContentCodec.lessonsAdapter.toJson(it.lessons)) })
            dao.deleteObjectives()
            dao.insertObjectives(content.objectives.mapIndexed { index, it -> ObjectiveEntity.from(it, index) })
            dao.insertQuestions(content.questions.map { QuestionEntity(it.id, it.revision, ContentCodec.questionAdapter.toJson(it)) })
            dao.saveMetadata(ContentMetadata(packageId = content.packageId, version = content.version, hash = preview.hash))
        }
        ensureCurrentRules()
    }

    /** Called only inside a transaction. Rebuild derived scores once; retain every attempt. */
    private suspend fun ensureCurrentRules() {
        if (dao.rulesVersion() == LearningEngine.RULES_VERSION) return
        val questions = dao.questions().map { requireNotNull(ContentCodec.questionAdapter.fromJson(it.payload)).model() }
        val progress = LearningEngine.replayProgress(questions, dao.attempts().map { it.model() })
        dao.deleteProgress()
        progress.forEach { dao.saveProgress(ProgressEntity.from(it)) }
        dao.saveRules(LearningRulesEntity(version = LearningEngine.RULES_VERSION))
    }

    /** Idempotent attempt token prevents repeated taps/recreation from recording twice. */
    suspend fun answer(question: Question, optionId: String, attemptId: String, now: Long): AnswerResult = database.withTransaction {
        ensureCurrentRules()
        dao.attempt(attemptId)?.let {
            require(it.questionId == question.id && it.revision == question.revision && it.selectedOptionId == optionId)
            // Return the original feedback, even if later attempts have already changed progress.
            val before = dao.questionAttempts(question.id, question.revision).takeWhile { a -> a.id != attemptId }.map { a -> a.model() }
            val previous = LearningEngine.replayProgress(listOf(question), before).singleOrNull()
            return@withTransaction AnswerResult(it.model(), LearningEngine.answer(question, optionId, previous, it.answeredAt), LearningEngine.canGainMastery(question, previous, it.answeredAt))
        }
        val stored = requireNotNull(dao.question(question.id)) { "Question is no longer available" }
        require(requireNotNull(ContentCodec.questionAdapter.fromJson(stored.payload)).model() == question) { "Question content changed; restart the session" }
        val previous = dao.progress(question.id)?.model()
        val updated = LearningEngine.answer(question, optionId, previous, now)
        val attempt = AttemptEntity(attemptId, question.id, question.revision, question.topicId, optionId, optionId == question.correctOptionId, now, question.language)
        if (dao.insertAttempt(attempt) != -1L) dao.saveProgress(ProgressEntity.from(updated))
        AnswerResult(attempt.model(), updated, LearningEngine.canGainMastery(question, previous, now))
    }
}
