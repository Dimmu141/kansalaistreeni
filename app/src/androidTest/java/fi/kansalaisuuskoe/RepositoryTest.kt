package fi.kansalaisuuskoe

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.moshi.Moshi
import fi.kansalaisuuskoe.data.*
import fi.kansalaisuuskoe.domain.LearningEngine
import fi.kansalaisuuskoe.domain.StudyLanguage
import fi.kansalaisuuskoe.domain.forLanguage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    @Test fun backupRoundtripRestoresMaterialBothLanguagesScoresAndDatesAfterReopen() = runBlocking {
        val questions = repository.snapshot.first().questions
        val fi = questions.first { it.language == "fi" }
        val sv = questions.first { it.language == "sv" }
        repository.answer(fi, fi.correctOptionId, "fi", 1000)
        repository.answer(fi, fi.correctOptionId, "fi-early", 2000)
        repository.answer(sv, sv.options.first { it.id != sv.correctOptionId }.id, "sv", 2000)
        val original = repository.snapshot.first()
        val exported = repository.exportBackup(3000)
        val c = ContentCodec.parse(seed())
        repository.importContent(json(c.copy(version = 7, questions = c.questions.map { it.copy(revision = 2) })))
        repository.answer(repository.snapshot.first().questions.first(), fi.correctOptionId, "later", 4000)
        val beforePreview = repository.snapshot.first()
        val preview = repository.prepareRestore(exported)
        assertEquals(beforePreview, repository.snapshot.first())
        assertEquals(4, preview.currentAnswers)
        assertEquals(2, preview.finnishAnswers); assertEquals(1, preview.swedishAnswers)
        repository.restoreBackup(preview)
        database.close(); open(); repository.initialize(context)
        val restored = repository.snapshot.first()
        assertEquals(original.questions, restored.questions)
        assertEquals(original.topics, restored.topics)
        assertEquals(original.attempts, restored.attempts)
        assertEquals(original.progress.sortedBy { it.questionId }, restored.progress.sortedBy { it.questionId })
        assertEquals(c.version, repository.installedContent().metadata?.version)
    }
    @Test fun restoreKeepsHistoricalRemovedAndRevisedAnswersOutsideCurrentMastery() = runBlocking {
        val c = ContentCodec.parse(seed())
        val questions = repository.snapshot.first().questions
        questions.take(2).forEachIndexed { index, q -> repository.answer(q, q.correctOptionId, "old-$index", 1000) }
        repository.importContent(json(c.copy(version = 5, questions = c.questions.drop(1).map { if (it.id == questions[1].id) it.copy(revision = 2) else it })))
        val exported = repository.exportBackup(2000)
        val preview = repository.prepareRestore(exported)
        repository.restoreBackup(preview)
        val state = repository.snapshot.first()
        assertEquals(2, state.attempts.size)
        assertTrue(state.progress.isEmpty())
        assertEquals(2, LearningEngine.summarize(state, 2000).answered)
    }
    @Test fun answerAfterRestorePreviewMakesConfirmationStaleWithoutLosingData() = runBlocking {
        val preview = repository.prepareRestore(repository.exportBackup(2000))
        val q = repository.snapshot.first().questions.first()
        repository.answer(q, q.correctOptionId, "after-preview", 3000)
        val before = repository.snapshot.first()
        try { repository.restoreBackup(preview); fail("Stale restore accepted") } catch (e: BackupException) { assertEquals(BackupProblem.STALE, e.problem) }
        assertEquals(before, repository.snapshot.first())
    }
    @Test fun materialUpdateAfterRestorePreviewIsRejectedWithoutRevertingContent() = runBlocking {
        val preview = repository.prepareRestore(repository.exportBackup(2000))
        val c = ContentCodec.parse(seed())
        repository.importContent(json(c.copy(version = c.version + 1)))
        val before = repository.installedContent()
        try { repository.restoreBackup(preview); fail("Stale material restore accepted") } catch (e: BackupException) { assertEquals(BackupProblem.STALE, e.problem) }
        assertEquals(before, repository.installedContent())
    }
    @Test fun failedRestoreRollsBackMaterialHistoryScoresAndMetadataTogether() = runBlocking {
        val q = repository.snapshot.first().questions.first()
        repository.answer(q, q.correctOptionId, "backed-up", 1000)
        val exported = repository.exportBackup(2000)
        val c = ContentCodec.parse(seed())
        repository.importContent(json(c.copy(version = c.version + 1)))
        repository.answer(q, q.correctOptionId, "newer", 3000)
        val before = repository.snapshot.first()
        val metadata = repository.installedContent().metadata
        val preview = repository.prepareRestore(exported)
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER abort_restore BEFORE INSERT ON question_progress BEGIN SELECT RAISE(ABORT, 'test restore interruption'); END")
        assertTrue(runCatching { repository.restoreBackup(preview) }.isFailure)
        assertEquals(before, repository.snapshot.first())
        assertEquals(metadata, repository.installedContent().metadata)
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER abort_restore")
        repository.restoreBackup(preview)
        assertEquals(listOf("backed-up"), repository.snapshot.first().attempts.map { it.id })
    }
    @Test fun emptyBackupCanDeliberatelyReplaceExistingHistoryAfterReview() = runBlocking {
        val empty = repository.exportBackup(1000)
        val q = repository.snapshot.first().questions.first()
        repository.answer(q, q.correctOptionId, "later", 2000)
        val preview = repository.prepareRestore(empty)
        assertEquals(1, preview.currentAnswers)
        assertTrue(preview.backup.attempts.isEmpty())
        repository.restoreBackup(preview)
        assertTrue(repository.snapshot.first().attempts.isEmpty())
        assertTrue(repository.snapshot.first().progress.isEmpty())
    }
    @Test fun invalidBackupCannotChangeDatabaseAndExportsNeverMutateProgress() = runBlocking {
        val q = repository.snapshot.first().questions.first()
        repository.answer(q, q.correctOptionId, "saved", 1000)
        val before = repository.snapshot.first()
        val json = repository.exportBackup(2000)
        assertEquals(before, repository.snapshot.first())
        try { repository.prepareRestore(json.replace("\"correct\":true", "\"correct\":false")); fail("Incorrect score accepted") } catch (e: BackupException) { assertEquals(BackupProblem.INVALID, e.problem) }
        assertEquals(before, repository.snapshot.first())
    }
    @Test fun translationOnlyUpdatePreservesMasteryScheduleAndHistoryAfterReopen() = runBlocking {
        val c = ContentCodec.parse(seed())
        val q = repository.snapshot.first().questions.first()
        val result = repository.answer(q, q.correctOptionId, "translation-history", 1000)
        val updated = c.copy(version = c.version + 1, questions = c.questions.map { it.copy(translations = emptyMap()) })
        val preview = repository.prepareImport(json(updated))
        assertEquals(24, preview.changes.translated)
        repository.confirmImport(preview)
        database.close(); open()
        val saved = repository.snapshot.first()
        assertEquals(result.progress, saved.progress.single())
        assertEquals(listOf(result.attempt), saved.attempts)
        assertTrue(saved.questions.all { it.translations.isEmpty() })
    }
    @Test fun failedProgressWriteRollsBackAttemptAndSameTokenCanBeRetried() = runBlocking {
        val q = repository.snapshot.first().questions.first()
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER abort_progress BEFORE INSERT ON question_progress BEGIN SELECT RAISE(ABORT, 'test storage interruption'); END")
        assertTrue(runCatching { repository.answer(q, q.correctOptionId, "interrupted", 1000) }.isFailure)
        assertTrue(repository.snapshot.first().attempts.isEmpty())
        assertTrue(repository.snapshot.first().progress.isEmpty())
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER abort_progress")
        val first = repository.answer(q, q.correctOptionId, "interrupted", 2000)
        assertEquals(first, repository.answer(q, q.correctOptionId, "interrupted", 3000))
        database.close(); open()
        assertEquals(1, repository.snapshot.first().attempts.size)
        assertEquals(first.progress, repository.snapshot.first().progress.single())
    }
    @Test fun restoredPendingSaveAfterCommitDoesNotDuplicateAnAnswer() = recoverPending(alreadyCommitted = true)
    @Test fun restoredPendingSaveBeforeCommitRecordsOneAnswer() = recoverPending(alreadyCommitted = false)
    private fun recoverPending(alreadyCommitted: Boolean) = runBlocking {
        val q = repository.snapshot.first().questions.first()
        val token = "restored-save"
        if (alreadyCommitted) repository.answer(q, q.correctOptionId, token, 1000)
        val store = androidx.lifecycle.ViewModelStore()
        val model = withContext(Dispatchers.Main) {
            fi.kansalaisuuskoe.ui.QuestionViewModel(repository, androidx.lifecycle.SavedStateHandle(mapOf(
                "question_ids" to arrayListOf(q.id), "index" to 0, "selected" to q.correctOptionId,
                "pending_option" to q.correctOptionId, "attempt_token" to token, "correct_count" to 0,
            ))).also { store.put("restored", it) }
        }
        try {
            val restored = withTimeout(15_000) { model.state.first { it.answered } }
            assertEquals(1, restored.correctCount)
            assertEquals(q.correctOptionId, restored.selected)
            assertEquals(1, repository.snapshot.first().attempts.size)
            assertEquals(25, repository.snapshot.first().progress.single().mastery)
            if (alreadyCommitted) assertEquals(86_401_000L, restored.nextReview)
            withContext(Dispatchers.Main) { model.submit() }
            assertEquals(1, repository.snapshot.first().attempts.size)
            withContext(Dispatchers.Main) { model.next() }
            val recap = withTimeout(15_000) { model.state.first { it.finished } }.recap
            assertEquals(listOf(token), recap.answers.map { it.attempt.id })
            assertEquals(repository.snapshot.first().progress.single().nextReview, recap.reviewAt)
        } finally { withContext(Dispatchers.Main) { store.clear() } }
    }
    @Test fun finishedSessionRestoresItsOwnAnswersFromSavedTokens() = runBlocking {
        val questions = repository.snapshot.first().questions.filter { it.language == "fi" }
        val q = questions.first()
        val result = repository.answer(q, q.options.first { it.id != q.correctOptionId }.id, "lesson", 1000)
        repository.answer(questions[1], questions[1].correctOptionId, "other-session", 2000)
        val store = androidx.lifecycle.ViewModelStore()
        val model = withContext(Dispatchers.Main) {
            fi.kansalaisuuskoe.ui.QuestionViewModel(repository, androidx.lifecycle.SavedStateHandle(mapOf(
                "question_ids" to arrayListOf(q.id), "index" to 1, "correct_count" to 0,
                "session_attempt_ids" to arrayListOf("lesson"),
            ))).also { store.put("recap", it) }
        }
        try {
            val state = withTimeout(15_000) { model.state.first { it.finished } }
            assertEquals(1, state.total)
            assertEquals(listOf(result.attempt), state.recap.mistakes.map { it.attempt })
            assertEquals(result.progress.nextReview, state.recap.reviewAt)
            assertEquals(2, repository.snapshot.first().attempts.size)
        } finally { withContext(Dispatchers.Main) { store.clear() } }
    }
    @Test fun legacyFinishedSessionKeepsCountsWithoutInventingAnswerDetails() = runBlocking {
        val q = repository.snapshot.first().questions.first()
        repository.answer(q, q.correctOptionId, "legacy", 1000)
        val store = androidx.lifecycle.ViewModelStore()
        val model = withContext(Dispatchers.Main) {
            fi.kansalaisuuskoe.ui.QuestionViewModel(repository, androidx.lifecycle.SavedStateHandle(mapOf(
                "question_ids" to arrayListOf(q.id), "index" to 1, "correct_count" to 1,
            ))).also { store.put("legacy", it) }
        }
        try {
            val state = withTimeout(15_000) { model.state.first { it.finished } }
            assertEquals(1, state.correctCount)
            assertTrue(state.recap.answers.isEmpty())
            assertNull(state.recap.reviewAt)
            assertEquals(1, repository.snapshot.first().attempts.size)
        } finally { withContext(Dispatchers.Main) { store.clear() } }
    }
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: LearningDatabase
    private lateinit var repository: LearningRepository
    private val name = "repository-verification.db"
    private fun open() { database = Room.databaseBuilder(context, LearningDatabase::class.java, name).addMigrations(LearningDatabase.MIGRATION_1_2, LearningDatabase.MIGRATION_2_3, LearningDatabase.MIGRATION_3_4, LearningDatabase.MIGRATION_4_5).build(); repository = LearningRepository(database) }
    private fun seed() = context.assets.open("demo-content.json").bufferedReader().use { it.readText() }
    private fun json(c: ContentPackageDto) = Moshi.Builder().build().adapter(ContentPackageDto::class.java).toJson(c)
    @Before fun before() = runBlocking { context.deleteDatabase(name); open(); repository.importContent(seed()) }
    @After fun after() { database.close(); context.deleteDatabase(name) }

    @Test fun answerIsIdempotentAndSurvivesDatabaseReopen() = runBlocking {
        val q = repository.snapshot.first().questions.first()
        repository.answer(q, q.correctOptionId, "token", 1000)
        repository.answer(q, q.correctOptionId, "token", 2000)
        database.close(); open()
        val saved = repository.snapshot.first()
        assertEquals(1, saved.attempts.size)
        assertEquals(1000L, saved.attempts.single().answeredAt)
        assertEquals(25, saved.progress.single().mastery)
        assertEquals(1, saved.progress.single().attempts)
    }
    @Test fun concurrentDuplicateSavesRecordExactlyOnce() = runBlocking {
        val q = repository.snapshot.first().questions.first()
        coroutineScope { List(10) { async(Dispatchers.IO) { repository.answer(q, q.correctOptionId, "duplicate", 1000) } }.awaitAll() }
        assertEquals(1, repository.snapshot.first().attempts.size)
        assertEquals(1, repository.snapshot.first().progress.single().attempts)
    }
    @Test fun earlyPracticeSurvivesReopenWithoutIncreasingMastery() = runBlocking {
        val q = repository.snapshot.first().questions.first()
        val first = repository.answer(q, q.correctOptionId, "first", 1000)
        val repeated = repository.answer(q, q.correctOptionId, "repeat", 2000)
        assertTrue(first.eligibleForMastery)
        assertFalse(repeated.eligibleForMastery)
        assertEquals(first.progress.nextReview, repeated.progress.nextReview)
        database.close(); open()
        val p = repository.snapshot.first().progress.single()
        assertEquals(25, p.mastery)
        assertEquals(2, p.attempts)
        assertEquals(2, p.correctAnswers)
    }
    @Test fun retryReturnsOriginalFeedbackEvenAfterLaterAnswers() = runBlocking {
        val q = repository.snapshot.first().questions.first()
        val first = repository.answer(q, q.correctOptionId, "first", 1000)
        val repeat = repository.answer(q, q.correctOptionId, "repeat", 2000)
        repository.answer(q, q.correctOptionId, "due", requireNotNull(first.progress.nextReview))
        assertEquals(first, repository.answer(q, q.correctOptionId, "first", 999_999_999))
        assertEquals(repeat, repository.answer(q, q.correctOptionId, "repeat", 999_999_999))
        assertEquals(3, repository.snapshot.first().attempts.size)
        assertEquals(50, repository.snapshot.first().progress.single().mastery)
    }
    @Test fun changedRevisionResetsCurrentMasteryAndRetainsHistory() = runBlocking {
        val q = repository.snapshot.first().questions.first()
        repository.answer(q, q.correctOptionId, "token", 1000)
        val c = ContentCodec.parse(seed())
        repository.importContent(json(c.copy(version = c.version + 1, questions = c.questions.map { if (it.id == q.id) it.copy(revision = 2, text = "Päivitetty kysymys?") else it })))
        val current = repository.snapshot.first()
        assertEquals(1, current.attempts.size)
        assertEquals(0, LearningEngine.summarize(current, 1000).mastery)
        assertEquals(0, LearningEngine.summarize(current, 1000).studiedCount)
        val updated = current.questions.first()
        repository.answer(updated, updated.correctOptionId, "new-token", 2000)
        assertEquals(25, repository.snapshot.first().progress.single().mastery)
        assertEquals(2, repository.snapshot.first().attempts.size)
        repository.initialize(context)
        assertEquals(c.version + 1, database.learningDao().metadata()!!.version)
    }
    @Test fun invalidImportLeavesStoredPackageIntact() = runBlocking {
        val c = ContentCodec.parse(seed())
        val before = repository.snapshot.first()
        try { repository.importContent(json(c.copy(version = c.version + 1, questions = listOf(c.questions.first().copy(correctOptionId = "missing"))))); fail("Invalid import accepted") }
        catch (_: IllegalArgumentException) { }
        assertEquals(before.questions, repository.snapshot.first().questions)
        assertEquals(c.version, database.learningDao().metadata()!!.version)
    }
    @Test fun editedQuestionNeedsRevisionAndPackageVersion() = runBlocking {
        val c = ContentCodec.parse(seed())
        val changed = c.copy(version = c.version + 1, questions = c.questions.map { it.copy(explanation = "Muutettu selitys.") })
        try { repository.importContent(json(changed)); fail("Unversioned question accepted") } catch (_: IllegalArgumentException) { }
        assertEquals(c.version, database.learningDao().metadata()!!.version)
        try { repository.importContent(json(c.copy(questions = c.questions.reversed()))); fail("Unversioned package accepted") } catch (_: IllegalArgumentException) { }
        assertEquals(24, repository.snapshot.first().questions.size)
    }
    @Test fun invalidAnswerDoesNotWriteAttemptOrProgress() = runBlocking {
        val q = repository.snapshot.first().questions.first()
        try { repository.answer(q, "missing", "bad", 1000); fail("Invalid answer accepted") } catch (_: IllegalArgumentException) { }
        assertTrue(repository.snapshot.first().attempts.isEmpty())
        assertTrue(repository.snapshot.first().progress.isEmpty())
    }
    @Test fun futurePackageIsNotOverwrittenByDemoOnLaunch() = runBlocking {
        val c = ContentCodec.parse(seed())
        repository.importContent(json(c.copy(packageId = "future-editorial", status = "EDITORIAL")))
        repository.initialize(context)
        assertEquals("future-editorial", database.learningDao().metadata()!!.packageId)
    }
    @Test fun topicTranslationsPersistAndTopicOnlyUpdatePreservesQuestionProgress() = runBlocking {
        val content = ContentCodec.parse(seed())
        val question = repository.snapshot.first().questions.first()
        val result = repository.answer(question, question.correctOptionId, "before-translation", 1000)
        val changed = content.copy(version = content.version + 1, topics = content.topics.map {
            it.copy(translations = mapOf("en" to TopicTranslationDto("Updated topic", "Updated description.")))
        })
        repository.importContent(json(changed))
        database.close(); open()
        val snapshot = repository.snapshot.first()
        assertEquals("Updated topic", snapshot.topics.first().titleFor("en"))
        assertEquals(content.topics.first().title, snapshot.topics.first().titleFor("fi"))
        assertEquals(result.progress, snapshot.progress.single())
        assertEquals(1, snapshot.attempts.size)
        assertEquals(question, snapshot.questions.first())
    }
    @Test fun previewDoesNotWriteAndConfirmationPreservesOnlyUnchangedProgress() = runBlocking {
        val c = ContentCodec.parse(seed())
        val original = repository.snapshot.first().questions
        original.take(2).forEachIndexed { i, q -> repository.answer(q, q.correctOptionId, "before-$i", 1000) }
        val before = repository.snapshot.first()
        val next = c.copy(version = c.version + 1, questions = c.questions.dropLast(1).mapIndexed { i, q ->
            if (i == 0) q.copy(revision = 2, explanation = "Päivitetty selitys.") else q
        } + c.questions.last().copy(id = "brand-new"))
        val preview = repository.prepareImport(json(next))
        assertEquals(ContentChanges(1, 1, 22, 1), preview.changes)
        assertEquals(before, repository.snapshot.first())
        // An answer after preview is retained; only changes to content invalidate confirmation.
        val later = original[2]
        repository.answer(later, later.correctOptionId, "after-preview", 2000)
        repository.confirmImport(preview)
        database.close(); open()
        val saved = repository.snapshot.first()
        assertEquals(3, saved.attempts.size)
        assertEquals(2, LearningEngine.summarize(saved, 2000).studiedCount)
        assertEquals(2, LearningEngine.summarize(saved, 2000).mastery)
        repository.initialize(context)
        assertEquals(c.version + 1, database.learningDao().metadata()!!.version)
    }
    @Test fun stalePreviewCannotOverwriteAnInterveningUpdate() = runBlocking {
        val c = ContentCodec.parse(seed())
        val preview = repository.prepareImport(json(c.copy(version = c.version + 1)))
        repository.importContent(json(c.copy(version = c.version + 2)))
        try { repository.confirmImport(preview); fail("Stale preview committed") }
        catch (e: ContentImportException) { assertEquals(ImportProblem.STALE, e.problem) }
        assertEquals(c.version + 2, database.learningDao().metadata()!!.version)
    }
    @Test fun concurrentConfirmationCommitsOnceAndThenRejectsStalePreview() = runBlocking {
        val c = ContentCodec.parse(seed())
        val preview = repository.prepareImport(json(c.copy(version = c.version + 1)))
        val results = coroutineScope { List(2) { async(Dispatchers.IO) { runCatching { repository.confirmImport(preview) } } }.awaitAll() }
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(ImportProblem.STALE, (results.single { it.isFailure }.exceptionOrNull() as ContentImportException).problem)
    }
    @Test fun removedStudiedQuestionCannotReclaimItsOldProgress() = runBlocking {
        val c = ContentCodec.parse(seed())
        val q = repository.snapshot.first().questions.first()
        repository.answer(q, q.correctOptionId, "old-answer", 1000)
        repository.importContent(json(c.copy(version = c.version + 1, questions = c.questions.drop(1))))
        try { repository.prepareImport(json(c.copy(version = c.version + 2))); fail("Historical revision reused") }
        catch (e: ContentImportException) { assertEquals(ImportProblem.REVISION, e.problem) }
        val next = c.copy(version = c.version + 2, questions = c.questions.map { if (it.id == q.id) it.copy(revision = 2) else it })
        repository.confirmImport(repository.prepareImport(json(next)))
        val saved = repository.snapshot.first()
        assertEquals(1, saved.attempts.size)
        assertEquals(0, LearningEngine.summarize(saved, 1000).studiedCount)
        assertEquals(0, LearningEngine.summarize(saved, 1000).mastery)
    }
    @Test fun invalidAndChangedLanguagePreviewsKeepContentAndHistory() = runBlocking {
        val c = ContentCodec.parse(seed())
        val before = repository.snapshot.first()
        listOf("not json" to ImportProblem.INVALID, json(c.copy(version = c.version + 1, questions = c.questions.map { it.copy(language = "sv") })) to ImportProblem.REVISION).forEach { (input, problem) ->
            try { repository.prepareImport(input); fail("Invalid preview accepted") }
            catch (e: ContentImportException) { assertEquals(problem, e.problem) }
            assertEquals(before, repository.snapshot.first())
        }
    }
    @Test fun identicalFileIsAlreadyInstalledAndKeepsHistory() = runBlocking {
        val q = repository.snapshot.first().questions.first()
        repository.answer(q, q.correctOptionId, "old", 1000)
        val before = repository.snapshot.first()
        val preview = repository.prepareImport(seed())
        assertTrue(preview.alreadyInstalled)
        repository.confirmImport(preview)
        assertEquals(before, repository.snapshot.first())
    }
    @Test fun reformattedFileIsNoOpAndPreservesMetadataHistoryAndScheduleAfterReopen() = runBlocking {
        val c = ContentCodec.parse(seed())
        val q = repository.snapshot.first().questions.first()
        repository.answer(q, q.correctOptionId, "formatting-history", 1000)
        val before = repository.snapshot.first()
        val metadata = repository.installedContent().metadata
        val compact = json(c)
        assertNotEquals(ContentFile.hash(seed()), ContentFile.hash(compact))
        val preview = repository.prepareImport(compact)
        assertTrue(preview.alreadyInstalled)
        assertEquals(ContentChanges(0, 0, 24, 0), preview.changes)
        repository.confirmImport(preview)
        repository.importContent("\n  $compact\n")
        assertEquals(metadata, repository.installedContent().metadata)
        database.close(); open(); repository.initialize(context)
        assertEquals(before, repository.snapshot.first())
        assertEquals(metadata, repository.installedContent().metadata)
    }
    @Test fun restoredBackupAcceptsOriginalMaterialAndThenARealVersionedUpdate() = runBlocking {
        val c = ContentCodec.parse(seed())
        val questions = repository.snapshot.first().questions
        questions.filter { it.id in setOf("demo-parliament", "demo-parliament-sv") }.forEach {
            repository.answer(it, it.correctOptionId, "answer-${it.id}", 1000)
        }
        val backup = repository.exportBackup(2000)
        repository.restoreBackup(repository.prepareRestore(backup))
        val restored = repository.snapshot.first()
        val restoredMetadata = repository.installedContent().metadata
        assertNotEquals(ContentFile.hash(seed()), restoredMetadata!!.hash)
        val original = repository.prepareImport(seed())
        assertTrue(original.alreadyInstalled)
        repository.confirmImport(original)
        assertEquals(restoredMetadata, repository.installedContent().metadata)
        assertEquals(restored, repository.snapshot.first())
        val next = c.copy(version = c.version + 1, topics = c.topics.map { it.copy(description = "Updated topic description.") })
        repository.confirmImport(repository.prepareImport(json(next)))
        database.close(); open(); repository.initialize(context)
        val updated = repository.snapshot.first()
        assertEquals(restored.attempts, updated.attempts)
        assertEquals(restored.progress, updated.progress)
        assertEquals(restored.questions, updated.questions)
        assertEquals(next.version, repository.installedContent().metadata!!.version)
        assertTrue(updated.topics.all { it.description == "Updated topic description." })
    }
    @Test fun restoredMaterialStillRejectsUnversionedFactsTranslationsTopicsAndProvenance() = runBlocking {
        val c = ContentCodec.parse(seed())
        val q = repository.snapshot.first().questions.first()
        repository.answer(q, q.correctOptionId, "retained-answer", 1000)
        repository.restoreBackup(repository.prepareRestore(repository.exportBackup(2000)))
        val before = repository.snapshot.first()
        val metadata = repository.installedContent().metadata
        val variants = listOf(
            c.copy(topics = c.topics.map { it.copy(description = "Changed topic.") }),
            c.copy(questions = c.questions.map { it.copy(translations = emptyMap()) }),
            c.copy(questions = c.questions.map { it.copy(revision = it.revision + 1, explanation = "Changed fact.") }),
            c.copy(status = "EDITORIAL", questions = c.questions.map { it.copy(status = "EDITORIAL") }),
            c.copy(questions = c.questions.reversed()),
        )
        variants.forEach { next ->
            try { repository.prepareImport(json(next)); fail("Unversioned material accepted") }
            catch (e: ContentImportException) { assertEquals(ImportProblem.VERSION, e.problem) }
            assertEquals(before, repository.snapshot.first())
            assertEquals(metadata, repository.installedContent().metadata)
        }
    }
    @Test fun compatibleReimportDoesNotInvalidatePendingBackupOrMaterialPreviews() = runBlocking {
        val c = ContentCodec.parse(seed())
        val backupPreview = repository.prepareRestore(repository.exportBackup(1000))
        val materialPreview = repository.prepareImport(json(c.copy(version = c.version + 1)))
        val before = repository.installedContent().metadata
        repository.importContent(json(c))
        assertEquals(before, repository.installedContent().metadata)
        repository.confirmImport(materialPreview)
        assertEquals(c.version + 1, repository.installedContent().metadata!!.version)
        try { repository.restoreBackup(backupPreview); fail("Changed material did not invalidate backup") }
        catch (e: BackupException) { assertEquals(BackupProblem.STALE, e.problem) }
        val currentBackup = repository.prepareRestore(repository.exportBackup(2000))
        repository.importContent("\n${json(c.copy(version = c.version + 1))}\n")
        repository.restoreBackup(currentBackup)
        assertEquals(c.version + 1, repository.installedContent().metadata!!.version)
    }
    @Test fun swedishContentPreviewAndReopenKeepLanguageHistory() = runBlocking {
        val c = ContentCodec.parse(seed())
        val next = c.copy(packageId = "sv-demo", questions = c.questions.filter { it.language == "sv" })
        val preview = repository.prepareImport(json(next))
        assertEquals(12, preview.changes.unchanged)
        assertEquals(12, preview.changes.removed)
        repository.confirmImport(preview)
        val q = repository.snapshot.first().questions.first()
        repository.answer(q, q.correctOptionId, "swedish", 1000)
        database.close(); open()
        val saved = repository.snapshot.first()
        assertEquals("sv", saved.attempts.single().language)
        assertEquals(25, saved.progress.single().mastery)
        assertEquals(0, LearningEngine.summarize(saved.forLanguage(StudyLanguage.FINNISH), 1000).answered)
        assertEquals(100, LearningEngine.summarize(saved.forLanguage(StudyLanguage.SWEDISH), 1000).accuracy)
    }
    @Test fun bundledVersionCollisionDoesNotReplaceImportedMaterialOrBlockStartup() = runBlocking {
        val c = ContentCodec.parse(seed())
        repository.importContent(json(c.copy(packageId = "temporary")))
        val edited = c.copy(topics = c.topics.map { it.copy(description = "User material") })
        repository.importContent(json(edited))
        val q = repository.snapshot.first().questions.first()
        repository.answer(q, q.correctOptionId, "retained", 1000)
        val before = repository.snapshot.first()
        repository.initialize(context)
        assertEquals(before, repository.snapshot.first())
        assertEquals("User material", repository.snapshot.first().topics.first().description)
    }
}
